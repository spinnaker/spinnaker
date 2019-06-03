/*
 * Copyright 2017 Google, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package com.netflix.spinnaker.halyard.deploy.spinnaker.v1.service.distributed;

import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperations;
import com.netflix.spinnaker.halyard.config.model.v1.node.Account;
import com.netflix.spinnaker.halyard.config.model.v1.node.Provider;
import com.netflix.spinnaker.halyard.core.error.v1.HalException;
import com.netflix.spinnaker.halyard.core.problem.v1.Problem;
import com.netflix.spinnaker.halyard.deploy.deployment.v1.AccountDeploymentDetails;
import com.netflix.spinnaker.halyard.deploy.services.v1.GenerateService;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.RunningServiceDetails;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.SpinnakerRuntimeSettings;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.service.ConfigSource;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.service.HasServiceSettings;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.service.ServiceSettings;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.service.SpinnakerMonitoringDaemonService;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.service.SpinnakerService;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * This interface represents the cloud-environments specific information/operations required to
 * install a service.
 *
 * @param <T> is the type of the service interface being deployed, e.g
 *     ClouddriverService.Clouddriver.
 * @param <A> is the type of an account in this cloud provider.
 */
public interface DistributedService<T, A extends Account> extends HasServiceSettings<T> {
  String getSpinnakerStagingPath(String deploymentName);

  Map<String, Object> getLoadBalancerDescription(
      AccountDeploymentDetails<A> details, SpinnakerRuntimeSettings runtimeSettings);

  Map<String, Object> getServerGroupDescription(
      AccountDeploymentDetails<A> details,
      SpinnakerRuntimeSettings runtimeSettings,
      List<ConfigSource> configSources);

  List<ConfigSource> stageProfiles(
      AccountDeploymentDetails<A> details,
      GenerateService.ResolvedConfiguration resolvedConfiguration);

  void ensureRunning(
      AccountDeploymentDetails<A> details,
      GenerateService.ResolvedConfiguration resolvedConfiguration,
      List<ConfigSource> configSources,
      boolean recreate);

  List<String> getHealthProviders();

  Map<String, List<String>> getAvailabilityZones(ServiceSettings settings);

  Provider.ProviderType getProviderType();

  RunningServiceDetails getRunningServiceDetails(
      AccountDeploymentDetails<A> details, SpinnakerRuntimeSettings runtimeSettings);

  String getServiceName();

  String getCanonicalName();

  SpinnakerMonitoringDaemonService getMonitoringDaemonService();

  <S> S connectToService(
      AccountDeploymentDetails<A> details,
      SpinnakerRuntimeSettings runtimeSettings,
      SpinnakerService<S> sidecar);

  <S> S connectToInstance(
      AccountDeploymentDetails<A> details,
      SpinnakerRuntimeSettings runtimeSettings,
      SpinnakerService<S> sidecar,
      String instanceId);

  String connectCommand(
      AccountDeploymentDetails<A> details, SpinnakerRuntimeSettings runtimeSettings);

  void deleteVersion(
      AccountDeploymentDetails<A> details, ServiceSettings settings, Integer version);

  void resizeVersion(
      AccountDeploymentDetails<A> details, ServiceSettings settings, int version, int targetSize);

  boolean isRequiredToBootstrap();

  DeployPriority getDeployPriority();

  SpinnakerService<T> getService();

  default boolean isStateful() {
    return false;
  }

  default T connectToPrimaryService(
      AccountDeploymentDetails<A> details, SpinnakerRuntimeSettings runtimeSettings) {
    return connectToService(details, runtimeSettings, getService());
  }

  default List<SidecarService> getSidecars(SpinnakerRuntimeSettings runtimeSettings) {
    SpinnakerMonitoringDaemonService monitoringService = getMonitoringDaemonService();
    ServiceSettings monitoringSettings = runtimeSettings.getServiceSettings(monitoringService);
    ServiceSettings thisSettings = runtimeSettings.getServiceSettings(getService());

    List<SidecarService> result = new ArrayList<>();
    if (monitoringSettings.getEnabled() && thisSettings.getMonitored()) {
      result.add(monitoringService);
    }

    return result;
  }

  default String getVersionedName(int version) {
    return String.format("%s-v%03d", getServiceName(), version);
  }

  default String getRegion(ServiceSettings settings) {
    return settings.getLocation();
  }

