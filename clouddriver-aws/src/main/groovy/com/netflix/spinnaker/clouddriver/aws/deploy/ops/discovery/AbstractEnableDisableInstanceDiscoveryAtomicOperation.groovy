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

package com.netflix.spinnaker.clouddriver.aws.deploy.ops.discovery

import com.amazonaws.services.autoscaling.model.AutoScalingGroup
import com.amazonaws.services.elasticloadbalancing.model.Instance
import com.netflix.spinnaker.clouddriver.data.task.Task
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository
import com.netflix.spinnaker.clouddriver.eureka.deploy.ops.AbstractEurekaSupport
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation
import com.netflix.spinnaker.clouddriver.aws.deploy.description.EnableDisableInstanceDiscoveryDescription
import com.netflix.spinnaker.clouddriver.aws.services.RegionScopedProviderFactory
import org.springframework.beans.factory.annotation.Autowired

abstract class AbstractEnableDisableInstanceDiscoveryAtomicOperation implements AtomicOperation<Void> {
  abstract boolean isEnable()

  abstract String getPhaseName()

  @Autowired
  RegionScopedProviderFactory regionScopedProviderFactory

  @Autowired
  AwsEurekaSupport discoverySupport

  EnableDisableInstanceDiscoveryDescription description

  AbstractEnableDisableInstanceDiscoveryAtomicOperation(EnableDisableInstanceDiscoveryDescription description) {
    this.description = description
  }

  @Override
  Void operate(List priorOutputs) {
    def performingAction = isEnable() ? 'Enabling' : 'Disabling'
    def task = getTask()

    task.updateStatus phaseName, "Initializing ${performingAction} of Instances (${description.instanceIds.join(", ")}) in Discovery Operation..."
    if (!description.credentials.discoveryEnabled) {
      task.updateStatus phaseName, "Discovery is not enabled, unable to modify instance status"
      task.fail()
      return null
    }

    def asgService = regionScopedProviderFactory.forRegion(description.credentials, description.region).asgService
    asgService.getAutoScalingGroups(description.asgName ? [description.asgName] : null).each { AutoScalingGroup asg ->
      def asgInstanceIds = asg.instances.findAll {
        it.lifecycleState == "InService" || it.lifecycleState.startsWith("Pending")
      }*.instanceId as Set<String>
      def instancesInAsg = description.instanceIds.findAll {
        asgInstanceIds.contains(it)
      }.collect { new Instance(it)}

      if (!instancesInAsg) {
        return
      }

      def status = isEnable() ? AbstractEurekaSupport.DiscoveryStatus.Enable : AbstractEurekaSupport.DiscoveryStatus.Disable
      discoverySupport.updateDiscoveryStatusForInstances(
          description, task, phaseName, status, instancesInAsg*.instanceId, true
      )
    }

    null
  }

  Task getTask() {
    TaskRepository.threadLocalTask.get()
  }
}
