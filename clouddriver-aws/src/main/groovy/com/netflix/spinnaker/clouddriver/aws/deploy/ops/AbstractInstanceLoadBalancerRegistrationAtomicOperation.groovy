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

import com.amazonaws.services.autoscaling.model.AutoScalingGroup
import com.amazonaws.services.elasticloadbalancing.model.DeregisterInstancesFromLoadBalancerRequest
import com.amazonaws.services.elasticloadbalancing.model.Instance
import com.amazonaws.services.elasticloadbalancing.model.RegisterInstancesWithLoadBalancerRequest
import com.google.common.annotations.VisibleForTesting
import com.netflix.spinnaker.clouddriver.aws.deploy.ops.loadbalancer.LoadBalancerLookupHelper
import com.netflix.spinnaker.clouddriver.data.task.Task
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation
import com.netflix.spinnaker.clouddriver.aws.deploy.description.InstanceLoadBalancerRegistrationDescription
import com.netflix.spinnaker.clouddriver.aws.services.RegionScopedProviderFactory
import groovy.transform.PackageScope
import org.springframework.beans.factory.annotation.Autowired

abstract class AbstractInstanceLoadBalancerRegistrationAtomicOperation implements AtomicOperation<Void> {
  abstract RegistrationAction getRegistrationAction()
  abstract String getPhaseName()

  InstanceLoadBalancerRegistrationDescription description

  AbstractInstanceLoadBalancerRegistrationAtomicOperation(InstanceLoadBalancerRegistrationDescription description) {
    this.description = description
  }

  @Autowired
  RegionScopedProviderFactory regionScopedProviderFactory

  @Override
  Void operate(List priorOutputs) {
    def performingAction = getRegistrationAction().actionVerb()
    def task = getTask()
    def asg = null

    task.updateStatus phaseName, "Initializing ${performingAction} of Instances (${description.instanceIds.join(", ")}) in Load Balancer Operation..."

    def regionScopedProvider = regionScopedProviderFactory.forRegion(description.credentials, description.region)
    if (description.asgName) {
      def asgService = regionScopedProvider.asgService
      asg = asgService.getAutoScalingGroup(description.asgName)
    }

    def instances = getInstanceIds(asg)
    def loadBalancers = asg ? lookupHelper().getLoadBalancersFromAsg(asg) : lookupHelper().getLoadBalancersByName(regionScopedProvider, description.loadBalancerNames)
    if (loadBalancers.unknownLoadBalancers) {
      throw new IllegalStateException("loadbalancers not found: $loadBalancers.unknownLoadBalancers")
    }

    if (!loadBalancers.classicLoadBalancers) {
      // instances may exist there are no load balancers to act against
      task.updateStatus phaseName, "${performingAction} instances not required for Instances ${description.instanceIds.join(", ")}, no load balancers are found"
      return
    }

    operateOnInstances(regionScopedProvider, loadBalancers, instances, performingAction)

    task.updateStatus phaseName, "${performingAction} completed."
    null
  }

  private Collection<String> getInstanceIds(AutoScalingGroup asg) {
    if (asg) {
      def asgInstanceIds = asg.instances*.instanceId as Set<String>
      return description.instanceIds.findAll {
        asgInstanceIds.contains(it)
      }
    }
    return description.instanceIds ?: []
  }

  private void operateOnInstances(RegionScopedProviderFactory.RegionScopedProvider regionScopedProvider, LoadBalancerLookupHelper.LoadBalancerLookupResult loadBalancers, Collection<String> instanceIds, String performingAction) {
    def task = getTask()
    if (loadBalancers.classicLoadBalancers) {
      def instances = instanceIds.collect { new Instance(instanceId: it) }

      def amazonELB = regionScopedProvider.getAmazonElasticLoadBalancing()
      loadBalancers.classicLoadBalancers.each {
        task.updateStatus phaseName, "${performingAction} instances ($instanceIds) in ${it}."
        if (getRegistrationAction() == RegistrationAction.REGISTER) {
          amazonELB.registerInstancesWithLoadBalancer(new RegisterInstancesWithLoadBalancerRequest(
            it,
            instances
          ))
        } else {
          amazonELB.deregisterInstancesFromLoadBalancer(new DeregisterInstancesFromLoadBalancerRequest(
            it,
            instances
          ))
        }
        task.updateStatus phaseName, "Finished ${performingAction.toLowerCase()} instances (${instances*.instanceId.join(", ")}) in ${it}."
      }
    }
  }

  @PackageScope
  @VisibleForTesting
  LoadBalancerLookupHelper lookupHelper() {
    return new LoadBalancerLookupHelper()
  }

  private Task getTask() {
    TaskRepository.threadLocalTask.get()
  }

}
