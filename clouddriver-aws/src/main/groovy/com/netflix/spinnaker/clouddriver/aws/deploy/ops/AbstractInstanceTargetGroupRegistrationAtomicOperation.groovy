/*
 * Copyright 2017 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.clouddriver.aws.deploy.ops

import com.amazonaws.services.autoscaling.model.AutoScalingGroup
import com.amazonaws.services.elasticloadbalancingv2.model.DeregisterTargetsRequest
import com.amazonaws.services.elasticloadbalancingv2.model.RegisterTargetsRequest
import com.amazonaws.services.elasticloadbalancingv2.model.TargetDescription
import com.google.common.annotations.VisibleForTesting
import com.netflix.spinnaker.clouddriver.aws.deploy.description.InstanceTargetGroupRegistrationDescription
import com.netflix.spinnaker.clouddriver.aws.deploy.ops.loadbalancer.TargetGroupLookupHelper
import com.netflix.spinnaker.clouddriver.aws.services.RegionScopedProviderFactory
import com.netflix.spinnaker.clouddriver.data.task.Task
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation
import groovy.transform.PackageScope
import org.springframework.beans.factory.annotation.Autowired

abstract class AbstractInstanceTargetGroupRegistrationAtomicOperation implements AtomicOperation<Void> {
  abstract RegistrationAction getRegistrationAction()
  abstract String getPhaseName()
  InstanceTargetGroupRegistrationDescription description

  AbstractInstanceTargetGroupRegistrationAtomicOperation(InstanceTargetGroupRegistrationDescription description) {
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
    def targetGroups = asg ? lookupHelper().getTargetGroupsFromAsg(asg) : lookupHelper().getTargetGroupsByName(regionScopedProvider, description.targetGroupNames)
    if (targetGroups.unknownTargetGroups) {
      throw new IllegalStateException("targetgroups not found: $targetGroups.unknownTargetGroups")
    }

    if (!targetGroups.targetGroupARNs) {
      // instances may exist there are no target groups to act against
      task.updateStatus phaseName, "${performingAction} instances not required for Instances ${description.instanceIds.join(", ")}, no target groups are found"
      return
    }

    operateOnInstances(regionScopedProvider, targetGroups, instances, performingAction)

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

  private void operateOnInstances(RegionScopedProviderFactory.RegionScopedProvider regionScopedProvider, TargetGroupLookupHelper.TargetGroupLookupResult targetGroups, Collection<String> instanceIds, String performingAction) {
    def task = getTask()
    if (targetGroups.targetGroupARNs) {
      def targets = instanceIds.collect { new TargetDescription().withId(it) }
      def elbv2 = regionScopedProvider.getAmazonElasticLoadBalancingV2()
      targetGroups.targetGroupARNs.each {
        task.updateStatus phaseName, "${performingAction} instances ($instanceIds) in target group $it"
        if (getRegistrationAction() == RegistrationAction.REGISTER) {
          elbv2.registerTargets(new RegisterTargetsRequest().withTargetGroupArn(it).withTargets(targets))
        } else {
          elbv2.deregisterTargets(new DeregisterTargetsRequest().withTargetGroupArn(it).withTargets(targets))
        }
      }
    }
  }

  @PackageScope
  @VisibleForTesting
  TargetGroupLookupHelper lookupHelper() {
    return new TargetGroupLookupHelper()
  }

  private Task getTask() {
    TaskRepository.threadLocalTask.get()
  }

}
