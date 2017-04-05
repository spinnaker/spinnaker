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

package com.netflix.spinnaker.halyard.deploy.spinnaker.v1.service;

import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperations;
import com.netflix.spinnaker.halyard.config.model.v1.node.Account;
import com.netflix.spinnaker.halyard.config.model.v1.node.Provider;
import com.netflix.spinnaker.halyard.core.error.v1.HalException;
import com.netflix.spinnaker.halyard.core.problem.v1.Problem;
import com.netflix.spinnaker.halyard.deploy.deployment.v1.AccountDeploymentDetails;
import com.netflix.spinnaker.halyard.deploy.services.v1.GenerateService;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.RunningServiceDetails;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.SpinnakerRuntimeSettings;

import java.util.*;

/**
 * This interface represents the cloud-environments specific information/operations required to install a service.
 * @param <T> is the type of the service interface being deployed, e.g ClouddriverService.Clouddriver.
 * @param <A> is the type of an account in this cloud provider.
 */
public interface DistributedService<T, A extends Account> extends HasServiceSettings<T> {
  String getSpinnakerStagingPath();

  Map<String, Object> getLoadBalancerDescription(AccountDeploymentDetails<A> details,
      SpinnakerRuntimeSettings runtimeSettings);
  Map<String, Object> getServerGroupDescription(AccountDeploymentDetails<A> details,
      SpinnakerRuntimeSettings runtimeSettings,
      List<ConfigSource> configSources);
  List<ConfigSource> stageProfiles(AccountDeploymentDetails<A> details,
      GenerateService.ResolvedConfiguration resolvedConfiguration);
  void ensureRunning(AccountDeploymentDetails<A> details,
      GenerateService.ResolvedConfiguration resolvedConfiguration,
      List<ConfigSource> configSources,
      boolean recreate);


  List<String> getHealthProviders();
  Map<String, List<String>> getAvailabilityZones();
  String getRegion();
  Provider.ProviderType getProviderType();
  RunningServiceDetails getRunningServiceDetails(AccountDeploymentDetails<A> details);
  String getName();
  SpinnakerMonitoringDaemonService getMonitoringDaemonService();
  T connect(AccountDeploymentDetails<A> details, SpinnakerRuntimeSettings runtimeSettings);
  String connectCommand(AccountDeploymentDetails<A> details, SpinnakerRuntimeSettings runtimeSettings);
  void deleteVersion(AccountDeploymentDetails<A> details, Integer version);
  boolean isRequiredToBootstrap();
  DeployPriority getDeployPriority();
  SpinnakerService<T> getService();

  default Integer getLatestEnabledServiceVersion(AccountDeploymentDetails<A> details) {
    RunningServiceDetails runningServiceDetails = getRunningServiceDetails(details);
    List<Integer> versions = new ArrayList<>(runningServiceDetails.getInstances().keySet());
    if (versions.isEmpty()) {
      return null;
    }

    versions.sort(Integer::compareTo);
    return versions.get(versions.size() - 1);
  }

  default Map<String, Object> buildRollbackPipeline(AccountDeploymentDetails<A> details) {
    Map<String, Object> baseDescription = new HashMap<>();
    baseDescription.put("cloudProvider", getProviderType().getId());
    baseDescription.put("cloudProviderType", getProviderType().getId());
    baseDescription.put("region", getRegion());
    baseDescription.put("credentials", details.getAccount().getName());
    baseDescription.put("cluster", getName());

    Map<String, Object> enableDescription = new HashMap<>();
    enableDescription.putAll(baseDescription);
    String enableId = "enable";
    enableDescription.put("name", "Enable old " + getName());
    enableDescription.put("type", "enableServerGroup");
    enableDescription.put("refId", enableId);
    enableDescription.put("target", "ancestor_asg_dynamic");
    enableDescription.put("requisiteStageRefIds", Collections.emptyList());

    // This is a destroy, rather than destroy because the typical flow will look like this:
    //
    // 1. You deploy a new version/config
    // 2. Something is wrong, so you rollback.
    // 3. Fixing the bad server group requires redeploying.
    //
    // Since you can't fix the newest destroyd server group in place, and you (at least I cant imagine why)
    // won't want to reenable that server group, there is no point it keeping it around. There's an argument
    // to be made for keeping it around to debug, but that's far from what the average halyard user will want
    // to do.
    Map<String, Object> destroyDescription = new HashMap<>();
    String destroyId = "destroy";
    destroyDescription.putAll(baseDescription);
    destroyDescription.put("name", "Destroy current " + getName());
    destroyDescription.put("type", "destroyServerGroup");
    destroyDescription.put("refId", destroyId);
    destroyDescription.put("requisiteStageRefIds", Collections.singletonList(enableId));
    destroyDescription.put("target", "current_asg_dynamic");

    List<Map<String, Object>> stages = new ArrayList<>();
    stages.add(enableDescription);
    stages.add(destroyDescription);

    Map<String, Object> pipeline = new HashMap<>();
    pipeline.put("stages", stages);
    pipeline.put("application", "spin");
    pipeline.put("name", "Rollback " + getName());
    pipeline.put("description", "Auto-generated by Halyard");

    return pipeline;
  }

  default Map<String, Object> buildDeployServerGroupPipeline(AccountDeploymentDetails<A> details,
      SpinnakerRuntimeSettings runtimeSettings,
      List<ConfigSource> configSources) {
    Map<String, Object> deployDescription  = getServerGroupDescription(details, runtimeSettings, configSources);
    deployDescription.put("interestingHealthProviders", getHealthProviders());
    deployDescription.put("type", AtomicOperations.CREATE_SERVER_GROUP);
    deployDescription.put("cloudProvider", getProviderType().getId());
    deployDescription.put("refId", "deployredblack");
    deployDescription.put("region", getRegion());
    deployDescription.put("strategy", "redblack");
    /* TODO(lwander)
    deployDescription.put("maxRemainingAsgs", maxRemaining + "");
    deployDescription.put("scaleDown", scaleDown + "");
    */

    List<Map<String, Object>> stages = new ArrayList<>();
    stages.add(deployDescription);

    Map<String, Object> pipeline = new HashMap<>();
    pipeline.put("stages", stages);
    pipeline.put("application", "spin");
    pipeline.put("name", "Deploy/Upgrade " + getName());
    pipeline.put("description", "Auto-generated by Halyard");
    return pipeline;
  }

  default Map<String, Object> buildUpsertLoadBalancerTask(AccountDeploymentDetails<A> details, SpinnakerRuntimeSettings runtimeSettings) {
    Map<String, Object> upsertDescription = getLoadBalancerDescription(details, runtimeSettings);
    upsertDescription.put("type", AtomicOperations.UPSERT_LOAD_BALANCER);
    upsertDescription.put("cloudProvider", getProviderType().getId());
    upsertDescription.put("refId", "upsertlb");
    upsertDescription.put("application", "spin");
    upsertDescription.put("availabilityZones", getAvailabilityZones());

    List<Map<String, Object>> job = new ArrayList<>();
    job.add(upsertDescription);

    Map<String, Object> task = new HashMap<>();
    task.put("job", job);
    task.put("application", "spin");
    task.put("name", "Upsert LB of " + getName());
    task.put("description", "Auto-generated by Halyard");
    return task;
  }

  // Used to ensure dependencies are deployed first. The higher the priority, the sooner the service is deployed.
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