  default Map<String, Object> buildRollbackPipeline(
      AccountDeploymentDetails<A> details, SpinnakerRuntimeSettings runtimeSettings) {
    RunningServiceDetails serviceDetails = getRunningServiceDetails(details, runtimeSettings);
    Integer version = serviceDetails.getLatestEnabledVersion();
    if (version == null) {
      throw new HalException(
          Problem.Severity.FATAL,
          "There are no enabled server groups for service "
              + getServiceName()
              + " nothing to rollback to.");
    }

    int targetSize = serviceDetails.getInstances().get(version).size();
    targetSize = targetSize == 0 ? 1 : targetSize;

    ServiceSettings settings = runtimeSettings.getServiceSettings(getService());
    Map<String, Object> baseDescription = new HashMap<>();
    baseDescription.put("cloudProvider", getProviderType().getId());
    baseDescription.put("cloudProviderType", getProviderType().getId());
    baseDescription.put("region", getRegion(settings));
    baseDescription.put("credentials", details.getAccount().getName());
    baseDescription.put("cluster", getServiceName());
    baseDescription.put("name", "rollback");

    Map<String, Object> capacity = new HashMap<>();
    capacity.put("desired", targetSize);

    Map<String, Object> resizeDescription = new HashMap<>();
    resizeDescription.putAll(baseDescription);
    String resizeId = "resize";
    resizeDescription.put("name", "Resize old " + getServiceName() + " to prior size");
    resizeDescription.put("capacity", capacity);
    resizeDescription.put("type", "resizeServerGroup");
    resizeDescription.put("refId", resizeId);
    resizeDescription.put("target", "ancestor_asg_dynamic");
    resizeDescription.put("action", "scale_exact");
    resizeDescription.put("requisiteStageRefIds", Collections.emptyList());

    Map<String, Object> enableDescription = new HashMap<>();
    enableDescription.putAll(baseDescription);
    String enableId = "enable";
    enableDescription.put("name", "Enable old " + getServiceName());
    enableDescription.put("type", "enableServerGroup");
    enableDescription.put("refId", enableId);
    enableDescription.put("target", "ancestor_asg_dynamic");
    enableDescription.put("requisiteStageRefIds", Collections.singletonList(resizeId));

    // This is a destroy, rather than a disable because the typical flow will look like this:
    //
    // 1. You deploy a new version/config
    // 2. Something is wrong, so you rollback.
    // 3. Fixing the bad server group requires redeploying.
    //
    // Since you can't fix the newest destroyed server group in place, and you won't (at least I
    // can't imagine why)
    // want to reenable that server group, there is no point it keeping it around. There's an
    // argument
    // to be made for keeping it around to debug, but that's far from what the average halyard user
    // will want
    // to do.
    Map<String, Object> destroyDescription = new HashMap<>();
    String destroyId = "destroy";
    destroyDescription.putAll(baseDescription);
    destroyDescription.put("name", "Destroy current " + getServiceName());
    destroyDescription.put("type", "destroyServerGroup");
    destroyDescription.put("refId", destroyId);
    destroyDescription.put("requisiteStageRefIds", Collections.singletonList(enableId));
    destroyDescription.put("target", "current_asg_dynamic");

    List<Map<String, Object>> stages = new ArrayList<>();
    stages.add(resizeDescription);
    stages.add(enableDescription);
    stages.add(destroyDescription);

    Map<String, Object> pipeline = new HashMap<>();
    pipeline.put("stages", stages);
    pipeline.put("application", "spin");
    pipeline.put("name", "Rollback " + getServiceName());
    pipeline.put("description", "Auto-generated by Halyard");

    return pipeline;
  }

  default Map<String, Object> buildDeployServerGroupPipeline(
      AccountDeploymentDetails<A> details,
      SpinnakerRuntimeSettings runtimeSettings,
      List<ConfigSource> configSources,
      Integer maxRemaining,
      boolean scaleDown) {
    String accountName = details.getAccount().getName();
    String region = runtimeSettings.getServiceSettings(getService()).getLocation();
    Map<String, Object> deployDescription =
        getServerGroupDescription(details, runtimeSettings, configSources);
    deployDescription.put("name", "deploy");
    Map<String, String> source = new HashMap<>();
    RunningServiceDetails runningServiceDetails =
        getRunningServiceDetails(details, runtimeSettings);
    if (runningServiceDetails.getLatestEnabledVersion() == null) {
      throw new HalException(
          Problem.Severity.FATAL, "No prior server group to clone for " + getServiceName());
    }
    source.put("account", accountName);
    source.put("credentials", accountName);
    source.put(
        "serverGroupName", getVersionedName(runningServiceDetails.getLatestEnabledVersion()));
    source.put("region", region);
    source.put("namespace", region);
    deployDescription.put("source", source);
    deployDescription.put("interestingHealthProviders", getHealthProviders());
    deployDescription.put("type", AtomicOperations.CLONE_SERVER_GROUP);
    deployDescription.put("cloudProvider", getProviderType().getId());
    deployDescription.put("refId", "deployredblack");
    deployDescription.put("region", getRegion(runtimeSettings.getServiceSettings(getService())));
    deployDescription.put("strategy", "redblack");
    if (maxRemaining != null) {
      deployDescription.put("maxRemainingAsgs", maxRemaining + "");
    }

    deployDescription.put("scaleDown", scaleDown + "");
    if (scaleDown) {
      deployDescription.put("allowShrinkDownActive", "true");
    }

    List<Map<String, Object>> stages = new ArrayList<>();
    stages.add(deployDescription);

    Map<String, Object> pipeline = new HashMap<>();
    pipeline.put("stages", stages);
    pipeline.put("application", "spin");
    pipeline.put("name", "Deploy/Upgrade " + getServiceName());
    pipeline.put("description", "Auto-generated by Halyard");
    return pipeline;
  }

  default Map<String, Object> buildUpsertLoadBalancerTask(
      AccountDeploymentDetails<A> details, SpinnakerRuntimeSettings runtimeSettings) {
    Map<String, Object> upsertDescription = getLoadBalancerDescription(details, runtimeSettings);
    upsertDescription.put("name", "upsert");
    upsertDescription.put("type", AtomicOperations.UPSERT_LOAD_BALANCER);
    upsertDescription.put("cloudProvider", getProviderType().getId());
    upsertDescription.put("refId", "upsertlb");
    upsertDescription.put("application", "spin");
    upsertDescription.put(
        "availabilityZones",
        getAvailabilityZones(runtimeSettings.getServiceSettings(getService())));

    List<Map<String, Object>> job = new ArrayList<>();
    job.add(upsertDescription);

    Map<String, Object> task = new HashMap<>();
    task.put("job", job);
    task.put("application", "spin");
    task.put("name", "Upsert LB of " + getServiceName());
    task.put("description", "Auto-generated by Halyard");
    return task;
  }

  // Used to ensure dependencies are deployed first. The higher the priority, the sooner the service
  // is deployed.
  class DeployPriority {
    final Integer priority;

    public DeployPriority(Integer priority) {
      this.priority = priority;
    }

    public int compareTo(DeployPriority other) {
      return this.priority.compareTo(other.priority);
    }
  }
}
