/*
 * Copyright 2016 Google, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.clouddriver.google.deploy.ops

import com.google.api.client.repackaged.com.google.common.annotations.VisibleForTesting
import com.google.api.services.compute.model.FixedOrPercent
import com.google.api.services.compute.model.InstanceGroupManagerAutoHealingPolicy
import com.google.api.services.compute.model.InstanceGroupManagersSetAutoHealingRequest
import com.google.api.services.compute.model.RegionInstanceGroupManagersSetAutoHealingRequest
import com.netflix.spinnaker.cats.cache.Cache
import com.netflix.spinnaker.clouddriver.data.task.Task
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository
import com.netflix.spinnaker.clouddriver.google.deploy.description.UpsertGoogleAutoscalingPolicyDescription
import com.netflix.spinnaker.clouddriver.google.deploy.GCEUtil
import com.netflix.spinnaker.clouddriver.google.model.GoogleAutoHealingPolicy
import com.netflix.spinnaker.clouddriver.google.model.GoogleAutoscalingPolicy
import com.netflix.spinnaker.clouddriver.google.provider.view.GoogleClusterProvider
import org.springframework.beans.factory.annotation.Autowired

class UpsertGoogleAutoscalingPolicyAtomicOperation extends GoogleAtomicOperation<Void> {
  private static final String BASE_PHASE = "UPSERT_SCALING_POLICY"

  private static Task getTask() {
    TaskRepository.threadLocalTask.get()
  }

  @Autowired
  private GoogleClusterProvider googleClusterProvider

  @Autowired
  Cache cacheView

  private final UpsertGoogleAutoscalingPolicyDescription description

  UpsertGoogleAutoscalingPolicyAtomicOperation(UpsertGoogleAutoscalingPolicyDescription description) {
    this.description = description
  }

  /**
   * Autoscaling policy:
   * curl -X POST -H "Content-Type: application/json" -d '[ { "upsertScalingPolicy": { "serverGroupName": "autoscale-regional", "region": "us-central1", "credentials": "my-google-account", "autoscalingPolicy": { "maxNumReplicas": 2, "minNumReplicas": 1, "coolDownPeriodSec" : 30, "cpuUtilization": { "utilizationTarget": 0.7 }}}} ]' localhost:7002/gce/ops
   * curl -X POST -H "Content-Type: application/json" -d '[ { "upsertScalingPolicy": { "serverGroupName": "autoscale-regional", "region": "us-central1", "credentials": "my-google-account", "autoscalingPolicy": { "cpuUtilization": { "utilizationTarget": 0.5 }}}} ]' localhost:7002/gce/ops
   * curl -X POST -H "Content-Type: application/json" -d '[ { "upsertScalingPolicy": { "serverGroupName": "autoscale-regional", "region": "us-central1", "credentials": "my-google-account", "autoscalingPolicy": { "maxNumReplicas": 2, "loadBalancingUtilization": { "utilizationTarget": 0.7 }, "cpuUtilization": {}}}} ]' localhost:7002/gce/ops
   * curl -X POST -H "Content-Type: application/json" -d '[ { "upsertScalingPolicy": { "serverGroupName": "autoscale-regional", "region": "us-central1", "credentials": "my-google-account", "autoscalingPolicy": { "maxNumReplicas": 3, "minNumReplicas": 2 , "coolDownPeriodSec" : 60 }}} ]' localhost:7002/gce/ops
   * curl -X POST -H "Content-Type: application/json" -d '[ { "upsertScalingPolicy": { "serverGroupName": "autoscale-regional", "region": "us-central1", "credentials": "my-google-account", "autoscalingPolicy": { "coolDownPeriodSec": 35, "cpuUtilization": { "utilizationTarget": 0.9 }, "loadBalancingUtilization": { "utilizationTarget" : 0.6 }, "customMetricUtilizations" : [ { "metric": "myMetric", "utilizationTarget": 0.9, "utilizationTargetType" : "DELTA_PER_SECOND" } ] }}} ]' localhost:7002/gce/ops
   * curl -X POST -H "Content-Type: application/json" -d '[ { "upsertScalingPolicy": { "serverGroupName": "autoscale-regional", "region": "us-central1", "credentials": "my-google-account", "autoscalingPolicy": { "maxNumReplicas": 2, "minNumReplicas": 1, "coolDownPeriodSec": 30, "cpuUtilization": {}, "loadBalancingUtilization": {}, "customMetricUtilizations" : [] }}} ]' localhost:7002/gce/ops
   *
   * Autohealing policy:
   * curl -X POST -H "Content-Type: application/json" -d '[ { "upsertScalingPolicy": { "serverGroupName": "autoheal-regional", "region": "us-central1", "credentials": "my-google-account", "autoHealingPolicy": {"initialDelaySec": 30, "healthCheck": "hc", "maxUnavailable": { "fixed": 3 }}}} ]' localhost:7002/gce/ops
   * curl -X POST -H "Content-Type: application/json" -d '[ { "upsertScalingPolicy": { "serverGroupName": "autoheal-regional", "region": "us-central1", "credentials": "my-google-account", "autoHealingPolicy": {"initialDelaySec": 50}}} ]' localhost:7002/gce/ops
   */

  @Override
  Void operate(List priorOutputs) {
    task.updateStatus BASE_PHASE, "Initializing upsert of scaling policy for $description.serverGroupName..."

    def serverGroupName = description.serverGroupName
    def credentials = description.credentials
    def project = credentials.project
    def compute = credentials.compute
    def accountName = description.accountName
    def region = description.region
    def serverGroup = GCEUtil.queryServerGroup(googleClusterProvider, accountName, region, serverGroupName)
    def isRegional = serverGroup.regional
    def zone = serverGroup.zone

    if (description.autoscalingPolicy) {
      def ancestorAutoscalingPolicyDescription =
        GCEUtil.buildAutoscalingPolicyDescriptionFromAutoscalingPolicy(serverGroup.autoscalingPolicy)
      if (ancestorAutoscalingPolicyDescription) {
        task.updateStatus BASE_PHASE, "Updating autoscaler for $serverGroupName..."

        def autoscaler = GCEUtil.buildAutoscaler(serverGroupName,
                                                 serverGroup.selfLink,
                                                 copyAndOverrideAncestorAutoscalingPolicy(ancestorAutoscalingPolicyDescription,
                                                                                          description.autoscalingPolicy))

        if (isRegional) {
          timeExecute(
              compute.regionAutoscalers().update(project, region, autoscaler),
              "compute.regionAutoscalers.update",
              TAG_SCOPE, SCOPE_REGIONAL, TAG_REGION, region)
        } else {
          timeExecute(
              compute.autoscalers().update(project, zone, autoscaler),
              "compute.autoscalers.update",
              TAG_SCOPE, SCOPE_ZONAL, TAG_ZONE, zone)
        }
      } else {
        task.updateStatus BASE_PHASE, "Creating new autoscaler for $serverGroupName..."

        def autoscaler = GCEUtil.buildAutoscaler(serverGroupName,
                                                 serverGroup.selfLink,
                                                 normalizeNewAutoscalingPolicy(description.autoscalingPolicy))

        if (isRegional) {
          timeExecute(
              compute.regionAutoscalers().insert(project, region, autoscaler),
              "compute.regionAutoscalers.insert",
              TAG_SCOPE, SCOPE_REGIONAL, TAG_REGION, region)
        } else {
          timeExecute(
              compute.autoscalers().insert(project, zone, autoscaler),
              "compute.autoscalers.insert",
              TAG_SCOPE, SCOPE_ZONAL, TAG_ZONE, zone)
        }
      }
    }

    if (description.autoHealingPolicy) {
      def ancestorAutoHealingPolicyDescription =
        GCEUtil.buildAutoHealingPolicyDescriptionFromAutoHealingPolicy(serverGroup.autoHealingPolicy)

      def regionalRequest = { List<InstanceGroupManagerAutoHealingPolicy> policy ->
        def request = new RegionInstanceGroupManagersSetAutoHealingRequest().setAutoHealingPolicies(policy)
        timeExecute(
          compute.regionInstanceGroupManagers().setAutoHealingPolicies(project, region, serverGroupName, request),
          "compute.regionInstanceGroupManagers.setAutoHealingPolicies",
          TAG_SCOPE, SCOPE_REGIONAL, TAG_REGION, region)
      }

      def zonalRequest = { List<InstanceGroupManagerAutoHealingPolicy> policy ->
        def request = new InstanceGroupManagersSetAutoHealingRequest().setAutoHealingPolicies(policy)
        timeExecute(
          compute.instanceGroupManagers().setAutoHealingPolicies(project, zone, serverGroupName, request),
          "compute.instanceGroupManagers.setAutoHealingPolicies",
          TAG_SCOPE, SCOPE_ZONAL, TAG_ZONE, zone)
      }

      if (ancestorAutoHealingPolicyDescription) {
        task.updateStatus BASE_PHASE, "Updating autoHealing policy for $serverGroupName..."

        def autoHealingPolicy =
          buildAutoHealingPolicyFromAutoHealingPolicyDescription(
            copyAndOverrideAncestorAutoHealingPolicy(ancestorAutoHealingPolicyDescription, description.autoHealingPolicy))
        isRegional ? regionalRequest(autoHealingPolicy) : zonalRequest(autoHealingPolicy)

      } else {
        task.updateStatus BASE_PHASE, "Creating new autoHealing policy for $serverGroupName..."

        def autoHealingPolicy =
          buildAutoHealingPolicyFromAutoHealingPolicyDescription(
            normalizeNewAutoHealingPolicy(description.autoHealingPolicy))
        isRegional ? regionalRequest(autoHealingPolicy) : zonalRequest(autoHealingPolicy)
      }
    }

    return null
  }

  private static GoogleAutoscalingPolicy copyAndOverrideAncestorAutoscalingPolicy(GoogleAutoscalingPolicy ancestor,
                                                                                  GoogleAutoscalingPolicy update) {
    GoogleAutoscalingPolicy newDescription = ancestor.clone()

    if (!update) {
      return newDescription
    }

    // Deletes existing customMetricUtilizations if passed an empty array.
    ["minNumReplicas", "maxNumReplicas", "coolDownPeriodSec", "customMetricUtilizations"].each {
      if (update[it] != null) {
        newDescription[it] = update[it]
      }
    }

    // Deletes existing cpuUtilization or loadBalancingUtilization if passed an empty object.
    ["cpuUtilization", "loadBalancingUtilization"].each {
      if (update[it] != null) {
        if (update[it].utilizationTarget != null) {
          newDescription[it] = update[it]
        } else {
          newDescription[it] = null
        }
      }
    }

    return newDescription
  }

  // Forces the behavior of this operation to be consistent: passing an empty `cpuUtilization` or
  // `loadBalancingUtilization` object always results in a policy without these properties.
  private static GoogleAutoscalingPolicy normalizeNewAutoscalingPolicy(GoogleAutoscalingPolicy newPolicy) {
    ["cpuUtilization", "loadBalancingUtilization"].each {
      if (newPolicy[it]?.utilizationTarget == null) {
        newPolicy[it] = null
      }
    }

    return newPolicy
  }

  @VisibleForTesting
  static GoogleAutoHealingPolicy copyAndOverrideAncestorAutoHealingPolicy(GoogleAutoHealingPolicy ancestor,
                                                                          GoogleAutoHealingPolicy update) {
    GoogleAutoHealingPolicy newDescription = ancestor.clone()

    if (!update) {
      return newDescription
    }

    ["healthCheck", "initialDelaySec"].each {
      if (update[it] != null) {
        newDescription[it] = update[it]
      }
    }

    // Deletes existing maxUnavailable if passed an empty object.
    if (update.maxUnavailable != null) {
      if (update.maxUnavailable.fixed != null || update.maxUnavailable.percent != null) {
        newDescription.maxUnavailable = update.maxUnavailable
      } else {
        newDescription.maxUnavailable = null
      }
    }

    return newDescription
  }

  // Forces the behavior of this operation to be consistent: passing an empty `maxUnavailable` object
  // always results in a policy with no `maxUnavailable` property.
  private static GoogleAutoHealingPolicy normalizeNewAutoHealingPolicy(GoogleAutoHealingPolicy newPolicy) {
    if (newPolicy.maxUnavailable?.fixed == null && newPolicy.maxUnavailable?.percent == null) {
      newPolicy.maxUnavailable = null
    }

    return newPolicy
  }

  private buildAutoHealingPolicyFromAutoHealingPolicyDescription(GoogleAutoHealingPolicy autoHealingPolicyDescription) {
    List<InstanceGroupManagerAutoHealingPolicy> autoHealingPolicy = autoHealingPolicyDescription?.healthCheck
      ? [new InstanceGroupManagerAutoHealingPolicy(
          healthCheck: GCEUtil.queryHealthCheck(
            description.credentials.project,
            description.accountName,
            autoHealingPolicyDescription.healthCheck,
            description.credentials.compute,
            cacheView,
            task,
            BASE_PHASE,
            this).selfLink,
          initialDelaySec: autoHealingPolicyDescription.initialDelaySec)]
      : null

    if (autoHealingPolicy && autoHealingPolicyDescription.maxUnavailable) {
      def maxUnavailable = new FixedOrPercent(fixed: autoHealingPolicyDescription.maxUnavailable.fixed as Integer,
                                              percent: autoHealingPolicyDescription.maxUnavailable.percent as Integer)

      autoHealingPolicy[0].setMaxUnavailable(maxUnavailable)
    }

    return autoHealingPolicy
  }
}
