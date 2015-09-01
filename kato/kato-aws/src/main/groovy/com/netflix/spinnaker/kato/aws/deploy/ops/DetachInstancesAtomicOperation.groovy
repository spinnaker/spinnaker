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


package com.netflix.spinnaker.kato.aws.deploy.ops

import com.amazonaws.services.autoscaling.model.AutoScalingGroup
import com.amazonaws.services.autoscaling.model.DescribeAutoScalingGroupsRequest
import com.amazonaws.services.autoscaling.model.DetachInstancesRequest
import com.amazonaws.services.ec2.model.TerminateInstancesRequest
import com.netflix.amazoncomponents.security.AmazonClientProvider
import com.netflix.spinnaker.kato.aws.deploy.description.DetachInstancesDescription
import com.netflix.spinnaker.kato.data.task.Task
import com.netflix.spinnaker.kato.data.task.TaskRepository
import com.netflix.spinnaker.kato.orchestration.AtomicOperation
import org.springframework.beans.factory.annotation.Autowired

class DetachInstancesAtomicOperation implements AtomicOperation<Void> {
  private static final String BASE_PHASE = "DETACH_INSTANCES"

  private static Task getTask() {
    TaskRepository.threadLocalTask.get()
  }

  private final DetachInstancesDescription description

  DetachInstancesAtomicOperation(DetachInstancesDescription description) {
    this.description = description
  }

  @Autowired
  AmazonClientProvider amazonClientProvider

  @Override
  Void operate(List priorOutputs) {
    def amazonAutoScaling = amazonClientProvider.getAutoScaling(description.credentials, description.region, true)
    amazonAutoScaling.describeAutoScalingGroups(
      new DescribeAutoScalingGroupsRequest().withAutoScalingGroupNames(description.asgName)
    ).autoScalingGroups.each { AutoScalingGroup autoScalingGroup ->
      def validInstanceIds = description.instanceIds.intersect(autoScalingGroup.instances*.instanceId)

      task.updateStatus BASE_PHASE, "Detaching instances (${validInstanceIds.join(", ")}) from ASG (${description.asgName})."
      amazonAutoScaling.detachInstances(
        new DetachInstancesRequest()
          .withAutoScalingGroupName(description.asgName)
          .withInstanceIds(validInstanceIds)
          .withShouldDecrementDesiredCapacity(description.decrementDesiredCapacity)
      )
      task.updateStatus BASE_PHASE, "Detached instances (${validInstanceIds.join(", ")}) from ASG (${description.asgName})."

      if (description.terminateDetachedInstances) {
        task.updateStatus BASE_PHASE, "Terminating instances (${validInstanceIds.join(", ")})."
        def amazonEC2 = amazonClientProvider.getAmazonEC2(description.credentials, description.region, true)
        amazonEC2.terminateInstances(new TerminateInstancesRequest().withInstanceIds(validInstanceIds))
        task.updateStatus BASE_PHASE, "Terminated instances (${validInstanceIds.join(", ")})."
      }
    }

    null
  }
}

