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

import com.amazonaws.services.autoscaling.model.AutoScalingGroup
import com.amazonaws.services.elasticloadbalancing.model.DeregisterInstancesFromLoadBalancerRequest
import com.amazonaws.services.elasticloadbalancing.model.Instance
import com.amazonaws.services.elasticloadbalancing.model.RegisterInstancesWithLoadBalancerRequest
import com.netflix.amazoncomponents.security.AmazonClientProvider
import com.netflix.spinnaker.kato.aws.deploy.description.InstanceLoadBalancerRegistrationDescription
import com.netflix.spinnaker.kato.aws.services.RegionScopedProviderFactory
import com.netflix.spinnaker.kato.data.task.Task
import com.netflix.spinnaker.kato.data.task.TaskRepository
import com.netflix.spinnaker.kato.orchestration.AtomicOperation
import org.springframework.beans.factory.annotation.Autowired

abstract class AbstractInstanceLoadBalancerRegistrationAtomicOperation implements AtomicOperation<Void> {
  abstract boolean isRegister()

  abstract String getPhaseName()

  InstanceLoadBalancerRegistrationDescription description

  AbstractInstanceLoadBalancerRegistrationAtomicOperation(InstanceLoadBalancerRegistrationDescription description) {
    this.description = description
  }

  @Autowired
  AmazonClientProvider amazonClientProvider

  @Autowired
  RegionScopedProviderFactory regionScopedProviderFactory

  @Override
  Void operate(List priorOutputs) {
    def performingAction = isRegister() ? 'Registering' : 'Deregistering'
    def task = getTask()

    task.updateStatus phaseName, "Initializing ${performingAction} of Instances (${description.instanceIds.join(", ")}) in Load Balancer Operation..."
    def amazonELB = amazonClientProvider.getAmazonElasticLoadBalancing(description.credentials, description.region)
    def asgService = regionScopedProviderFactory.forRegion(description.credentials, description.region).asgService
    asgService.getAutoScalingGroups(description.asgName ? [description.asgName] : null).each { AutoScalingGroup asg ->
      def asgInstanceIds = asg.instances*.instanceId as Set<String>
      def instancesInAsg = description.instanceIds.findAll {
        asgInstanceIds.contains(it)
      }.collect { new Instance(it)}

      if (!instancesInAsg) {
        return
      }

      def loadBalancerNames = asg.loadBalancerNames
      if (!loadBalancerNames) {
        // instances exist in this ASG but there is no load balancer to act against
        task.updateStatus phaseName, "No load balancers associated with ASG (${asg.autoScalingGroupName})"
        task.fail()
        return
      }

      loadBalancerNames.each {
        task.updateStatus phaseName, "${performingAction} instances (${instancesInAsg*.instanceId.join(", ")}) in ${it}."
        if (isRegister()) {
          amazonELB.registerInstancesWithLoadBalancer(new RegisterInstancesWithLoadBalancerRequest(
              it,
              instancesInAsg
          ))
        } else {
          amazonELB.deregisterInstancesFromLoadBalancer(new DeregisterInstancesFromLoadBalancerRequest(
              it,
              instancesInAsg
          ))
        }
        task.updateStatus phaseName, "Finished ${performingAction.toLowerCase()} instances (${instancesInAsg*.instanceId.join(", ")}) in ${it}."
      }
    }

    task.updateStatus phaseName, "${performingAction} completed."
    null
  }

  private Task getTask() {
    TaskRepository.threadLocalTask.get()
  }

}
