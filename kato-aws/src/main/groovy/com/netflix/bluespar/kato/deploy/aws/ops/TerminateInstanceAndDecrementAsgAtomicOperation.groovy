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

import com.amazonaws.services.autoscaling.model.DescribeAutoScalingGroupsRequest
import com.amazonaws.services.autoscaling.model.TerminateInstanceInAutoScalingGroupRequest
import com.amazonaws.services.elasticloadbalancing.model.DeregisterInstancesFromLoadBalancerRequest
import com.amazonaws.services.elasticloadbalancing.model.Instance
import com.netflix.bluespar.kato.data.task.Task
import com.netflix.bluespar.kato.data.task.TaskRepository
import com.netflix.bluespar.kato.deploy.aws.description.TerminateInstanceAndDecrementAsgDescription
import com.netflix.bluespar.kato.orchestration.AtomicOperation
import com.netflix.bluespar.kato.security.aws.AmazonClientProvider
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
    def autoScaling = amazonClientProvider.getAutoScaling(description.credentials, description.region)
    def result = autoScaling.describeAutoScalingGroups(new DescribeAutoScalingGroupsRequest().withAutoScalingGroupNames(description.asgName))
    if (!result.autoScalingGroups) {
      task.updateStatus BASE_PHASE, "ASG not found in specified regions."
      throw new AutoScalingGroupNotFoundException()
    }
    def asg = result.autoScalingGroups.getAt(0)
    def loadBalancing = amazonClientProvider.getAmazonElasticLoadBalancing(description.credentials, description.region)
    for (loadBalancer in asg.loadBalancerNames) {
      def deregisterRequest = new DeregisterInstancesFromLoadBalancerRequest(loadBalancer, [new Instance(description.instance)])
      loadBalancing.deregisterInstancesFromLoadBalancer(deregisterRequest)
    }
    autoScaling.terminateInstanceInAutoScalingGroup(new TerminateInstanceInAutoScalingGroupRequest().withInstanceId(description.instance).withShouldDecrementDesiredCapacity(true))
  }

  @InheritConstructors
  static class AutoScalingGroupNotFoundException extends RuntimeException {}
}
