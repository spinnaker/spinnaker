/*
 * Copyright 2014 Netflix, Inc.
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

import software.amazon.awssdk.services.autoscaling.model.CreateOrUpdateTagsRequest
import software.amazon.awssdk.services.autoscaling.model.DescribeAutoScalingGroupsRequest
import software.amazon.awssdk.services.autoscaling.model.Tag
import com.netflix.spinnaker.clouddriver.aws.security.AmazonClientProvider
import com.netflix.spinnaker.clouddriver.data.task.Task
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation
import com.netflix.spinnaker.clouddriver.aws.deploy.description.UpsertAsgTagsDescription
import org.springframework.beans.factory.annotation.Autowired

class UpsertAsgTagsAtomicOperation implements AtomicOperation<Void> {
  private static final String BASE_PHASE = "UPSERT_ASG_TAGS"

  private static Task getTask() {
    TaskRepository.threadLocalTask.get()
  }

  private final UpsertAsgTagsDescription description

  UpsertAsgTagsAtomicOperation(UpsertAsgTagsDescription description) {
    this.description = description
  }

  @Autowired
  AmazonClientProvider amazonClientProvider

  @Override
  Void operate(List priorOutputs) {
    boolean hasSucceeded = true
    String descriptor = description.asgs.collect { it.toString() }
    task.updateStatus BASE_PHASE, "Initializing Upsert ASG Tags operation for $descriptor..."
    for (asg in description.asgs) {
      hasSucceeded = upsertAsgTags(asg.serverGroupName, asg.region)
    }
    if (!hasSucceeded) {
      task.fail()
    } else {
      task.updateStatus BASE_PHASE, "Finished Upsert ASG Tags operation for $descriptor."
    }
    null
  }

  private boolean upsertAsgTags(String asgName, String region) {
    try {
      def autoScaling = amazonClientProvider.getAutoScalingV2(description.credentials, region)
      def result = autoScaling.describeAutoScalingGroups(DescribeAutoScalingGroupsRequest.builder().autoScalingGroupNames(asgName).build())
      if (!result.autoScalingGroups()) {
        task.updateStatus BASE_PHASE, "No ASG named $asgName found in $region"
        return false
      }
      task.updateStatus BASE_PHASE, "Preparing tags for $asgName in $region..."
      def tags = description.tags.collect { k, v -> Tag.builder().key(k).value(v).resourceId(asgName).resourceType("auto-scaling-group").propagateAtLaunch(true).build() }
      def createTagsRequest = CreateOrUpdateTagsRequest.builder().tags(tags).build()
      task.updateStatus BASE_PHASE, "Creating tags for $asgName in $region..."
      autoScaling.createOrUpdateTags(createTagsRequest)
      task.updateStatus BASE_PHASE, "Tags created for $asgName in $region"
      return true
    } catch (e) {
      task.updateStatus BASE_PHASE, "Could not upsert ASG tags for ASG '$asgName' in region $region! Reason: $e.message"
    }
  }
}
