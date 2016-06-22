/*
 * Copyright 2016 Netflix, Inc.
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

import com.amazonaws.services.autoscaling.model.DescribeLaunchConfigurationsRequest
import com.amazonaws.services.ec2.model.ModifyInstanceAttributeRequest
import com.google.common.util.concurrent.RateLimiter
import com.netflix.spinnaker.clouddriver.aws.deploy.description.UpdateInstancesDescription
import com.netflix.spinnaker.clouddriver.aws.services.RegionScopedProviderFactory
import com.netflix.spinnaker.clouddriver.data.task.Task
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation
import org.springframework.beans.factory.annotation.Autowired

class UpdateInstancesAtomicOperation implements AtomicOperation<Void> {
  private static final String PHASE = "UPDATE"
  private static final int MAX_REQUESTS_PER_SECOND = 20

  private static Task getTask() {
    TaskRepository.threadLocalTask.get()
  }

  @Autowired
  RegionScopedProviderFactory regionScopedProviderFactory

  final UpdateInstancesDescription description

  UpdateInstancesAtomicOperation(UpdateInstancesDescription description) {
    this.description = description
  }

  @Override
  Void operate(List priorOutputs) {
    task.updateStatus PHASE, "Initializing Update Instances operation for ${description.serverGroupName}..."

    def serverGroupName = description.serverGroupName
    def credentials = description.credentials
    def region = description.region

    def regionScopedProvider = regionScopedProviderFactory.forRegion(credentials, region)

    def asgService = regionScopedProvider.asgService
    def asg = asgService.getAutoScalingGroup(serverGroupName)
    if (!asg) {
      throw new IllegalStateException("Cannot find ASG named $serverGroupName in $region")
    }
    if (!asg.getVPCZoneIdentifier()) {
      throw new IllegalStateException("Cannot update security groups on instances in EC2 Classic")
    }
    def instances = asg.instances.instanceId
    // update up to 20 instances at a time (reduce risk of AWS throttling)
    def groups = description.securityGroups
    if (description.securityGroupsAppendOnly) {
      def launchConfigs = regionScopedProvider.autoScaling.describeLaunchConfigurations(
        new DescribeLaunchConfigurationsRequest().withLaunchConfigurationNames(asg.launchConfigurationName))
      if (launchConfigs.launchConfigurations.empty) {
        throw new IllegalStateException("Could not find launch config ${asg.launchConfigurationName}")
      }
      groups = groups + launchConfigs.launchConfigurations.get(0).securityGroups
    }
    def limiter = RateLimiter.create(MAX_REQUESTS_PER_SECOND)
    instances.each { instanceId ->
      limiter.acquire()
      task.updateStatus PHASE, "Updating security groups for ${instanceId}..."
      try {
        regionScopedProvider.amazonEC2.modifyInstanceAttribute(new ModifyInstanceAttributeRequest(instanceId: instanceId).withGroups(groups))
      } catch (Exception e) {
        task.updateStatus PHASE, "Error updating ${instanceId}: ${e.message}"
      }
    }
    task.updateStatus PHASE, "Updated ${instances.size()} instances in $serverGroupName/$region"
  }
}
