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

import com.amazonaws.services.autoscaling.model.DescribeAutoScalingGroupsRequest
import com.amazonaws.services.autoscaling.model.UpdateAutoScalingGroupRequest
import com.netflix.spinnaker.clouddriver.aws.security.AmazonClientProvider
import com.netflix.spinnaker.clouddriver.data.task.Task
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation
import com.netflix.spinnaker.clouddriver.aws.deploy.description.ResizeAsgDescription
import org.springframework.beans.factory.annotation.Autowired

class ResizeAsgAtomicOperation implements AtomicOperation<Void> {
  private static final String PHASE = "RESIZE"

  private static Task getTask() {
    TaskRepository.threadLocalTask.get()
  }

  @Autowired
  AmazonClientProvider amazonClientProvider

  final ResizeAsgDescription description

  ResizeAsgAtomicOperation(ResizeAsgDescription description) {
    this.description = description
  }

  @Override
  Void operate(List priorOutputs) {
    String descriptor = description.asgName ?:
        description.asgs.size() == 1 ? description.asgs[0].toString() :
        description.asgs.collect { it.toString() }
    task.updateStatus PHASE, "Initializing Resize ASG operation for $descriptor..."

    for (String region : description.regions) {
      resizeAsg(description.asgName, region, description.capacity)
    }
    for (asg in description.asgs) {
      resizeAsg(asg.asgName, asg.region, asg.capacity)
    }
    task.updateStatus PHASE, "Finished Resize ASG operation for $descriptor."
    null
  }

  private void resizeAsg(String asgName, String region, ResizeAsgDescription.Capacity capacity) {
    task.updateStatus PHASE, "Beginning resize of ${asgName} in ${region} to ${capacity}."
    def autoScaling = amazonClientProvider.getAutoScaling(description.credentials, region, true)
    def describeAutoScalingGroups = autoScaling.describeAutoScalingGroups(
      new DescribeAutoScalingGroupsRequest().withAutoScalingGroupNames(asgName)
    )
    if (describeAutoScalingGroups.autoScalingGroups.isEmpty() || describeAutoScalingGroups.autoScalingGroups.get(0).status != null) {
      task.updateStatus PHASE, "Skipping resize of ${asgName} in ${region}, server group does not exist"
      return
    }

    def request = new UpdateAutoScalingGroupRequest().withAutoScalingGroupName(asgName)
        .withMinSize(capacity.min).withMaxSize(capacity.max)
        .withDesiredCapacity(capacity.desired)

    autoScaling.updateAutoScalingGroup request
    task.updateStatus PHASE, "Completed resize of ${asgName} in ${region}."
  }
}
