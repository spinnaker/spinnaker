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

package com.netflix.bluespar.kato.deploy.aws.ops

import com.amazonaws.services.autoscaling.model.CreateOrUpdateTagsRequest
import com.amazonaws.services.autoscaling.model.DescribeAutoScalingGroupsRequest
import com.amazonaws.services.autoscaling.model.Tag
import com.netflix.bluespar.amazon.security.AmazonClientProvider
import com.netflix.bluespar.kato.data.task.Task
import com.netflix.bluespar.kato.data.task.TaskRepository
import com.netflix.bluespar.kato.deploy.aws.description.TagAsgDescription
import com.netflix.bluespar.kato.orchestration.AtomicOperation
import org.springframework.beans.factory.annotation.Autowired

class TagAsgAtomicOperation implements AtomicOperation<Void> {
  private static final String BASE_PHASE = "TAG_ASG"

  private static Task getTask() {
    TaskRepository.threadLocalTask.get()
  }

  private final TagAsgDescription description

  TagAsgAtomicOperation(TagAsgDescription description) {
    this.description = description
  }

  @Autowired
  AmazonClientProvider amazonClientProvider

  @Override
  Void operate(List priorOutputs) {
    task.updateStatus BASE_PHASE, "Initializing Tagging ASG operation for $description.asgName..."
    for (region in description.regions) {
      def autoScaling = amazonClientProvider.getAutoScaling(description.credentials, region)
      def request = autoScaling.describeAutoScalingGroups(new DescribeAutoScalingGroupsRequest().withAutoScalingGroupNames(description.asgName))
      if (!request.autoScalingGroups) {
        task.updateStatus BASE_PHASE, "No ASG named $description.asgName found in $region"
        continue
      }
      task.updateStatus BASE_PHASE, " > Preparing tags for $description.asgName in $region..."
      def tags = description.tags.collect {k, v -> new Tag().withKey(k).withValue(v).withResourceId(description.asgName).withResourceType("auto-scaling-group")}
      def createTagsRequest = new CreateOrUpdateTagsRequest().withTags(tags)
      task.updateStatus BASE_PHASE, " > Creating tags for $description.asgName in $region..."
      autoScaling.createOrUpdateTags(createTagsRequest)
      task.updateStatus BASE_PHASE, "Tags created for $description.asgName in $region"
    }
    task.updateStatus BASE_PHASE, "Done tagging ASG $description.asgName."
    null
  }
}
