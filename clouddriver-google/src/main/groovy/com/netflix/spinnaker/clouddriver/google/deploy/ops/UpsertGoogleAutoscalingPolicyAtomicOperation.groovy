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

import com.fasterxml.jackson.databind.ObjectMapper
import com.google.api.services.compute.Compute
import com.google.api.services.compute.model.*
import com.google.common.annotations.VisibleForTesting
import com.netflix.spinnaker.cats.cache.Cache
import com.netflix.spinnaker.clouddriver.data.task.Task
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository
import com.netflix.spinnaker.clouddriver.google.deploy.GCEUtil
import com.netflix.spinnaker.clouddriver.google.deploy.description.UpsertGoogleAutoscalingPolicyDescription
import com.netflix.spinnaker.clouddriver.google.model.GoogleAutoHealingPolicy
import com.netflix.spinnaker.clouddriver.google.model.GoogleAutoscalingPolicy
import com.netflix.spinnaker.clouddriver.google.model.GoogleServerGroup
import com.netflix.spinnaker.clouddriver.google.model.callbacks.Utils
import com.netflix.spinnaker.clouddriver.google.provider.view.GoogleClusterProvider
import com.netflix.spinnaker.clouddriver.google.security.GoogleNamedAccountCredentials
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperationsRegistry
import com.netflix.spinnaker.clouddriver.orchestration.OrchestrationProcessor
import com.netflix.spinnaker.clouddriver.security.ProviderVersion
import org.springframework.beans.factory.annotation.Autowired

class UpsertGoogleAutoscalingPolicyAtomicOperation extends GoogleAtomicOperation<Void> {
  private static final String BASE_PHASE = "UPSERT_SCALING_POLICY"

  private static Task getTask() {
    TaskRepository.threadLocalTask.get()
  }

  @Autowired
  private GoogleClusterProvider googleClusterProvider

  @Autowired
  AtomicOperationsRegistry atomicOperationsRegistry

  @Autowired
  OrchestrationProcessor orchestrationProcessor

  @Autowired
  Cache cacheView

  @Autowired
  ObjectMapper objectMapper

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

