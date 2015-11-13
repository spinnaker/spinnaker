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

import com.amazonaws.services.autoscaling.AmazonAutoScaling
import com.amazonaws.services.autoscaling.model.AutoScalingGroup
import com.amazonaws.services.autoscaling.model.DescribeAutoScalingGroupsRequest
import com.amazonaws.services.autoscaling.model.TerminateInstanceInAutoScalingGroupRequest
import com.amazonaws.services.autoscaling.model.UpdateAutoScalingGroupRequest
import com.amazonaws.services.elasticloadbalancing.model.DeregisterInstancesFromLoadBalancerRequest
import com.amazonaws.services.elasticloadbalancing.model.Instance
import com.netflix.spinnaker.clouddriver.aws.security.AmazonClientProvider
import com.netflix.spinnaker.kato.data.task.Task
import com.netflix.spinnaker.kato.data.task.TaskRepository
import com.netflix.spinnaker.kato.aws.deploy.description.TerminateInstanceAndDecrementAsgDescription
import com.netflix.spinnaker.kato.orchestration.AtomicOperation
import groovy.transform.InheritConstructors
import org.springframework.beans.factory.annotation.Autowired

class TerminateInstanceAndDecrementAsgAtomicOperation implements AtomicOperation<Void> {
  private static final String BASE_PHASE = "TERMINATE-AND-DEC-ASG_PHASE"

  private static Task getTask() {
    TaskRepository.threadLocalTask.get()
  }

  private final TerminateInstanceAndDecrementAsgDescription description

  TerminateInstanceAndDecrementAsgAtomicOperation(TerminateInstanceAndDecrementAsgDescription description) {
    this.description = description
  }

  @Autowired
  AmazonClientProvider amazonClientProvider

  @Override
  Void operate(List priorOutputs) {
    task.updateStatus BASE_PHASE, "Initializing termination of $description.instance in $description.asgName"
    def autoScaling = amazonClientProvider.getAutoScaling(description.credentials, description.region, true)
    def asg = getAsg(autoScaling, description.asgName)
    if (asg.minSize == asg.desiredCapacity) {
      if (description.adjustMinIfNecessary) {
        int newMin = asg.minSize - 1
        if (newMin < 0) {
          task.updateStatus BASE_PHASE, "Cannot adjust min size below 0"
        } else {
          autoScaling.updateAutoScalingGroup(new UpdateAutoScalingGroupRequest().withAutoScalingGroupName(asg.autoScalingGroupName).withMinSize(newMin))
        }
      } else {
        task.updateStatus BASE_PHASE, "Cannot decrement ASG below minSize - set adjustMinIfNecessary to resize down minSize before terminating"
        throw new IllegalStateException("Invalid ASG capacity for terminateAndDecrementAsg: min: $asg.minSize, max: $asg.maxSize, desired: $asg.desiredCapacity")
      }
    }
    def loadBalancing = amazonClientProvider.getAmazonElasticLoadBalancing(description.credentials, description.region, true)
    for (loadBalancer in asg.loadBalancerNames) {
      def deregisterRequest = new DeregisterInstancesFromLoadBalancerRequest(loadBalancer, [new Instance(description.instance)])
      loadBalancing.deregisterInstancesFromLoadBalancer(deregisterRequest)
    }

    def termRequest = new TerminateInstanceInAutoScalingGroupRequest().withInstanceId(description.instance).withShouldDecrementDesiredCapacity(true)
    autoScaling.terminateInstanceInAutoScalingGroup(termRequest)
    if (description.setMaxToNewDesired) {
      asg = getAsg(autoScaling, description.asgName)
      if (asg.desiredCapacity != asg.maxSize) {
        autoScaling.updateAutoScalingGroup(new UpdateAutoScalingGroupRequest().withAutoScalingGroupName(description.asgName).withMaxSize(asg.desiredCapacity))
      }
    }
    task.updateStatus BASE_PHASE, "Done executing termination and ASG size decrement."
  }

  AutoScalingGroup getAsg(AmazonAutoScaling autoScaling, String asgName) {
    def result = autoScaling.describeAutoScalingGroups(new DescribeAutoScalingGroupsRequest().withAutoScalingGroupNames(asgName))
    if (!result.autoScalingGroups) {
      task.updateStatus BASE_PHASE, "ASG not found in specified regions."
      throw new AutoScalingGroupNotFoundException()
    }
    result.autoScalingGroups.getAt(0)
  }

  @InheritConstructors
  static class AutoScalingGroupNotFoundException extends RuntimeException {}
}
