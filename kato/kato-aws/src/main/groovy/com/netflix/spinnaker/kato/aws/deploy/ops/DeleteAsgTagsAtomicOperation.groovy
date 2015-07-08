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

import com.amazonaws.services.autoscaling.model.DeleteTagsRequest
import com.amazonaws.services.autoscaling.model.DescribeAutoScalingGroupsRequest
import com.amazonaws.services.autoscaling.model.Tag
import com.netflix.amazoncomponents.security.AmazonClientProvider
import com.netflix.spinnaker.kato.data.task.Task
import com.netflix.spinnaker.kato.data.task.TaskRepository
import com.netflix.spinnaker.kato.aws.deploy.description.DeleteAsgTagsDescription
import com.netflix.spinnaker.kato.orchestration.AtomicOperation
import org.springframework.beans.factory.annotation.Autowired

class DeleteAsgTagsAtomicOperation implements AtomicOperation<Void> {
  private static final String BASE_PHASE = "DELETE_ASG_TAGS"

  private static Task getTask() {
    TaskRepository.threadLocalTask.get()
  }

  private final DeleteAsgTagsDescription description

  DeleteAsgTagsAtomicOperation(DeleteAsgTagsDescription description) {
    this.description = description
  }

  @Autowired
  AmazonClientProvider amazonClientProvider

  @Override
  Void operate(List priorOutputs) {
    String descriptor = description.asgName ?: description.asgs.collect { it.toString() }
    task.updateStatus BASE_PHASE, "Initializing Delete ASG Tags operation for $descriptor..."
    for (region in description.regions) {
      deleteAsgTags(description.asgName, region)
    }
    for (asg in description.asgs) {
      deleteAsgTags(asg.asgName, asg.region)
    }
    task.updateStatus BASE_PHASE, "Finished Delete ASG Tags operation for $descriptor."
    null
  }

  private void deleteAsgTags(String asgName, String region) {
    def autoScaling = amazonClientProvider.getAutoScaling(description.credentials, region, true)
    def result = autoScaling.describeAutoScalingGroups(new DescribeAutoScalingGroupsRequest().withAutoScalingGroupNames(asgName))
    if (!result.autoScalingGroups) {
      task.updateStatus BASE_PHASE, "No ASG named $asgName found in $region"
      return
    }
    def deleteTagsRequest = new DeleteTagsRequest(tags: description.tagKeys.collect { new Tag(resourceId: asgName, resourceType: "auto-scaling-group", key: it) })
    autoScaling.deleteTags(deleteTagsRequest)
    task.updateStatus BASE_PHASE, "Tags deleted for $asgName in $region"
  }
}
