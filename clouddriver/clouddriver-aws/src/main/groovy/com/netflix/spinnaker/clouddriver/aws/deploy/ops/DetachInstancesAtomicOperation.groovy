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

import software.amazon.awssdk.services.autoscaling.model.AutoScalingGroup
import software.amazon.awssdk.services.autoscaling.model.DescribeAutoScalingGroupsRequest
import software.amazon.awssdk.services.autoscaling.model.DetachInstancesRequest
import software.amazon.awssdk.services.autoscaling.model.LifecycleState
import software.amazon.awssdk.services.autoscaling.model.UpdateAutoScalingGroupRequest
import software.amazon.awssdk.services.ec2.model.CreateTagsRequest
import software.amazon.awssdk.services.ec2.model.Tag
import software.amazon.awssdk.services.ec2.model.TerminateInstancesRequest
import com.netflix.spinnaker.clouddriver.aws.security.AmazonClientProvider
import com.netflix.spinnaker.clouddriver.data.task.Task
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation
import com.netflix.spinnaker.clouddriver.aws.deploy.description.DetachInstancesDescription
import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Autowired

@Slf4j
class DetachInstancesAtomicOperation implements AtomicOperation<Void> {
  private static final int MAX_DETACH = 20
  private static final Set<LifecycleState> ALLOWED_LIFECYCLE_STATES = [
    LifecycleState.IN_SERVICE, LifecycleState.STANDBY
  ]

  private static final String BASE_PHASE = "DETACH_INSTANCES"
  private static final String TAG_DETACHED = "spinnaker:Detached"
  public static final String TAG_PENDING_TERMINATION = "spinnaker:PendingTermination"

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
    def amazonAutoScaling = amazonClientProvider.getAutoScalingV2(description.credentials, description.region)
    amazonAutoScaling.describeAutoScalingGroups(
      DescribeAutoScalingGroupsRequest.builder().autoScalingGroupNames(description.asgName).build()
    ).autoScalingGroups().each { AutoScalingGroup autoScalingGroup ->
      def validInstanceIds = description.instanceIds.intersect(autoScalingGroup.instances()*.instanceId())
      if (!validInstanceIds) {
        // no work to do, no-op
        return
      }

      validInstanceIds = validInstanceIds.findAll { String instanceId ->
        def instance = autoScalingGroup.instances().find { it.instanceId() == instanceId }
        if (ALLOWED_LIFECYCLE_STATES.contains(instance.lifecycleState())) {
          return true
        }

        task.updateStatus BASE_PHASE, "Unable to detach instance ${instanceId} (lifecycleState: ${instance.lifecycleStateAsString()}, asgName: ${description.asgName})"
        return false
      }

      int newMin = autoScalingGroup.desiredCapacity() - validInstanceIds.size()
      if (description.decrementDesiredCapacity && newMin < autoScalingGroup.minSize()) {
        if (description.adjustMinIfNecessary) {
          if (newMin < 0) {
            task.updateStatus BASE_PHASE, "Cannot adjust min size below 0"
          } else {
            amazonAutoScaling.updateAutoScalingGroup(
              UpdateAutoScalingGroupRequest.builder().autoScalingGroupName(autoScalingGroup.autoScalingGroupName()).minSize(newMin).build()
            )
          }
        } else {
          task.updateStatus BASE_PHASE, "Cannot decrement ASG below minSize - set adjustMinIfNecessary to resize down minSize before detaching instances"
          throw new IllegalStateException("Invalid ASG capacity for detachInstances (min: ${autoScalingGroup.minSize()}, max: ${autoScalingGroup.maxSize()}, desired: ${autoScalingGroup.desiredCapacity()})")
        }
      }

      if (validInstanceIds.isEmpty()) {
        task.updateStatus BASE_PHASE, "No detachable instances"
        return
      }

      task.updateStatus BASE_PHASE, "Tagging instances (${validInstanceIds.join(", ")})."
      def tags = [Tag.builder().key(TAG_DETACHED).value(description.asgName).build()]
      if (description.terminateDetachedInstances) {
        tags << Tag.builder().key(TAG_PENDING_TERMINATION).value(System.currentTimeMillis() as String).build()
      }

      def amazonEC2 = amazonClientProvider.getAmazonEC2V2(description.credentials, description.region)
      amazonEC2.createTags(CreateTagsRequest.builder().resources(validInstanceIds).tags(tags).build())
      task.updateStatus BASE_PHASE, "Tagged instances (${validInstanceIds.join(", ")})."

      validInstanceIds.collate(MAX_DETACH).each {
        // AWS has a restriction on the # of instances that can be detached at any one time, hence batching is required.
        task.updateStatus BASE_PHASE, "Detaching instances (${it.join(", ")}) from ASG (${description.asgName})."
        amazonAutoScaling.detachInstances(
          DetachInstancesRequest.builder()
            .autoScalingGroupName(description.asgName)
            .instanceIds(it)
            .shouldDecrementDesiredCapacity(description.decrementDesiredCapacity)
            .build()
        )
        task.updateStatus BASE_PHASE, "Detached instances (${it.join(", ")}) from ASG (${description.asgName})."
      }

      if (description.terminateDetachedInstances) {
        task.updateStatus BASE_PHASE, "Terminating instances (${validInstanceIds.join(", ")})."
        amazonEC2.terminateInstances(TerminateInstancesRequest.builder().instanceIds(validInstanceIds).build())
        task.updateStatus BASE_PHASE, "Terminated instances (${validInstanceIds.join(", ")})."
      }
    }

    null
  }
}