    def autoscaler = null
    if (description.autoscalingPolicy) {
      def ancestorAutoscalingPolicyDescription =
        GCEUtil.buildAutoscalingPolicyDescriptionFromAutoscalingPolicy(serverGroup.autoscalingPolicy)
      if (ancestorAutoscalingPolicyDescription) {
        task.updateStatus BASE_PHASE, "Updating autoscaler for $serverGroupName..."

        autoscaler = GCEUtil.buildAutoscaler(serverGroupName,
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

        autoscaler = GCEUtil.buildAutoscaler(serverGroupName,
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
            copyAndOverrideAncestorAutoHealingPolicy(ancestorAutoHealingPolicyDescription, description.autoHealingPolicy),
            project, compute)
        isRegional ? regionalRequest(autoHealingPolicy) : zonalRequest(autoHealingPolicy)

      } else {
        task.updateStatus BASE_PHASE, "Creating new autoHealing policy for $serverGroupName..."

        def autoHealingPolicy =
          buildAutoHealingPolicyFromAutoHealingPolicyDescription(
            normalizeNewAutoHealingPolicy(description.autoHealingPolicy),
            project, compute)
        isRegional ? regionalRequest(autoHealingPolicy) : zonalRequest(autoHealingPolicy)
      }
    }

    // TODO(jacobkiefer): Update metadata for autoHealingPolicy when 'mode' support lands.
    // NOTE: This block is here intentionally, we should wait until all the modifications are done before
    // updating the instance template metadata.
    if (description.writeMetadata == null || description.writeMetadata) {
      if (isRegional) {
        updatePolicyMetadata(compute,
          credentials,
          project,
          GCEUtil.buildRegionalServerGroupUrl(project, region, serverGroupName),
          autoscaler)
      } else {
        updatePolicyMetadata(compute,
          credentials,
          project,
          GCEUtil.buildZonalServerGroupUrl(project, zone, serverGroupName),
          autoscaler)
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
    ["minNumReplicas", "maxNumReplicas", "coolDownPeriodSec", "customMetricUtilizations", "mode"].each {
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

    ["healthCheck", "initialDelaySec", "healthCheckKind"].each {
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

  private buildAutoHealingPolicyFromAutoHealingPolicyDescription(GoogleAutoHealingPolicy autoHealingPolicyDescription, String project, Compute compute) {
    def autoHealingHealthCheck = GCEUtil.queryHealthCheck(project, description.accountName, autoHealingPolicyDescription.healthCheck, autoHealingPolicyDescription.healthCheckKind, compute, cacheView, task, BASE_PHASE, this)

    List<InstanceGroupManagerAutoHealingPolicy> autoHealingPolicy = autoHealingPolicyDescription?.healthCheck
      ? [new InstanceGroupManagerAutoHealingPolicy(
          healthCheck: autoHealingHealthCheck.selfLink,
          initialDelaySec: autoHealingPolicyDescription.initialDelaySec)]
      : null

    if (autoHealingPolicy && autoHealingPolicyDescription.maxUnavailable) {
      def maxUnavailable = new FixedOrPercent(fixed: autoHealingPolicyDescription.maxUnavailable.fixed as Integer,
                                              percent: autoHealingPolicyDescription.maxUnavailable.percent as Integer)

      autoHealingPolicy[0].setMaxUnavailable(maxUnavailable)
    }

    return autoHealingPolicy
  }

  void updatePolicyMetadata(Compute compute,
                            GoogleNamedAccountCredentials credentials,
                            String project,
                            String groupUrl,
                            autoscaler) {
    def groupName = Utils.getLocalName(groupUrl)
    def groupRegion = Utils.getRegionFromGroupUrl(groupUrl)

    String templateUrl = null
    switch (Utils.determineServerGroupType(groupUrl)) {
      case GoogleServerGroup.ServerGroupType.REGIONAL:
        templateUrl = timeExecute(
          compute.regionInstanceGroupManagers().get(project, groupRegion, groupName),
          "compute.regionInstanceGroupManagers.get",
          TAG_SCOPE, SCOPE_REGIONAL, TAG_REGION, groupRegion)
          .getInstanceTemplate()
        break
      case GoogleServerGroup.ServerGroupType.ZONAL:
        def groupZone = Utils.getZoneFromGroupUrl(groupUrl)
        templateUrl = timeExecute(
          compute.instanceGroupManagers().get(project, groupZone, groupName),
          "compute.instanceGroupManagers.get",
          TAG_SCOPE, SCOPE_ZONAL, TAG_ZONE, groupZone)
          .getInstanceTemplate()
        break
      default:
        throw new IllegalStateException("Server group referenced by ${groupUrl} has illegal type.")
        break
    }

    InstanceTemplate template = timeExecute(
      compute.instanceTemplates().get(project, Utils.getLocalName(templateUrl)),
      "compute.instancesTemplates.get",
      TAG_SCOPE, SCOPE_GLOBAL)
    def instanceDescription = GCEUtil.buildInstanceDescriptionFromTemplate(project, template)

    def templateOpMap = [
      image              : instanceDescription.image,
      instanceType       : instanceDescription.instanceType,
      credentials        : credentials.getName(),
      disks              : instanceDescription.disks,
      instanceMetadata   : instanceDescription.instanceMetadata,
      tags               : instanceDescription.tags,
      network            : instanceDescription.network,
      subnet             : instanceDescription.subnet,
      serviceAccountEmail: instanceDescription.serviceAccountEmail,
      authScopes         : instanceDescription.authScopes,
      preemptible        : instanceDescription.preemptible,
      automaticRestart   : instanceDescription.automaticRestart,
      onHostMaintenance  : instanceDescription.onHostMaintenance,
      region             : groupRegion,
      serverGroupName    : groupName
    ]

    if (instanceDescription.minCpuPlatform) {
      templateOpMap.minCpuPlatform = instanceDescription.minCpuPlatform
    }

    def instanceMetadata = templateOpMap?.instanceMetadata
    if (instanceMetadata && autoscaler) {
      instanceMetadata.(GoogleServerGroup.View.AUTOSCALING_POLICY) = objectMapper.writeValueAsString(autoscaler)
    } else if (autoscaler) {
      templateOpMap.instanceMetadata = [
        (GoogleServerGroup.View.AUTOSCALING_POLICY): objectMapper.writeValueAsString(autoscaler)
      ]
    }

    if (templateOpMap.instanceMetadata) {
      def converter = atomicOperationsRegistry.getAtomicOperationConverter('modifyGoogleServerGroupInstanceTemplateDescription', 'gce', ProviderVersion.v1)
      AtomicOperation templateOp = converter.convertOperation(templateOpMap)
      orchestrationProcessor.process([templateOp], UUID.randomUUID().toString())
    }
  }
}
