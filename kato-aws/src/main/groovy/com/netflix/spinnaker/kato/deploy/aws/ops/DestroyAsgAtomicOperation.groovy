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

package com.netflix.spinnaker.kato.deploy.aws.ops

import com.amazonaws.services.autoscaling.model.DeleteAutoScalingGroupRequest
import com.amazonaws.services.autoscaling.model.DescribeAutoScalingGroupsRequest
import com.amazonaws.services.autoscaling.model.TerminateInstanceInAutoScalingGroupRequest
import com.amazonaws.services.autoscaling.model.UpdateAutoScalingGroupRequest
import com.netflix.amazoncomponents.security.AmazonClientProvider
import com.netflix.spinnaker.kato.data.task.Task
import com.netflix.spinnaker.kato.data.task.TaskRepository
import com.netflix.spinnaker.kato.deploy.aws.description.DestroyAsgDescription
import com.netflix.spinnaker.kato.orchestration.AtomicOperation
import org.springframework.beans.factory.annotation.Autowired

import java.util.concurrent.ExecutorService
import java.util.concurrent.ForkJoinPool

class DestroyAsgAtomicOperation implements AtomicOperation<Void> {
  private static final String BASE_PHASE = "DESTROY_ASG"

  private static Task getTask() {
    TaskRepository.threadLocalTask.get()
  }

  @Autowired
  AmazonClientProvider amazonClientProvider

  private final DestroyAsgDescription description
  private final ExecutorService executorService = new ForkJoinPool()

  DestroyAsgAtomicOperation(DestroyAsgDescription description) {
    this.description = description
  }

  @Override
  Void operate(List priorOutputs) {
    task.updateStatus BASE_PHASE, "Initializing ASG Destroy Operation..."
    for (region in description.regions) {
      def client = amazonClientProvider.getAutoScaling(description.credentials, region)
      task.updateStatus BASE_PHASE, "Looking up instance ids for $description.asgName in $region..."
      def instanceIds = getInstanceIds(region, description.asgName)
      for (instanceId in instanceIds) {
        task.updateStatus BASE_PHASE, " > Destroying instance $instanceId"
        client.updateAutoScalingGroup(new UpdateAutoScalingGroupRequest().withAutoScalingGroupName(description.asgName).withMinSize(0).withMaxSize(0).withDesiredCapacity(0))
        client.terminateInstanceInAutoScalingGroup(new TerminateInstanceInAutoScalingGroupRequest().withInstanceId(instanceId).withShouldDecrementDesiredCapacity(true))
      }
    }
    def c = { String region ->
      while (true) {
        def instances = getInstanceIds(region, description.asgName)
        if (!instances) {
          break
        }
        Thread.sleep 1000
      }
    }
    def callables = description.regions.collect { c.curry(it) }
    task.updateStatus BASE_PHASE, "Waiting for instances to go away."
    executorService.invokeAll(callables)*.get()

    for (region in description.regions) {
      def client = amazonClientProvider.getAutoScaling(description.credentials, region)
      task.updateStatus BASE_PHASE, "Force deleting $description.asgName in $region."
      client.deleteAutoScalingGroup(new DeleteAutoScalingGroupRequest().withForceDelete(true).withAutoScalingGroupName(description.asgName))
    }
    task.updateStatus BASE_PHASE, "Done destroying $description.asgName in $description.regions."
    null
  }

  List<String> getInstanceIds(String region, String asgName) {
    def client = amazonClientProvider.getAutoScaling(description.credentials, region)
    def result = client.describeAutoScalingGroups(new DescribeAutoScalingGroupsRequest().withAutoScalingGroupNames(asgName))
    def instances = []
    for (asg in result.autoScalingGroups) {
      instances.addAll asg.instances*.instanceId
    }
    instances
  }
}
