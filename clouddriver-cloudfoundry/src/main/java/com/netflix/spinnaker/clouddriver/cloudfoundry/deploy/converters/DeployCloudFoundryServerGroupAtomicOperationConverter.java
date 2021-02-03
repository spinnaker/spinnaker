/*
 * Copyright 2018 Pivotal, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.clouddriver.cloudfoundry.deploy.converters;

import static io.vavr.API.*;
import static java.util.stream.Collectors.toList;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import com.netflix.spinnaker.clouddriver.artifacts.ArtifactCredentialsRepository;
import com.netflix.spinnaker.clouddriver.artifacts.config.ArtifactCredentials;
import com.netflix.spinnaker.clouddriver.cloudfoundry.CloudFoundryOperation;
import com.netflix.spinnaker.clouddriver.cloudfoundry.artifacts.CloudFoundryArtifactCredentials;
import com.netflix.spinnaker.clouddriver.cloudfoundry.client.model.v3.Docker;
import com.netflix.spinnaker.clouddriver.cloudfoundry.deploy.description.DeployCloudFoundryServerGroupDescription;
import com.netflix.spinnaker.clouddriver.cloudfoundry.deploy.ops.DeployCloudFoundryServerGroupAtomicOperation;
import com.netflix.spinnaker.clouddriver.cloudfoundry.security.CloudFoundryCredentials;
import com.netflix.spinnaker.clouddriver.docker.registry.security.DockerRegistryNamedAccountCredentials;
import com.netflix.spinnaker.clouddriver.helpers.OperationPoller;
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation;
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperations;
import com.netflix.spinnaker.kork.artifacts.model.Artifact;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
import lombok.Data;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

@CloudFoundryOperation(AtomicOperations.CREATE_SERVER_GROUP)
@Component
public class DeployCloudFoundryServerGroupAtomicOperationConverter
    extends AbstractCloudFoundryServerGroupAtomicOperationConverter {
  private final OperationPoller operationPoller;
  private final ArtifactCredentialsRepository credentialsRepository;
  private final List<? extends DockerRegistryNamedAccountCredentials>
      dockerRegistryNamedAccountCredentials;

  public DeployCloudFoundryServerGroupAtomicOperationConverter(
      @Qualifier("cloudFoundryOperationPoller") OperationPoller operationPoller,
      ArtifactCredentialsRepository credentialsRepository,
      List<? extends DockerRegistryNamedAccountCredentials> dockerRegistryNamedAccountCredentials) {
    this.operationPoller = operationPoller;
    this.credentialsRepository = credentialsRepository;
    this.dockerRegistryNamedAccountCredentials = dockerRegistryNamedAccountCredentials;
  }

  @Override
  public AtomicOperation convertOperation(Map input) {
    return new DeployCloudFoundryServerGroupAtomicOperation(
        operationPoller, convertDescription(input));
  }

  @Override
  public DeployCloudFoundryServerGroupDescription convertDescription(Map input) {
    DeployCloudFoundryServerGroupDescription converted =
        getObjectMapper().convertValue(input, DeployCloudFoundryServerGroupDescription.class);
    CloudFoundryCredentials credentials = getCredentialsObject(input.get("credentials").toString());
    converted.setCredentials(credentials);
    converted.setClient(credentials.getClient());
    converted.setAccountName(credentials.getName());

    String region = converted.getRegion();
    converted.setSpace(
        findSpace(region, converted.getClient())
            .orElseThrow(
                () ->
                    new IllegalArgumentException(
                        "Unable to find organization and space '" + region + "'.")));

    // fail early if we're not going to be able to locate credentials to download the artifact in
    // the deploy operation.
    converted.setArtifactCredentials(getArtifactCredentials(converted));
    converted.setApplicationAttributes(
        convertManifest(
            converted.getManifest().stream().findFirst().orElse(Collections.emptyMap())));
    converted.setDocker(
        converted.getArtifactCredentials().getTypes().contains("docker/image")
            ? resolveDockerAccount(converted.getApplicationArtifact())
            : null);
    return converted;
  }

  private Docker resolveDockerAccount(Artifact artifact) {
    DockerRegistryNamedAccountCredentials dockerCreds =
        dockerRegistryNamedAccountCredentials.stream()
            .filter(reg -> reg.getRegistry().equals(artifact.getReference().split("/")[0]))
            .filter(reg -> reg.getRepositories().contains(artifact.getName().split("/", 2)[1]))
            .findFirst()
            .orElseThrow(
                () ->
                    new IllegalArgumentException(
                        "Could not find a docker registry for the docker image: "
                            + artifact.getName()));

    return Docker.builder()
        .image(artifact.getReference())
        .username(dockerCreds.getUsername())
        .password(dockerCreds.getPassword())
        .build();
  }

  private ArtifactCredentials getArtifactCredentials(
      DeployCloudFoundryServerGroupDescription converted) {
    Artifact artifact = converted.getApplicationArtifact();
    String artifactAccount = artifact.getArtifactAccount();
    if (CloudFoundryArtifactCredentials.TYPE.equals(artifact.getType())) {
      CloudFoundryCredentials credentials = getCredentialsObject(artifactAccount);
      String uuid =
          getServerGroupId(artifact.getName(), artifact.getLocation(), credentials.getClient());
      converted.setApplicationArtifact(artifact.toBuilder().uuid(uuid).build());
      return new CloudFoundryArtifactCredentials(credentials.getClient());
    }

    ArtifactCredentials credentials =
        credentialsRepository.getFirstCredentialsWithName(artifactAccount);
    if (credentials == null) {
      throw new IllegalArgumentException(
          "Unable to find artifact credentials '" + artifactAccount + "'");
    }
    return credentials;
  }

  // visible for testing
  DeployCloudFoundryServerGroupDescription.ApplicationAttributes convertManifest(
      Map<Object, Object> manifestMap) {
    List<CloudFoundryManifest> manifestApps =
        new ObjectMapper()
            .setPropertyNamingStrategy(PropertyNamingStrategy.KEBAB_CASE)
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .convertValue(
                manifestMap.get("applications"),
                new TypeReference<List<CloudFoundryManifest>>() {});

    return manifestApps.stream()
        .findFirst()
        .map(
            app -> {
              final List<String> buildpacks =
                  Match(app)
                      .of(
                          Case($(a -> a.getBuildpacks() != null), app.getBuildpacks()),
                          Case(
                              $(a -> a.getBuildpack() != null && a.getBuildpack().length() > 0),
                              Collections.singletonList(app.getBuildpack())),
                          Case($(), Collections.emptyList()));

              DeployCloudFoundryServerGroupDescription.ApplicationAttributes attrs =
                  new DeployCloudFoundryServerGroupDescription.ApplicationAttributes();
              attrs.setInstances(app.getInstances() == null ? 1 : app.getInstances());
              attrs.setMemory(app.getMemory() == null ? "1024" : app.getMemory());
              attrs.setDiskQuota(app.getDiskQuota() == null ? "1024" : app.getDiskQuota());
              attrs.setHealthCheckHttpEndpoint(app.getHealthCheckHttpEndpoint());
              attrs.setHealthCheckType(app.getHealthCheckType());
              attrs.setBuildpacks(buildpacks);
              attrs.setServices(app.getServices());
              attrs.setRoutes(
                  app.getRoutes() == null
                      ? null
                      : app.getRoutes().stream()
                          .flatMap(route -> route.values().stream())
                          .collect(toList()));
              attrs.setEnv(app.getEnv());
              attrs.setStack(app.getStack());
              attrs.setCommand(app.getCommand());
              return attrs;
            })
        .get();
  }

  @Data
  private static class CloudFoundryManifest {
    @Nullable private Integer instances;

    @Nullable private String memory;

    @Nullable
    @JsonProperty("disk_quota")
    private String diskQuota;

    @Nullable private String healthCheckType;

    @Nullable private String healthCheckHttpEndpoint;

    @Nullable private String buildpack;

    @Nullable private List<String> buildpacks;

    @Nullable private List<String> services;

    @Nullable private List<Map<String, String>> routes;

    @Nullable private Map<String, String> env;

    @Nullable private String stack;

    @Nullable private String command;
  }
}
