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

import com.netflix.spinnaker.clouddriver.cloudfoundry.CloudFoundryCloudProvider;
import com.netflix.spinnaker.clouddriver.cloudfoundry.client.CloudFoundryApiException;
import com.netflix.spinnaker.clouddriver.cloudfoundry.client.CloudFoundryClient;
import com.netflix.spinnaker.clouddriver.cloudfoundry.client.model.v3.ProcessStats;
import com.netflix.spinnaker.clouddriver.cloudfoundry.deploy.CloudFoundryServerGroupNameResolver;
import com.netflix.spinnaker.clouddriver.cloudfoundry.deploy.description.DeployCloudFoundryServerGroupDescription;
import com.netflix.spinnaker.clouddriver.cloudfoundry.model.CloudFoundryServerGroup;
import com.netflix.spinnaker.clouddriver.deploy.DeploymentResult;
import com.netflix.spinnaker.clouddriver.helpers.OperationPoller;
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation;
import lombok.RequiredArgsConstructor;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;

import javax.annotation.Nullable;
import java.io.*;
import java.util.*;
import java.util.function.Function;

import static com.netflix.spinnaker.clouddriver.cloudfoundry.deploy.ops.CloudFoundryOperationUtils.describeProcessState;
import static com.netflix.spinnaker.clouddriver.deploy.DeploymentResult.*;
import static com.netflix.spinnaker.clouddriver.deploy.DeploymentResult.Deployment.*;
import static java.util.stream.Collectors.toList;

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

    CloudFoundryServerGroupNameResolver serverGroupNameResolver = new CloudFoundryServerGroupNameResolver(client,
      description.getSpace());

    description.setServerGroupName(serverGroupNameResolver.resolveNextServerGroupName(description.getApplication(),
      description.getStack(), description.getFreeFormDetails(), false));

    CloudFoundryServerGroup serverGroup;
    String packageId;
    // we download the package artifact first, because if this fails, we don't want to create an empty CF app
    File packageArtifact = downloadPackageArtifact(description);
    try {
      serverGroup = createApplication(description);
      packageId = buildPackage(serverGroup.getId(), description, packageArtifact);
    } finally {
      if (packageArtifact != null) {
        packageArtifact.delete();
      }
    }

    buildDroplet(packageId, serverGroup.getId(), description);
    scaleApplication(serverGroup.getId(), description);
    if (description.getApplicationAttributes().getHealthCheckType() != null) {
      updateProcess(serverGroup.getId(), description);
    }

    client.getServiceInstances().createServiceBindingsByName(serverGroup, description.getApplicationAttributes().getServices());

    if (!mapRoutes(description, description.getApplicationAttributes().getRoutes(), description.getSpace(), serverGroup.getId())) {
      return deploymentResult();
    }

    final Integer desiredInstanceCount = description.getApplicationAttributes().getInstances();
    if (description.isStartApplication() && desiredInstanceCount > 0) {
      client.getApplications().startApplication(serverGroup.getId());
      ProcessStats.State state = operationPoller.waitForOperation(
        () -> client.getApplications().getProcessState(serverGroup.getId()),
        inProgressState -> inProgressState == ProcessStats.State.RUNNING || inProgressState == ProcessStats.State.CRASHED,
        null, getTask(), description.getServerGroupName(), PHASE);

      if (state != ProcessStats.State.RUNNING) {
        throw new CloudFoundryApiException("Failed to start '" + description.getServerGroupName() + "' which instead " + describeProcessState(state));
      }
    } else {
      getTask().updateStatus(PHASE, "Stop state requested for '" + description.getServerGroupName());
    }

    getTask().updateStatus(PHASE, "Deployed '" + description.getApplication() + "'");

    return deploymentResult();
  }

  private DeploymentResult deploymentResult() {
    DeploymentResult deploymentResult = new DeploymentResult();
    deploymentResult.setServerGroupNames(Collections.singletonList(description.getRegion() + ":" + description.getServerGroupName()));
    deploymentResult.getServerGroupNameByRegion().put(description.getRegion(), description.getServerGroupName());
    deploymentResult.setMessages(getTask().getHistory().stream()
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

  private static CloudFoundryServerGroup createApplication(DeployCloudFoundryServerGroupDescription description) {
    CloudFoundryClient client = description.getClient();
    getTask().updateStatus(PHASE, "Creating Cloud Foundry application '" + description.getServerGroupName() + "'");

    CloudFoundryServerGroup serverGroup = client.getApplications().createApplication(description.getServerGroupName(),
      description.getSpace(),
      description.getApplicationAttributes().getBuildpacks(),
      description.getApplicationAttributes().getEnv());
    getTask().updateStatus(PHASE, "Created Cloud Foundry application '" + description.getServerGroupName() + "'");

    return serverGroup;
  }

  @Nullable
  private File downloadPackageArtifact(DeployCloudFoundryServerGroupDescription description) {
    File file = null;
    try {
      InputStream artifactInputStream = description.getArtifactCredentials().download(description.getApplicationArtifact());
      file = File.createTempFile(UUID.randomUUID().toString(), null);
      FileOutputStream fileOutputStream = new FileOutputStream(file);
      IOUtils.copy(artifactInputStream, fileOutputStream);
      fileOutputStream.close();
    } catch (IOException e) {
      if (file != null) {
        file.delete();
      }
      throw new UncheckedIOException(e);
    }
    return file;
  }

  private String buildPackage(String serverGroupId, DeployCloudFoundryServerGroupDescription description, File packageArtifact) {
    CloudFoundryClient client = description.getClient();
    getTask().updateStatus(PHASE, "Creating package for application '" + description.getServerGroupName() + "'");

    String packageId = client.getApplications().createPackage(serverGroupId);
    client.getApplications().uploadPackageBits(packageId, packageArtifact);

    operationPoller.waitForOperation(
      () -> client.getApplications().packageUploadComplete(packageId),
      Function.identity(), null, getTask(), description.getServerGroupName(), PHASE);

    getTask().updateStatus(PHASE, "Completed creating package for application '" + description.getServerGroupName() + "'");

    return packageId;
  }

  private void buildDroplet(String packageId, String serverGroupId, DeployCloudFoundryServerGroupDescription description) {
    CloudFoundryClient client = description.getClient();
    getTask().updateStatus(PHASE, "Building droplet for package '" + packageId + "'");

    String buildId = client.getApplications().createBuild(packageId);

    operationPoller.waitForOperation(() -> client.getApplications().buildCompleted(buildId),
      Function.identity(), null, getTask(), description.getServerGroupName(), PHASE);

    String dropletGuid = client.getApplications().findDropletGuidFromBuildId(buildId);

    client.getApplications().setCurrentDroplet(serverGroupId, dropletGuid);
    getTask().updateStatus(PHASE, "Droplet built for package '" + packageId + "'");
  }

  private void scaleApplication(String serverGroupId, DeployCloudFoundryServerGroupDescription description) {
    CloudFoundryClient client = description.getClient();
    getTask().updateStatus(PHASE, "Scaling application '" + description.getServerGroupName() + "'");

    Integer memoryAmount = convertToMb("memory", description.getApplicationAttributes().getMemory());
    Integer diskSizeAmount = convertToMb("disk quota", description.getApplicationAttributes().getDiskQuota());

    client.getApplications().scaleApplication(serverGroupId, description.getApplicationAttributes().getInstances(), memoryAmount, diskSizeAmount);
    getTask().updateStatus(PHASE, "Scaled application '" + description.getServerGroupName() + "'");
  }

  private void updateProcess(String serverGroupId, DeployCloudFoundryServerGroupDescription description) {
    CloudFoundryClient client = description.getClient();
    getTask().updateStatus(PHASE, "Updating process '" + description.getServerGroupName() + "'");
    client.getApplications().updateProcess(serverGroupId, null,
      description.getApplicationAttributes().getHealthCheckType(),
      description.getApplicationAttributes().getHealthCheckHttpEndpoint());
    getTask().updateStatus(PHASE, "Updated process '" + description.getServerGroupName() + "'");
  }

  // VisibleForTesting
  @Nullable
  static Integer convertToMb(String field, @Nullable String size) {
    if (size == null) {
      return null;
    } else if (StringUtils.isNumeric(size)) {
      return Integer.parseInt(size);
    } else if (size.toLowerCase().endsWith("g")) {
      String value = size.substring(0, size.length() - 1);
      if (StringUtils.isNumeric(value))
        return Integer.parseInt(value) * 1024;
    } else if (size.toLowerCase().endsWith("m")) {
      String value = size.substring(0, size.length() - 1);
      if (StringUtils.isNumeric(value))
        return Integer.parseInt(value);
    }

    throw new IllegalArgumentException("Invalid size for application " + field + " = '" + size + "'");
  }
}
