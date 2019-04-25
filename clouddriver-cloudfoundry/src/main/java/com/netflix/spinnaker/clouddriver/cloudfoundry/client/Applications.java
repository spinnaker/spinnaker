/*
 * Copyright 2018 Pivotal, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.clouddriver.cloudfoundry.client;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.netflix.frigga.Names;
import com.netflix.spinnaker.clouddriver.cloudfoundry.client.api.ApplicationService;
import com.netflix.spinnaker.clouddriver.cloudfoundry.client.model.v2.ApplicationEnv;
import com.netflix.spinnaker.clouddriver.cloudfoundry.client.model.v2.MapRoute;
import com.netflix.spinnaker.clouddriver.cloudfoundry.client.model.v2.Resource;
import com.netflix.spinnaker.clouddriver.cloudfoundry.client.model.v3.*;
import com.netflix.spinnaker.clouddriver.cloudfoundry.client.model.v3.Package;
import com.netflix.spinnaker.clouddriver.cloudfoundry.client.model.v3.Process;
import com.netflix.spinnaker.clouddriver.cloudfoundry.model.*;
import com.netflix.spinnaker.clouddriver.model.HealthState;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import retrofit.RetrofitError;
import retrofit.client.Response;
import retrofit.mime.TypedFile;
import retrofit.mime.TypedInput;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static com.netflix.spinnaker.clouddriver.cloudfoundry.client.CloudFoundryClientUtils.*;
import static java.util.Arrays.asList;
import static java.util.Collections.*;
import static java.util.Optional.ofNullable;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;

@RequiredArgsConstructor
@Slf4j
public class Applications {
  private final String account;
  private final String appsManagerUri;
  private final String metricsUri;
  private final ApplicationService api;
  private final Spaces spaces;

  private final LoadingCache<String, CloudFoundryServerGroup> serverGroupCache = CacheBuilder
    .newBuilder()
    .expireAfterWrite(5, TimeUnit.MINUTES)
    .build(new CacheLoader<String, CloudFoundryServerGroup>() {
      @Override
      public CloudFoundryServerGroup load(@Nonnull String guid) throws ResourceNotFoundException {
        return safelyCall(() -> api.findById(guid))
          .map(Applications.this::map)
          .orElseThrow(ResourceNotFoundException::new);
      }
    });

  @Nullable
  public CloudFoundryServerGroup findById(String guid) {
    return safelyCall(() -> {
      try {
        return serverGroupCache.get(guid);
      } catch (ExecutionException e) {
        if (e.getCause() instanceof ResourceNotFoundException) {
          return null;
        }
        throw new CloudFoundryApiException(e.getCause(), "Unable to find server group by id");
      }
    }).orElse(null);
  }

  public List<CloudFoundryApplication> all() {
    List<CloudFoundryServerGroup> serverGroups = collectPages("applications", page -> api.all(page, null, null))
      .stream().map(this::map).collect(toList());
    serverGroupCache.invalidateAll();
    serverGroups.forEach(sg -> serverGroupCache.put(sg.getId(), sg));

    Map<String, Set<CloudFoundryServerGroup>> serverGroupsByClusters = new HashMap<>();
    Map<String, Set<String>> clustersByApps = new HashMap<>();

    for (CloudFoundryServerGroup serverGroup : serverGroups) {
      Names names = Names.parseName(serverGroup.getName());
      serverGroupsByClusters.computeIfAbsent(names.getCluster(), clusterName -> new HashSet<>()).add(serverGroup);
      clustersByApps.computeIfAbsent(names.getApp(), appName -> new HashSet<>()).add(names.getCluster());
    }

    return clustersByApps.entrySet().stream()
      .map(clustersByApp -> CloudFoundryApplication.builder()
        .name(clustersByApp.getKey())
        .clusters(clustersByApp.getValue().stream()
          .map(clusterName -> CloudFoundryCluster.builder()
            .accountName(account)
            .name(clusterName)
            .serverGroups(serverGroupsByClusters.get(clusterName))
            .build())
          .collect(toSet()))
        .build()
      )
      .collect(toList());
  }

  @Nullable
  public CloudFoundryServerGroup findServerGroupByNameAndSpaceId(String name, String spaceId) {
    return Optional.ofNullable(findServerGroupId(name, spaceId))
      .map(serverGroupId -> Optional.ofNullable(findById(serverGroupId))
        .orElse(null))
      .orElse(null);
  }

  @Nullable
  public String findServerGroupId(String name, String spaceId) {
    return serverGroupCache.asMap().values().stream()
      .filter(serverGroup -> serverGroup.getName().equalsIgnoreCase(name) && serverGroup.getSpace().getId().equals(spaceId))
      .findFirst()
      .map(CloudFoundryServerGroup::getId)
      .orElseGet(() -> safelyCall(() -> api.all(null, singletonList(name), singletonList(spaceId)))
        .flatMap(page -> page.getResources().stream().findFirst().map(this::map)
          .map(serverGroup -> {
            serverGroupCache.put(serverGroup.getId(), serverGroup);
            return serverGroup;
          })
          .map(CloudFoundryServerGroup::getId))
        .orElse(null));
  }

  private CloudFoundryServerGroup map(Application application) {
    CloudFoundryServerGroup.State state = CloudFoundryServerGroup.State.valueOf(application.getState());

    CloudFoundrySpace space = safelyCall(() -> spaces.findById(application.getLinks().get("space").getGuid())).orElse(null);
    String appId = application.getGuid();
    ApplicationEnv applicationEnv = safelyCall(() -> api.findApplicationEnvById(appId)).orElse(null);
    Process process = safelyCall(() -> api.findProcessById(appId)).orElse(null);

    Set<CloudFoundryInstance> instances;
    switch (state) {
      case STOPPED:
        instances = emptySet();
        break;
      case STARTED:
      default:
        try {
          instances = safelyCall(() -> api.instances(appId))
            .orElse(emptyMap())
            .entrySet()
            .stream()
            .map(inst -> {
              HealthState healthState = HealthState.Unknown;
              switch (inst.getValue().getState()) {
                case RUNNING:
                  healthState = HealthState.Up;
                  break;
                case DOWN:
                case CRASHED:
                  healthState = HealthState.Down;
                  break;
                case STARTING:
                  healthState = HealthState.Starting;
                  break;
              }
              return CloudFoundryInstance.builder()
                .appGuid(appId)
                .key(inst.getKey())
                .healthState(healthState)
                .details(inst.getValue().getDetails())
                .launchTime(System.currentTimeMillis() - (inst.getValue().getUptime() * 1000))
                .zone(space == null ? "unknown" : space.getName())
                .build();
            }).collect(toSet());

          log.debug("Successfully retrieved " + instances.size() + " instances for application '" + application.getName() + "'");
        } catch (RetrofitError e) {
          try {
            log.debug("Unable to retrieve instances for application '" + application.getName() + "': " +
              IOUtils.toString(e.getResponse().getBody().in(), Charset.defaultCharset()));
          } catch (IOException e1) {
            log.debug("Unable to retrieve instances for application '" + application.getName());
          }
          instances = emptySet();
        }
    }

    CloudFoundryDroplet droplet = null;
    try {
      CloudFoundryPackage cfPackage = safelyCall(() -> api.findPackagesByAppId(appId))
        .map(packages ->
          packages.getResources().stream().findFirst()
            .map(pkg -> CloudFoundryPackage.builder()
              .downloadUrl(pkg.getLinks().containsKey("download") ? pkg.getLinks().get("download").getHref() : null)
              .checksumType(pkg.getData().getChecksum() == null ? null : pkg.getData().getChecksum().getType())
              .checksum(pkg.getData().getChecksum() == null ? null : pkg.getData().getChecksum().getValue())
              .build()
            )
            .orElse(null)
        )
        .orElse(null);

      droplet = safelyCall(() -> api.findDropletByApplicationGuid(appId))
        .map(apiDroplet ->
          CloudFoundryDroplet.builder()
            .id(apiDroplet.getGuid())
            .name(application.getName() + "-droplet")
            .stack(apiDroplet.getStack())
            .buildpacks(ofNullable(apiDroplet.getBuildpacks())
              .orElse(emptyList())
              .stream()
              .map(bp -> CloudFoundryBuildpack.builder()
                .name(bp.getName())
                .detectOutput(bp.getDetectOutput())
                .version(bp.getVersion())
                .buildpackName(bp.getBuildpackName())
                .build()
              )
              .collect(toList())
            )
            .space(space)
            .sourcePackage(cfPackage)
            .build()
        )
        .orElse(null);
    } catch (RetrofitError ignored) {
      log.debug("Unable to retrieve droplet for application '" + application.getName() + "'");
    }

    List<CloudFoundryServiceInstance> cloudFoundryServices = applicationEnv == null ? emptyList() :
      applicationEnv.getSystemEnvJson().getVcapServices()
        .entrySet()
        .stream()
        .flatMap(vcap -> vcap.getValue().stream()
          .map(instance -> CloudFoundryServiceInstance.builder()
            .serviceInstanceName(vcap.getKey())
            .name(instance.getName())
            .plan(instance.getPlan())
            .tags(instance.getTags())
            .build()))
        .collect(toList());

    Map<String, String> environmentVars = applicationEnv == null || applicationEnv.getEnvironmentJson() == null ? emptyMap() : applicationEnv.getEnvironmentJson();

    String healthCheckType = null;
    String healthCheckHttpEndpoint = null;
    if (process != null && process.getHealthCheck() != null) {
      final Process.HealthCheck healthCheck = process.getHealthCheck();
      healthCheckType = healthCheck.getType();
      if (healthCheck.getData() != null) {
        healthCheckHttpEndpoint = healthCheck.getData().getEndpoint();
      }
    }

    String serverGroupAppManagerUri = appsManagerUri;
    if (StringUtils.isNotEmpty(appsManagerUri)) {
      serverGroupAppManagerUri = appsManagerUri + "/organizations/" + space.getOrganization().getId() + "/spaces/" + space.getId() + "/applications/" + appId;
    }

    String serverGroupMetricsUri = metricsUri;
    if (StringUtils.isNotEmpty(metricsUri)) {
      serverGroupMetricsUri = metricsUri + "/apps/" + appId;
    }

    return CloudFoundryServerGroup.builder()
      .account(account)
      .appsManagerUri(serverGroupAppManagerUri)
      .metricsUri(serverGroupMetricsUri)
      .name(application.getName())
      .id(appId)
      .memory(process != null ? process.getMemoryInMb() : null)
      .instances(emptySet())
      .droplet(droplet)
      .diskQuota(process != null ? process.getDiskInMb() : null)
      .healthCheckType(healthCheckType)
      .healthCheckHttpEndpoint(healthCheckHttpEndpoint)
      .space(space)
      .createdTime(application.getCreatedAt().toInstant().toEpochMilli())
      .serviceInstances(cloudFoundryServices)
      .instances(instances)
      .state(state)
      .env(environmentVars)
      .build();
  }

  public void mapRoute(String applicationGuid, String routeGuid) throws CloudFoundryApiException {
    safelyCall(() -> api.mapRoute(applicationGuid, routeGuid, new MapRoute()));
  }

  public void unmapRoute(String applicationGuid, String routeGuid) throws CloudFoundryApiException {
    safelyCall(() -> api.unmapRoute(applicationGuid, routeGuid));
  }

  public void startApplication(String applicationGuid) throws CloudFoundryApiException {
    safelyCall(() -> api.startApplication(applicationGuid, new StartApplication()));
  }

  public void stopApplication(String applicationGuid) throws CloudFoundryApiException {
    safelyCall(() -> api.stopApplication(applicationGuid, new StopApplication()));
  }

  public void deleteApplication(String applicationGuid) throws CloudFoundryApiException {
    safelyCall(() -> api.deleteApplication(applicationGuid));
  }

  public void deleteAppInstance(String guid, String index) throws CloudFoundryApiException {
    safelyCall(() -> api.deleteAppInstance(guid, index));
  }

  public CloudFoundryServerGroup createApplication(String appName, CloudFoundrySpace space, List<String> buildpacks,
                                                   @Nullable Map<String, String> environmentVariables) throws CloudFoundryApiException {
    Map<String, ToOneRelationship> relationships = new HashMap<>();
    relationships.put("space", new ToOneRelationship(new Relationship(space.getId())));

    return safelyCall(() -> api.createApplication(new CreateApplication(appName, relationships, environmentVariables, buildpacks)))
      .map(this::map)
      .orElseThrow(() -> new CloudFoundryApiException("Cloud Foundry signaled that application creation succeeded but failed to provide a response."));
  }

  public void scaleApplication(String guid, @Nullable Integer instances, @Nullable Integer memInMb, @Nullable Integer diskInMb) throws CloudFoundryApiException {
    if ((memInMb == null && diskInMb == null && instances == null) ||
      (Integer.valueOf(0).equals(memInMb) && Integer.valueOf(0).equals(diskInMb) && Integer.valueOf(0).equals(instances))) {
      return;
    }
    safelyCall(() -> api.scaleApplication(guid, new ScaleApplication(instances, memInMb, diskInMb)));
  }

  public void updateProcess(String guid, @Nullable String command, @Nullable String healthCheckType, @Nullable String healthCheckEndpoint) throws CloudFoundryApiException {
    final Process.HealthCheck healthCheck = healthCheckType != null ?
      new Process.HealthCheck().setType(healthCheckType) : null;
    if (healthCheckEndpoint != null && !healthCheckEndpoint.isEmpty()) {
      healthCheck.setData(new Process.HealthCheckData().setEndpoint(healthCheckEndpoint));
    }
    safelyCall(() -> api.updateProcess(guid, new UpdateProcess(command, healthCheck)));
  }

  public String createPackage(String appGuid) throws CloudFoundryApiException {
    return safelyCall(() -> api.createPackage(new CreatePackage(appGuid)))
      .map(Package::getGuid)
      .orElseThrow(() -> new CloudFoundryApiException("Cloud Foundry signaled that package creation succeeded but failed to provide a response."));
  }

  @Nullable
  public String findCurrentPackageIdByAppId(String appGuid) throws CloudFoundryApiException {
    return safelyCall(() -> this.api.findDropletByApplicationGuid(appGuid))
      .map(droplet -> StringUtils.substringAfterLast(droplet.getLinks().get("package").getHref(), "/"))
      .orElse(null);
  }

  @Nullable
  public InputStream downloadPackageBits(String packageGuid) throws CloudFoundryApiException {
    try {
      Optional<TypedInput> packageInput = safelyCall(() -> api.downloadPackage(packageGuid))
        .map(Response::getBody);
      return packageInput.isPresent() ? packageInput.get().in() : null;
    } catch (IOException e) {
      throw new CloudFoundryApiException(e, "Failed to retrieve input stream of package bits.");
    }
  }

  public String uploadPackageBits(String packageGuid, File file) throws CloudFoundryApiException {
    TypedFile uploadFile = new TypedFile("multipart/form-data", file);
    return safelyCall(() -> api.uploadPackageBits(packageGuid, uploadFile))
      .map(Package::getGuid)
      .orElseThrow(() -> new CloudFoundryApiException("Cloud Foundry signaled that package upload succeeded but failed to provide a response."));
  }

  public String createBuild(String packageGuid) throws CloudFoundryApiException {
    return safelyCall(() -> api.createBuild(new CreateBuild(packageGuid)))
      .map(Build::getGuid)
      .orElseThrow(() -> new CloudFoundryApiException("Cloud Foundry signaled that build creation succeeded but failed to provide a response."));
  }

  public Boolean buildCompleted(String buildGuid) throws CloudFoundryApiException {
    switch (safelyCall(() -> api.getBuild(buildGuid)).map(Build::getState).orElse(Build.State.FAILED)) {
      case FAILED:
        throw new CloudFoundryApiException("Failed to build droplet");
      case STAGED:
        return true;
      default:
        return false;
    }
  }

  public boolean packageUploadComplete(String packageGuid) throws CloudFoundryApiException {
    switch (safelyCall(() -> api.getPackage(packageGuid)).map(Package::getState).orElse(Package.State.FAILED)) {
      case FAILED:
      case EXPIRED:
        throw new CloudFoundryApiException("Upload failed");
      case READY:
        return true;
      default:
        return false;
    }
  }

  public String findDropletGuidFromBuildId(String buildGuid) throws CloudFoundryApiException {
    return safelyCall(() -> api.getBuild(buildGuid))
      .map(Build::getDroplet)
      .map(Droplet::getGuid)
      .orElse(null);
  }

  public void setCurrentDroplet(String appGuid, String dropletGuid) throws CloudFoundryApiException {
    safelyCall(() -> api.setCurrentDroplet(appGuid, new ToOneRelationship(new Relationship(dropletGuid))));
  }

  @Nullable
  public ProcessStats.State getProcessState(String appGuid) throws CloudFoundryApiException {
    return safelyCall(() -> api.findProcessStatsById(appGuid))
      .map(pr -> pr.getResources().stream().findAny().map(ProcessStats::getState)
        .orElseGet(() ->
          Optional.ofNullable(api.findById(appGuid))
            .filter(application -> CloudFoundryServerGroup.State.STARTED.equals(CloudFoundryServerGroup.State.valueOf(application.getState())))
            .map(appState -> ProcessStats.State.RUNNING)
            .orElse(ProcessStats.State.DOWN)
        ))
      .orElse(ProcessStats.State.DOWN);
  }

  public List<Resource<com.netflix.spinnaker.clouddriver.cloudfoundry.client.model.v2.Application>> getTakenSlots(String clusterName, String spaceId) {
    List<String> filter = asList("name<" + clusterName + "-v9999", "name>=" + clusterName, "space_guid:" + spaceId);
    return collectPageResources("applications", page -> api.listAppsFiltered(page, filter, 10))
      .stream()
      .filter(app -> {
        Names names = Names.parseName(app.getEntity().getName());
        return clusterName.equals(names.getCluster());
      })
      .collect(Collectors.toList());
  }
}
