/*
 * Copyright 2015 Netflix, Inc.
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

package com.netflix.spinnaker.clouddriver.aws.deploy.ops

import software.amazon.awssdk.services.autoscaling.model.DescribeAutoScalingGroupsRequest
import software.amazon.awssdk.services.autoscaling.model.DisableMetricsCollectionRequest
import software.amazon.awssdk.services.autoscaling.model.EnableMetricsCollectionRequest
import software.amazon.awssdk.services.autoscaling.model.UpdateAutoScalingGroupRequest
import com.netflix.spinnaker.clouddriver.aws.deploy.description.ModifyAsgDescription
import com.netflix.spinnaker.clouddriver.aws.security.AmazonClientProvider
import com.netflix.spinnaker.clouddriver.data.task.Task
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation
import org.springframework.beans.factory.annotation.Autowired

class ModifyAsgAtomicOperation implements AtomicOperation<Void> {
  private static final String BASE_PHASE = "MODIFY_ASG"

  private static Task getTask() {
    TaskRepository.threadLocalTask.get()
  }

  @Autowired
  AmazonClientProvider amazonClientProvider

  private final ModifyAsgDescription description

  ModifyAsgAtomicOperation(ModifyAsgDescription description) {
    this.description = description
  }

  @Override
  Void operate(List priorOutputs) {
    boolean hasSucceeded = true

    String descriptor = description.asgs.collect { it.toString() }
    task.updateStatus BASE_PHASE, "Initializing Update ASG operation for $descriptor..."
    for (asg in description.asgs) {
      hasSucceeded = modifyAsg(asg.serverGroupName, asg.region)
    }

    if (!hasSucceeded) {
      task.fail()
    } else {
      task.updateStatus BASE_PHASE, "Finished Update ASG operation for $descriptor."
    }
    null
  }

  private boolean modifyAsg(String asgName, String region) {
    try {
      def autoScaling = amazonClientProvider.getAutoScalingV2(description.credentials, region)

      def asgResult = autoScaling.describeAutoScalingGroups(DescribeAutoScalingGroupsRequest.builder().autoScalingGroupNames(asgName).build())
      if (!asgResult.autoScalingGroups()) {
        task.updateStatus BASE_PHASE, "No ASG named $asgName found in $region"
        return false
      }
      def asg = asgResult.autoScalingGroups()[0]

      task.updateStatus BASE_PHASE, "Updating $asgName in $region..."

      def updateRequest = UpdateAutoScalingGroupRequest.builder()
          .autoScalingGroupName(asgName)
          .defaultCooldown(description.cooldown)
          .healthCheckGracePeriod(description.healthCheckGracePeriod)
          .healthCheckType(description.healthCheckType)
          .terminationPolicies(description.terminationPolicies)
          .capacityRebalance(description.capacityRebalance)
          .build()

      // enable / disable metrics
      def desiredMetrics = description.enabledMetrics ?: []
      def metricsToDisable = []
      asg.enabledMetrics().each {
        if (!desiredMetrics.contains(it.metric)) {
          metricsToDisable << it.metric
        }
      }
      if (metricsToDisable) {
        task.updateStatus BASE_PHASE, "Disabling unselected Auto Scaling Group metrics for $asgName in $region..."
        autoScaling.disableMetricsCollection(DisableMetricsCollectionRequest.builder()
          .autoScalingGroupName(asgName)
          .metrics(metricsToDisable)
          .build())
      }

      def metricsToEnable = []
      desiredMetrics.each { desiredMetric ->
         if (!asg.enabledMetrics().find { it.metric == desiredMetric }) {
           metricsToEnable << desiredMetric
         }
      }
      if (metricsToEnable) {
        task.updateStatus BASE_PHASE, "Enabling selected Auto Scaling Group metrics for $asgName in $region..."
        autoScaling.enableMetricsCollection(EnableMetricsCollectionRequest.builder()
          .autoScalingGroupName(asgName)
          .granularity('1Minute')
          .metrics(metricsToEnable)
          .build())
      }

      // update sever group
      autoScaling.updateAutoScalingGroup(updateRequest)

      task.updateStatus BASE_PHASE, "Updated $asgName in $region..."
      true
    } catch (Exception e) {
      task.updateStatus BASE_PHASE, "Could not update $asgName in $region! Reason: $e.message"
      return false
    }
  }

}
