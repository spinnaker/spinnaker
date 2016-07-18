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

import com.netflix.spinnaker.clouddriver.data.task.Task
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository
import com.netflix.spinnaker.clouddriver.google.deploy.description.UpsertGoogleAutoscalingPolicyDescription
import com.netflix.spinnaker.clouddriver.google.deploy.GCEUtil
import com.netflix.spinnaker.clouddriver.google.model.GoogleAutoscalingPolicy
import com.netflix.spinnaker.clouddriver.google.provider.view.GoogleClusterProvider
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation
import org.springframework.beans.factory.annotation.Autowired

class UpsertGoogleAutoscalingPolicyAtomicOperation implements AtomicOperation<Void> {
  private static final String BASE_PHASE = "UPSERT_SCALING_POLICY"

  private static Task getTask() {
    TaskRepository.threadLocalTask.get()
  }

  @Autowired
  private GoogleClusterProvider googleClusterProvider

  private final UpsertGoogleAutoscalingPolicyDescription description

  UpsertGoogleAutoscalingPolicyAtomicOperation(UpsertGoogleAutoscalingPolicyDescription description) {
    this.description = description
  }

  /**
   * curl -X POST -H "Content-Type: application/json" -d '[ { "upsertScalingPolicy": { "serverGroupName": "autoscale-regional", "region": "us-central1", "credentials": "my-google-account", "autoscalingPolicy": { "maxNumReplicas": 2, "minNumReplicas": 1, "coolDownPeriodSec" : 30, "cpuUtilization": { "utilizationTarget": 0.7 }}}} ]' localhost:7002/gce/ops
   * curl -X POST -H "Content-Type: application/json" -d '[ { "upsertScalingPolicy": { "serverGroupName": "autoscale-regional", "region": "us-central1", "credentials": "my-google-account", "autoscalingPolicy": { "cpuUtilization": { "utilizationTarget": 0.5 }}}} ]' localhost:7002/gce/ops
   * curl -X POST -H "Content-Type: application/json" -d '[ { "upsertScalingPolicy": { "serverGroupName": "autoscale-regional", "region": "us-central1", "credentials": "my-google-account", "autoscalingPolicy": { "maxNumReplicas": 2, "loadBalancingUtilization": { "utilizationTarget": 0.7 }, "cpuUtilization": {}}}} ]' localhost:7002/gce/ops
   * curl -X POST -H "Content-Type: application/json" -d '[ { "upsertScalingPolicy": { "serverGroupName": "autoscale-regional", "region": "us-central1", "credentials": "my-google-account", "autoscalingPolicy": { "maxNumReplicas": 3, "minNumReplicas": 2 , "coolDownPeriodSec" : 60 }}} ]' localhost:7002/gce/ops
   * curl -X POST -H "Content-Type: application/json" -d '[ { "upsertScalingPolicy": { "serverGroupName": "autoscale-regional", "region": "us-central1", "credentials": "my-google-account", "autoscalingPolicy": { "coolDownPeriodSec": 35, "cpuUtilization": { "utilizationTarget": 0.9 }, "loadBalancingUtilization": { "utilizationTarget" : 0.6 }, "customMetricUtilizations" : [ { "metric": "myMetric", "utilizationTarget": 0.9, "utilizationTargetType" : "DELTA_PER_SECOND" } ] }}} ]' localhost:7002/gce/ops
   * curl -X POST -H "Content-Type: application/json" -d '[ { "upsertScalingPolicy": { "serverGroupName": "autoscale-regional", "region": "us-central1", "credentials": "my-google-account", "autoscalingPolicy": { "maxNumReplicas": 2, "minNumReplicas": 1, "coolDownPeriodSec": 30, "cpuUtilization": {}, "loadBalancingUtilization": {}, "customMetricUtilizations" : [] }}} ]' localhost:7002/gce/ops
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
    def ancestorAutoscalingPolicyDescription =
      GCEUtil.buildAutoscalingPolicyDescriptionFromAutoscalingPolicy(serverGroup.autoscalingPolicy)

    if (ancestorAutoscalingPolicyDescription) {
      task.updateStatus BASE_PHASE, "Updating autoscaler for $serverGroupName..."

      def autoscaler = GCEUtil.buildAutoscaler(serverGroupName,
                                               serverGroup.selfLink,
                                               copyAndOverrideAncestor(ancestorAutoscalingPolicyDescription,
                                                                       description.autoscalingPolicy))

      if (isRegional) {
        compute.regionAutoscalers().update(project, region, autoscaler).execute()
      } else {
        compute.autoscalers().update(project, zone, autoscaler).execute()
      }
    } else {
      task.updateStatus BASE_PHASE, "Creating new autoscaler for $serverGroupName..."

      def autoscaler = GCEUtil.buildAutoscaler(serverGroupName,
                                               serverGroup.selfLink,
                                               description.autoscalingPolicy)

      if (isRegional) {
        compute.regionAutoscalers().insert(project, region, autoscaler).execute()
      } else {
        compute.autoscalers().insert(project, zone, autoscaler).execute()
      }
    }
    null
  }

  private static GoogleAutoscalingPolicy copyAndOverrideAncestor(GoogleAutoscalingPolicy ancestor,
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

    newDescription
  }
}
