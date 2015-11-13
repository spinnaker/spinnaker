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

package com.netflix.spinnaker.kato.aws.deploy.ops

import com.amazonaws.services.autoscaling.model.CreateOrUpdateTagsRequest
import com.amazonaws.services.autoscaling.model.DescribeAutoScalingGroupsRequest
import com.amazonaws.services.autoscaling.model.Tag
import com.netflix.spinnaker.clouddriver.aws.security.AmazonClientProvider
import com.netflix.spinnaker.kato.data.task.Task
import com.netflix.spinnaker.kato.data.task.TaskRepository
import com.netflix.spinnaker.kato.aws.deploy.description.UpsertAsgTagsDescription
import com.netflix.spinnaker.kato.orchestration.AtomicOperation
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
    String descriptor = description.asgName ?: description.asgs.collect { it.toString() }
    task.updateStatus BASE_PHASE, "Initializing Upsert ASG Tags operation for $descriptor..."
    for (region in description.regions) {
      hasSucceeded = upsertAsgTags(description.asgName, region)
    }
    for (asg in description.asgs) {
      hasSucceeded = upsertAsgTags(asg.asgName, asg.region)
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
      def autoScaling = amazonClientProvider.getAutoScaling(description.credentials, region, true)
      def result = autoScaling.describeAutoScalingGroups(new DescribeAutoScalingGroupsRequest().withAutoScalingGroupNames(asgName))
      if (!result.autoScalingGroups) {
        task.updateStatus BASE_PHASE, "No ASG named $asgName found in $region"
        return false
      }
      task.updateStatus BASE_PHASE, "Preparing tags for $asgName in $region..."
      def tags = description.tags.collect { k, v -> new Tag().withKey(k).withValue(v).withResourceId(asgName).withResourceType("auto-scaling-group").withPropagateAtLaunch(true) }
      def createTagsRequest = new CreateOrUpdateTagsRequest().withTags(tags)
      task.updateStatus BASE_PHASE, "Creating tags for $asgName in $region..."
      autoScaling.createOrUpdateTags(createTagsRequest)
      task.updateStatus BASE_PHASE, "Tags created for $asgName in $region"
      return true
    } catch (e) {
      task.updateStatus BASE_PHASE, "Could not upsert ASG tags for ASG '$asgName' in region $region! Reason: $e.message"
    }
  }
}
