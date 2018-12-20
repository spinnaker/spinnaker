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

import com.netflix.spinnaker.clouddriver.cloudfoundry.client.CloudFoundryClient;
import com.netflix.spinnaker.clouddriver.cloudfoundry.client.model.RouteId;
import com.netflix.spinnaker.clouddriver.cloudfoundry.client.model.v3.ProcessStats;
import com.netflix.spinnaker.clouddriver.cloudfoundry.deploy.CloudFoundryServerGroupNameResolver;
import com.netflix.spinnaker.clouddriver.cloudfoundry.deploy.description.DeployCloudFoundryServerGroupDescription;
import com.netflix.spinnaker.clouddriver.cloudfoundry.model.CloudFoundryLoadBalancer;
import com.netflix.spinnaker.clouddriver.cloudfoundry.model.CloudFoundryServerGroup;
import com.netflix.spinnaker.clouddriver.cloudfoundry.model.CloudFoundrySpace;
import com.netflix.spinnaker.clouddriver.cloudfoundry.provider.view.CloudFoundryClusterProvider;
import com.netflix.spinnaker.clouddriver.data.task.Task;
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository;
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
import static java.util.stream.Collectors.toList;

@RequiredArgsConstructor
public class DeployCloudFoundryServerGroupAtomicOperation implements AtomicOperation<DeploymentResult> {
  private static final String PHASE = "DEPLOY";

  private final OperationPoller operationPoller;
  private final DeployCloudFoundryServerGroupDescription description;
  private final CloudFoundryClusterProvider clusterProvider;

  private static Task getTask() {
    return TaskRepository.threadLocalTask.get();
  }

  @Override
  public DeploymentResult operate(List priorOutputs) {
    getTask().updateStatus(PHASE, "Deploying '" + description.getApplication() + "'");

    CloudFoundryClient client = description.getClient();

    CloudFoundryServerGroupNameResolver serverGroupNameResolver = new CloudFoundryServerGroupNameResolver(description.getAccountName(),
      clusterProvider, description.getSpace());

    description.setServerGroupName(serverGroupNameResolver.resolveNextServerGroupName(description.getApplication(),
      description.getStack(), description.getDetail(), false));

    final CloudFoundryServerGroup serverGroup = createApplication(description);
    String packageId = buildPackage(serverGroup.getId(), description);

    buildDroplet(packageId, serverGroup.getId(), description);
    scaleApplication(serverGroup.getId(), description);
    if (description.getApplicationAttributes().getHealthCheckType() != null) {
      updateProcess(serverGroup.getId(), description);
    }

    client.getServiceInstances().createServiceBindingsByName(serverGroup, description.getApplicationAttributes().getServices());

    if (!mapRoutes(description.getApplicationAttributes().getRoutes(), description.getSpace(), serverGroup.getId())) {
      return deploymentResult();
    }

    client.getApplications().startApplication(serverGroup.getId());
    ProcessStats.State state = operationPoller.waitForOperation(
      () -> client.getApplications().getProcessState(serverGroup.getId()),
      inProgressState -> inProgressState == ProcessStats.State.RUNNING || inProgressState == ProcessStats.State.CRASHED,
      null, getTask(), description.getServerGroupName(), PHASE);

    if (state != ProcessStats.State.RUNNING) {
      getTask().updateStatus(PHASE, "Failed to start '" + description.getServerGroupName() + "' which instead " + describeProcessState(state));
      getTask().fail();
      return null;
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
    return deploymentResult;
  }

  private static CloudFoundryServerGroup createApplication(DeployCloudFoundryServerGroupDescription description) {
    CloudFoundryClient client = description.getClient();
    getTask().updateStatus(PHASE, "Creating Cloud Foundry application '" + description.getServerGroupName() + "'");

    CloudFoundryServerGroup serverGroup = client.getApplications().createApplication(description.getServerGroupName(),
      description.getSpace(), description.getApplicationAttributes().getBuildpacks(), description.getApplicationAttributes().getEnv());
    getTask().updateStatus(PHASE, "Created Cloud Foundry application '" + description.getServerGroupName() + "'");

    return serverGroup;
  }

  private String buildPackage(String serverGroupId, DeployCloudFoundryServerGroupDescription description) {
    final CloudFoundryClient client = description.getClient();
    getTask().updateStatus(PHASE, "Creating package for application '" + description.getServerGroupName() + "'");

    final String packageId = client.getApplications().createPackage(serverGroupId);

    File file = null;
    try {
      InputStream artifactInputStream = description.getArtifactCredentials().download(description.getArtifact());
      file = File.createTempFile(description.getArtifact().getReference(), null);
      FileOutputStream fileOutputStream = new FileOutputStream(file);
      IOUtils.copy(artifactInputStream, fileOutputStream);
      fileOutputStream.close();
      client.getApplications().uploadPackageBits(packageId, file);

      operationPoller.waitForOperation(
        () -> client.getApplications().packageUploadComplete(packageId),
        Function.identity(), null, getTask(), description.getServerGroupName(), PHASE);

      getTask().updateStatus(PHASE, "Completed creating package for application '" + description.getServerGroupName() + "'");
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    } finally {
      if (file != null) {
        file.delete();
      }
    }

    return packageId;
  }

  private void buildDroplet(String packageId, String serverGroupId, DeployCloudFoundryServerGroupDescription description) {
    final CloudFoundryClient client = description.getClient();
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
    final CloudFoundryClient client = description.getClient();
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

  // VisibleForTesting
  boolean mapRoutes(@Nullable List<String> routes, CloudFoundrySpace space, String serverGroupId) {
    if (routes == null) {
      getTask().updateStatus(PHASE, "No load balancers provided to create or update");
      return true;
    }

    getTask().updateStatus(PHASE, "Creating or updating load balancers");

    List<String> invalidRoutes = new ArrayList<>();

    CloudFoundryClient client = description.getClient();
    List<RouteId> routeIds = routes.stream()
      .map(routePath -> {
        RouteId routeId = client.getRoutes().toRouteId(routePath);
        if (routeId == null) {
          invalidRoutes.add(routePath);
        }
        return routeId;
      })
      .filter(Objects::nonNull)
      .collect(toList());

    for (String routePath : invalidRoutes) {
      getTask().updateStatus(PHASE, "Invalid format or domain for route '" + routePath + "'");
    }

    if (!invalidRoutes.isEmpty()) {
      getTask().fail();
      return false;
    }

    for (RouteId routeId : routeIds) {
      CloudFoundryLoadBalancer loadBalancer = client.getRoutes().createRoute(routeId, space.getId());
      if (loadBalancer == null) {
        getTask().updateStatus(PHASE, "Load balancer already exists in another organization and space");
        getTask().fail();
        return false;
      }
      getTask().updateStatus(PHASE, "Mapping load balancer '" + loadBalancer.getName() + "' to " + description.getServerGroupName());
      client.getApplications().mapRoute(serverGroupId, loadBalancer.getId());
    }

    return true;
  }
}
