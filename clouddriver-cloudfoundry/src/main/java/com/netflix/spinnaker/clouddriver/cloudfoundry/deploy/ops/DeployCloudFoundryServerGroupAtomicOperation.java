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

package com.netflix.spinnaker.clouddriver.cloudfoundry.deploy.ops;

import static com.netflix.spinnaker.clouddriver.cloudfoundry.deploy.ops.CloudFoundryOperationUtils.describeProcessState;
import static com.netflix.spinnaker.clouddriver.deploy.DeploymentResult.Deployment;
import static com.netflix.spinnaker.clouddriver.deploy.DeploymentResult.Deployment.Capacity;
import static java.util.stream.Collectors.toList;

import com.netflix.spinnaker.clouddriver.artifacts.config.ArtifactCredentials;
import com.netflix.spinnaker.clouddriver.artifacts.maven.MavenArtifactCredentials;
import com.netflix.spinnaker.clouddriver.cloudfoundry.CloudFoundryCloudProvider;
import com.netflix.spinnaker.clouddriver.cloudfoundry.artifacts.CloudFoundryArtifactCredentials;
import com.netflix.spinnaker.clouddriver.cloudfoundry.client.CloudFoundryApiException;
import com.netflix.spinnaker.clouddriver.cloudfoundry.client.CloudFoundryClient;
import com.netflix.spinnaker.clouddriver.cloudfoundry.client.model.v2.CreateServiceBinding;
import com.netflix.spinnaker.clouddriver.cloudfoundry.client.model.v3.CreatePackage;
import com.netflix.spinnaker.clouddriver.cloudfoundry.client.model.v3.Lifecycle;
import com.netflix.spinnaker.clouddriver.cloudfoundry.client.model.v3.Process;
import com.netflix.spinnaker.clouddriver.cloudfoundry.client.model.v3.ProcessRequest;
import com.netflix.spinnaker.clouddriver.cloudfoundry.client.model.v3.ProcessStats;
import com.netflix.spinnaker.clouddriver.cloudfoundry.deploy.CloudFoundryServerGroupNameResolver;
import com.netflix.spinnaker.clouddriver.cloudfoundry.deploy.description.DeployCloudFoundryServerGroupDescription;
import com.netflix.spinnaker.clouddriver.cloudfoundry.model.CloudFoundryOrganization;
import com.netflix.spinnaker.clouddriver.cloudfoundry.model.CloudFoundryServerGroup;
import com.netflix.spinnaker.clouddriver.cloudfoundry.model.CloudFoundrySpace;
import com.netflix.spinnaker.clouddriver.cloudfoundry.model.ServerGroupMetaDataEnvVar;
import com.netflix.spinnaker.clouddriver.deploy.DeploymentResult;
import com.netflix.spinnaker.clouddriver.helpers.OperationPoller;
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation;
import com.netflix.spinnaker.kork.artifacts.model.Artifact;
import java.io.*;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;

