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

import com.amazonaws.AmazonClientException
import com.amazonaws.services.autoscaling.model.AutoScalingGroup
import com.amazonaws.services.autoscaling.model.DeleteAutoScalingGroupRequest
import com.amazonaws.services.autoscaling.model.DeleteLaunchConfigurationRequest
import com.amazonaws.services.autoscaling.model.DescribeAutoScalingGroupsRequest
import com.amazonaws.services.ec2.model.TerminateInstancesRequest
import com.netflix.spinnaker.clouddriver.aws.security.AmazonClientProvider
import com.netflix.spinnaker.kato.aws.deploy.description.DestroyAsgDescription
import com.netflix.spinnaker.kato.data.task.Task
import com.netflix.spinnaker.kato.data.task.TaskRepository
import com.netflix.spinnaker.kato.orchestration.AtomicOperation
import org.springframework.beans.factory.annotation.Autowired

class DestroyAsgAtomicOperation implements AtomicOperation<Void> {
  protected static final MAX_SIMULTANEOUS_TERMINATIONS = 100
  private static final String BASE_PHASE = "DESTROY_ASG"

  private static Task getTask() {
    TaskRepository.threadLocalTask.get()
  }

  @Autowired
  AmazonClientProvider amazonClientProvider

  private final DestroyAsgDescription description

  DestroyAsgAtomicOperation(DestroyAsgDescription description) {
    this.description = description
  }

  @Override
  Void operate(List priorOutputs) {
    String descriptor = description.asgName ?: description.asgs.collect { it.toString() }
    task.updateStatus BASE_PHASE, "Initializing ASG Destroy operation for $descriptor..."
    for (region in description.regions) {
      deleteAsg(description.asgName, region)
    }
    for (asg in description.asgs) {
      deleteAsg(asg.asgName, asg.region)
    }

    task.updateStatus BASE_PHASE, "Finished Destroy ASG operation for $descriptor."
    null
  }

  private void deleteAsg(String asgName, String region) {
    def credentials = description.credentials
    def autoScaling = amazonClientProvider.getAutoScaling(credentials, region, true)
    task.updateStatus BASE_PHASE, "Looking up instance ids for $asgName in $region..."

    def result = autoScaling.describeAutoScalingGroups(
        new DescribeAutoScalingGroupsRequest(autoScalingGroupNames: [asgName]))
    if (!result.autoScalingGroups) {
      return // Okay, there is no auto scaling group. Let's be idempotent and not complain about that.
    }
    if (result.autoScalingGroups.size() > 1) {
      throw new IllegalStateException(
          "There should only be one ASG in ${credentials}:${region} named ${asgName}")
    }
    AutoScalingGroup autoScalingGroup = result.autoScalingGroups[0]
    List<String> instanceIds = autoScalingGroup.instances.instanceId

    task.updateStatus BASE_PHASE, "Force deleting $asgName in $region."
    autoScaling.deleteAutoScalingGroup(new DeleteAutoScalingGroupRequest(
        autoScalingGroupName: asgName, forceDelete: true))

    if (autoScalingGroup.launchConfigurationName) {
      task.updateStatus BASE_PHASE, "Deleting launch config ${autoScalingGroup.launchConfigurationName} in $region."
      autoScaling.deleteLaunchConfiguration(new DeleteLaunchConfigurationRequest(
          launchConfigurationName: autoScalingGroup.launchConfigurationName))
    }
    def ec2 = amazonClientProvider.getAmazonEC2(credentials, region, true)

    for (int i = 0; i < instanceIds.size(); i += MAX_SIMULTANEOUS_TERMINATIONS) {
      int end = Math.min(instanceIds.size(), i + MAX_SIMULTANEOUS_TERMINATIONS)
      try {
        task.updateStatus BASE_PHASE, "Issuing terminate instances request for ${end - i} instances."
        ec2.terminateInstances(new TerminateInstancesRequest().withInstanceIds(instanceIds.subList(i, end)))
      } catch (AmazonClientException e) {
        task.updateStatus BASE_PHASE, "Unable to terminate instances, reason: '${e.message}'"
      }
    }
  }

}