@RequiredArgsConstructor
public class DeployCloudFoundryServerGroupAtomicOperation
    extends AbstractCloudFoundryLoadBalancerMappingOperation
    implements AtomicOperation<DeploymentResult> {
  private static final String PHASE = "DEPLOY";

  private final OperationPoller operationPoller;
  private final DeployCloudFoundryServerGroupDescription description;

  @Override
  protected String getPhase() {
    return PHASE;
  }

  @Override
  public DeploymentResult operate(List priorOutputs) {
    getTask().updateStatus(PHASE, "Deploying '" + description.getApplication() + "'");
    CloudFoundryClient client = description.getClient();

    CloudFoundryServerGroupNameResolver serverGroupNameResolver =
        new CloudFoundryServerGroupNameResolver(client, description.getSpace());

    description.setServerGroupName(
        serverGroupNameResolver.resolveNextServerGroupName(
            description.getApplication(),
            description.getStack(),
            description.getFreeFormDetails(),
            false));

    CloudFoundryServerGroup serverGroup = createApplication(description);
    String packageId;

    File packageArtifact =
        description.getDocker() == null ? downloadPackageArtifact(description) : null;
    try {
      packageId = buildPackage(serverGroup.getId(), description, packageArtifact);
    } finally {
      if (packageArtifact != null) {
        packageArtifact.delete();
      }
    }

    createServiceBindings(serverGroup, description);

    buildDroplet(packageId, serverGroup.getId(), description);

    // update process before scaling process
    updateProcess(serverGroup.getId(), description);
    scaleApplication(serverGroup.getId(), description);

    if (!mapRoutes(
        description,
        description.getApplicationAttributes().getRoutes(),
        description.getSpace(),
        serverGroup.getId())) {
      return deploymentResult();
    }

    final int desiredInstanceCount = description.getApplicationAttributes().getInstances();
    if (description.isStartApplication() && desiredInstanceCount > 0) {
      client.getApplications().startApplication(serverGroup.getId());
      ProcessStats.State state =
          operationPoller.waitForOperation(
              () -> client.getApplications().getAppState(serverGroup.getId()),
              inProgressState ->
                  inProgressState == ProcessStats.State.RUNNING
                      || inProgressState == ProcessStats.State.CRASHED,
              null,
              getTask(),
              description.getServerGroupName(),
              PHASE);

      if (state != ProcessStats.State.RUNNING) {
        throw new CloudFoundryApiException(
            "Failed to start '"
                + description.getServerGroupName()
                + "' which instead "
                + describeProcessState(state));
      }
    } else {
      getTask()
          .updateStatus(PHASE, "Stop state requested for '" + description.getServerGroupName());
    }

    getTask().updateStatus(PHASE, "Deployed '" + description.getApplication() + "'");

    return deploymentResult();
  }

  private void createServiceBindings(
      CloudFoundryServerGroup serverGroup, DeployCloudFoundryServerGroupDescription description) {

    List<String> serviceNames = description.getApplicationAttributes().getServices();
    if (serviceNames == null || serviceNames.isEmpty()) return;

    getTask()
        .updateStatus(
            PHASE,
            "Creating Cloud Foundry service bindings between application '"
                + description.getServerGroupName()
                + "' and services: "
                + description.getApplicationAttributes().getServices());

    Map<String, String> serviceInstanceGuids = new HashMap<>();

    // find guids for services
    description
        .getClient()
        .getServiceInstances()
        .findAllServicesBySpaceAndNames(serverGroup.getSpace(), serviceNames)
        .forEach(s -> serviceInstanceGuids.put(s.getEntity().getName(), s.getMetadata().getGuid()));

    // try and create service binding request for each service
    List<CreateServiceBinding> bindings =
        serviceNames.stream()
            .map(
                name -> {
                  String serviceGuid = serviceInstanceGuids.get(name);
                  if (serviceGuid == null || serviceGuid.isEmpty()) {
                    getTask()
                        .updateStatus(
                            PHASE,
                            "Failed to create Cloud Foundry service bindings between application '"
                                + description.getServerGroupName()
                                + "' and services: "
                                + serviceNames);

                    throw new CloudFoundryApiException(
                        "Unable to find service with the name: '"
                            + name
                            + "' in "
                            + serverGroup.getSpace());
                  }

                  return new CreateServiceBinding(
                      serviceGuid, serverGroup.getId(), name, Collections.emptyMap());
                })
            .collect(Collectors.toList());

    bindings.forEach(b -> description.getClient().getServiceInstances().createServiceBinding(b));

    getTask()
        .updateStatus(
            PHASE,
            "Created Cloud Foundry service bindings between application '"
                + description.getServerGroupName()
                + "' and services: "
                + description.getApplicationAttributes().getServices());
  }

  private DeploymentResult deploymentResult() {
    DeploymentResult deploymentResult = new DeploymentResult();
    deploymentResult.setServerGroupNames(
        Collections.singletonList(
            description.getRegion() + ":" + description.getServerGroupName()));
    deploymentResult
        .getServerGroupNameByRegion()
        .put(description.getRegion(), description.getServerGroupName());
    deploymentResult.setMessages(
        getTask().getHistory().stream()
            .map(hist -> hist.getPhase() + ":" + hist.getStatus())
            .collect(toList()));
    List<String> routes = description.getApplicationAttributes().getRoutes();
    if (routes == null) {
      routes = Collections.emptyList();
    }
    final Integer desiredInstanceCount = description.getApplicationAttributes().getInstances();
    final Deployment deployment = new Deployment();
    deployment.setCloudProvider(CloudFoundryCloudProvider.ID);
    deployment.setAccount(description.getAccountName());
    deployment.setServerGroupName(description.getServerGroupName());
    final Capacity capacity = new Capacity();
    capacity.setDesired(desiredInstanceCount);
    deployment.setCapacity(capacity);
    final Map<String, Object> metadata = new HashMap<>();
    metadata.put("env", description.getApplicationAttributes().getEnv());
    metadata.put("routes", routes);
    deployment.setMetadata(metadata);
    if (!routes.isEmpty()) {
      deployment.setLocation(routes.get(0));
    }
    deploymentResult.setDeployments(Collections.singleton(deployment));
    return deploymentResult;
  }

  private static CloudFoundryServerGroup createApplication(
      DeployCloudFoundryServerGroupDescription description) {
    CloudFoundryClient client = description.getClient();
    getTask()
        .updateStatus(
            PHASE, "Creating Cloud Foundry application '" + description.getServerGroupName() + "'");

    Lifecycle lifecycle =
        description.getDocker() == null
            ? new Lifecycle(Lifecycle.Type.BUILDPACK, description.getApplicationAttributes())
            : new Lifecycle(Lifecycle.Type.DOCKER, description.getApplicationAttributes());

    CloudFoundryServerGroup serverGroup =
        client
            .getApplications()
            .createApplication(
                description.getServerGroupName(),
                description.getSpace(),
                getEnvironmentVars(description),
                lifecycle);
    getTask()
        .updateStatus(
            PHASE, "Created Cloud Foundry application '" + description.getServerGroupName() + "'");

    return serverGroup;
  }

  private static Map<String, String> getEnvironmentVars(
      DeployCloudFoundryServerGroupDescription description) {
    Map<String, String> environmentVars =
        Optional.ofNullable(description.getApplicationAttributes().getEnv())
            .map(HashMap::new)
            .orElse(new HashMap<>());

    final Artifact applicationArtifact = description.getApplicationArtifact();
    if (CloudFoundryArtifactCredentials.TYPE.equals(applicationArtifact.getType())) {
      CloudFoundryClient client = description.getClient();
      final CloudFoundrySpace orgAndSpaceName =
          CloudFoundrySpace.fromRegion(applicationArtifact.getLocation());
      final Optional<CloudFoundryOrganization> orgOptional =
          client.getOrganizations().findByName(orgAndSpaceName.getOrganization().getName());
      orgOptional.ifPresent(
          org -> {
            final CloudFoundrySpace space =
                client.getSpaces().findByName(org.getId(), orgAndSpaceName.getName());
            if (space != null) {
              final CloudFoundryServerGroup serverGroup =
                  client
                      .getApplications()
                      .findServerGroupByNameAndSpaceId(
                          applicationArtifact.getName(), space.getId());
              if (serverGroup != null) {
                serverGroup.getEnv().entrySet().stream()
                    .filter(e -> e.getKey().startsWith(ServerGroupMetaDataEnvVar.PREFIX))
                    .forEach(i -> environmentVars.put(i.getKey(), i.getValue().toString()));
              }
            }
          });
    }

    final ExternalReference artifactInfo = resolveArtifactInfo(description);
    artifactInfo
        .getName()
        .map(name -> environmentVars.put(ServerGroupMetaDataEnvVar.ArtifactName.envVarName, name));
    artifactInfo
        .getNumber()
        .map(
            number ->
                environmentVars.put(ServerGroupMetaDataEnvVar.ArtifactVersion.envVarName, number));
    artifactInfo
        .getUrl()
        .map(url -> environmentVars.put(ServerGroupMetaDataEnvVar.ArtifactUrl.envVarName, url));
    final ExternalReference buildInfo = resolveBuildInfo(description);
    buildInfo
        .getName()
        .map(name -> environmentVars.put(ServerGroupMetaDataEnvVar.JobName.envVarName, name));
    buildInfo
        .getNumber()
        .map(number -> environmentVars.put(ServerGroupMetaDataEnvVar.JobNumber.envVarName, number));
    buildInfo
        .getUrl()
        .map(url -> environmentVars.put(ServerGroupMetaDataEnvVar.JobUrl.envVarName, url));
    Optional.ofNullable(description.getExecutionId())
        .ifPresent(
            executionId ->
                environmentVars.put(ServerGroupMetaDataEnvVar.PipelineId.envVarName, executionId));

    return environmentVars;
  }

  private static ExternalReference resolveArtifactInfo(
      DeployCloudFoundryServerGroupDescription description) {
    return Optional.ofNullable(description.getApplicationArtifact())
        .map(
            applicationArtifact -> {
              final ExternalReference.ExternalReferenceBuilder artifactInfo =
                  ExternalReference.builder();
              if (MavenArtifactCredentials.TYPES.contains(applicationArtifact.getType())) {
                final ArtifactCredentials artifactCredentials =
                    description.getArtifactCredentials();
                artifactInfo
                    .name(artifactCredentials.resolveArtifactName(applicationArtifact))
                    .number(artifactCredentials.resolveArtifactVersion(applicationArtifact))
                    .url(Optional.ofNullable(applicationArtifact.getLocation()));
              }
              return artifactInfo.build();
            })
        .orElseGet(() -> ExternalReference.builder().build());
  }

  private static ExternalReference resolveBuildInfo(
      DeployCloudFoundryServerGroupDescription description) {
    Map<String, Object> buildInfo = null;
    final Artifact applicationArtifact = description.getApplicationArtifact();
    if (applicationArtifact != null) {
      buildInfo = (Map<String, Object>) applicationArtifact.getMetadata("build");
    }
    if (buildInfo == null) {
      final Map<String, Object> trigger = description.getTrigger();
      if (trigger != null) {
        final String triggerType = (String) trigger.get("type");
        if (triggerType.equals("jenkins") || triggerType.equals("manual")) {
          final Map<String, Object> triggerBuildInfo =
              (Map<String, Object>) trigger.get("buildInfo");
          if (triggerBuildInfo != null) {
            buildInfo = triggerBuildInfo;
          }
        }
      }
    }
    return Optional.ofNullable(buildInfo)
        .map(
            buildInfoMap ->
                ExternalReference.builder()
                    .name(Optional.ofNullable(buildInfoMap.get("name")).map(Object::toString))
                    .number(Optional.ofNullable(buildInfoMap.get("number")).map(Object::toString))
                    .url(Optional.ofNullable(buildInfoMap.get("url")).map(Object::toString))
                    .build())
        .orElse(ExternalReference.builder().build());
  }

  @Data
  @Builder
  private static class ExternalReference {
    @Builder.Default private Optional<String> name = Optional.empty();

    @Builder.Default private Optional<String> number = Optional.empty();

    @Builder.Default private Optional<String> url = Optional.empty();
  }

  @NotNull
  private File downloadPackageArtifact(DeployCloudFoundryServerGroupDescription description) {
    File file = null;
    try {
      file = File.createTempFile(UUID.randomUUID().toString(), null);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    } finally {
      if (file != null) file.delete();
    }

    try (InputStream artifactInputStream =
            description.getArtifactCredentials().download(description.getApplicationArtifact());
        FileOutputStream fileOutputStream = new FileOutputStream(file)) {
      IOUtils.copy(artifactInputStream, fileOutputStream);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
    return file;
  }

  private String buildPackage(
      String serverGroupId,
      DeployCloudFoundryServerGroupDescription description,
      File packageArtifact) {
    CloudFoundryClient client = description.getClient();
    getTask()
        .updateStatus(
            PHASE, "Creating package for application '" + description.getServerGroupName() + "'");

    String packageId;
    if (packageArtifact != null) {
      // Bits Package
      packageId =
          client
              .getApplications()
              .createPackage(new CreatePackage(serverGroupId, CreatePackage.Type.BITS, null));
      client.getApplications().uploadPackageBits(packageId, packageArtifact);
    } else {
      // Docker Package
      packageId =
          client
              .getApplications()
              .createPackage(
                  new CreatePackage(
                      serverGroupId, CreatePackage.Type.DOCKER, description.getDocker()));
    }

    operationPoller.waitForOperation(
        () -> client.getApplications().packageUploadComplete(packageId),
        Function.identity(),
        null,
        getTask(),
        description.getServerGroupName(),
        PHASE);

    getTask()
        .updateStatus(
            PHASE,
            "Completed creating package for application '"
                + description.getServerGroupName()
                + "'");

    return packageId;
  }

  private void buildDroplet(
      String packageId,
      String serverGroupId,
      DeployCloudFoundryServerGroupDescription description) {
    CloudFoundryClient client = description.getClient();
    getTask().updateStatus(PHASE, "Building droplet for package '" + packageId + "'");

    String buildId = client.getApplications().createBuild(packageId);

    operationPoller.waitForOperation(
        () -> client.getApplications().buildCompleted(buildId),
        Function.identity(),
        null,
        getTask(),
        description.getServerGroupName(),
        PHASE);

    String dropletGuid = client.getApplications().findDropletGuidFromBuildId(buildId);

    client.getApplications().setCurrentDroplet(serverGroupId, dropletGuid);
    getTask().updateStatus(PHASE, "Droplet built for package '" + packageId + "'");
  }

  private void scaleApplication(
      String serverGroupId, DeployCloudFoundryServerGroupDescription description) {
    CloudFoundryClient client = description.getClient();
    getTask().updateStatus(PHASE, "Scaling application '" + description.getServerGroupName() + "'");

    Integer memoryAmount =
        convertToMb("memory", description.getApplicationAttributes().getMemory());
    Integer diskSizeAmount =
        convertToMb("disk quota", description.getApplicationAttributes().getDiskQuota());
    client
        .getProcesses()
        .scaleProcess(
            serverGroupId,
            description.getApplicationAttributes().getInstances(),
            memoryAmount,
            diskSizeAmount);

    if (!description.getApplicationAttributes().getProcesses().isEmpty()) {
      List<Process> processes = client.getProcesses().getAllProcessesByAppId(serverGroupId);

      for (ProcessRequest req : description.getApplicationAttributes().getProcesses()) {
        String processGuid = getProcessGuidByType(processes, req.getType());

        Integer pMemoryAmount = convertToMb("memory", req.getMemory());
        Integer pDiskSizeAmount = convertToMb("disk quota", req.getDiskQuota());
        client
            .getProcesses()
            .scaleProcess(processGuid, req.getInstances(), pMemoryAmount, pDiskSizeAmount);
      }
    }
    getTask().updateStatus(PHASE, "Scaled application '" + description.getServerGroupName() + "'");
  }

  private void updateProcess(
      String serverGroupId, DeployCloudFoundryServerGroupDescription description) {
    CloudFoundryClient client = description.getClient();
    getTask().updateStatus(PHASE, "Updating process '" + description.getServerGroupName() + "'");

    client
        .getProcesses()
        .updateProcess(
            serverGroupId,
            description.getApplicationAttributes().getCommand(),
            description.getApplicationAttributes().getHealthCheckType(),
            description.getApplicationAttributes().getHealthCheckHttpEndpoint(),
            description.getApplicationAttributes().getTimeout(),
            null);

    if (!description.getApplicationAttributes().getProcesses().isEmpty()) {
      List<Process> processes = client.getProcesses().getAllProcessesByAppId(serverGroupId);

      for (ProcessRequest req : description.getApplicationAttributes().getProcesses()) {
        String processGuid = getProcessGuidByType(processes, req.getType());

        client
            .getProcesses()
            .updateProcess(
                processGuid,
                req.getCommand(),
                req.getHealthCheckType(),
                req.getHealthCheckHttpEndpoint(),
                req.getTimeout(),
                req.getHealthCheckInvocationTimeout());
      }
    }

    getTask().updateStatus(PHASE, "Updated process '" + description.getServerGroupName() + "'");
  }

  // VisibleForTesting
  @Nullable
  static Integer convertToMb(String field, @Nullable String size) {
    if (size == null) {
      return null;
    } else if (StringUtils.isNumeric(size)) {
      return Integer.parseInt(size);
    } else {
      size = size.toLowerCase();
      if (size.endsWith("g") || size.endsWith("gb")) {
        String value = size.substring(0, size.indexOf("g"));
        if (StringUtils.isNumeric(value)) return Integer.parseInt(value) * 1024;
      } else if (size.endsWith("m") || size.endsWith("mb")) {
        String value = size.substring(0, size.indexOf("m"));
        if (StringUtils.isNumeric(value)) return Integer.parseInt(value);
      }
    }

    throw new IllegalArgumentException(
        String.format("Invalid size for application %s = '%s'", field, size));
  }

  // Helper method for filtering and returning a process guid by type
  private String getProcessGuidByType(List<Process> processes, String type) {
    return processes.stream()
        .filter(p -> p.getType().equalsIgnoreCase(type))
        .map(Process::getGuid)
        .findFirst()
        .orElseThrow(
            () -> new CloudFoundryApiException("Unable to find a process with type: " + type));
  }
}
