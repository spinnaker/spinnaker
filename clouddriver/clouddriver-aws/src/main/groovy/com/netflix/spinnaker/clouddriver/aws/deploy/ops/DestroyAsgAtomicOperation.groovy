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

import software.amazon.awssdk.core.exception.SdkClientException
import software.amazon.awssdk.services.autoscaling.AutoScalingClient
import software.amazon.awssdk.services.autoscaling.model.AutoScalingException
import software.amazon.awssdk.services.autoscaling.model.AutoScalingGroup
import software.amazon.awssdk.services.autoscaling.model.DeleteAutoScalingGroupRequest
import software.amazon.awssdk.services.autoscaling.model.DeleteLaunchConfigurationRequest
import software.amazon.awssdk.services.autoscaling.model.DescribeAutoScalingGroupsRequest
import software.amazon.awssdk.services.ec2.Ec2Client
import software.amazon.awssdk.services.ec2.model.Ec2Exception
import software.amazon.awssdk.services.ec2.model.DeleteLaunchTemplateRequest
import software.amazon.awssdk.services.ec2.model.TerminateInstancesRequest
import com.netflix.spinnaker.clouddriver.aws.AmazonCloudProvider
import com.netflix.spinnaker.clouddriver.aws.deploy.description.DestroyAsgDescription
import com.netflix.spinnaker.clouddriver.aws.security.AmazonClientProvider
import com.netflix.spinnaker.clouddriver.data.task.Task
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation
import com.netflix.spinnaker.clouddriver.orchestration.events.DeleteServerGroupEvent
import com.netflix.spinnaker.clouddriver.orchestration.events.OperationEvent
import com.netflix.spinnaker.kork.core.RetrySupport
import org.springframework.beans.factory.annotation.Autowired

import java.time.Duration

class DestroyAsgAtomicOperation implements AtomicOperation<Void> {
  protected static final MAX_SIMULTANEOUS_TERMINATIONS = 100
  private static final String BASE_PHASE = "DESTROY_ASG"

  private static Task getTask() {
    TaskRepository.threadLocalTask.get()
  }

  @Autowired
  AmazonClientProvider amazonClientProvider

  private final DestroyAsgDescription description
  private final Collection<DeleteServerGroupEvent> events = []
  private final RetrySupport retrySupport = new RetrySupport()

  DestroyAsgAtomicOperation(DestroyAsgDescription description) {
    this.description = description
  }

  @Override
  Void operate(List priorOutputs) {
    String descriptor = description.asgs.collect { it.toString() }
    task.updateStatus BASE_PHASE, "Initializing ASG Destroy operation for $descriptor..."
    for (asg in description.asgs) {
      deleteAsg(asg.serverGroupName, asg.region)
      events << new DeleteServerGroupEvent(
        AmazonCloudProvider.ID, description.credentials.accountId, asg.region, asg.serverGroupName
      )
    }

    task.updateStatus BASE_PHASE, "Finished Destroy ASG operation for $descriptor."
    null
  }

  @Override
  Collection<OperationEvent> getEvents() {
    return events
  }

  private void deleteAsg(String asgName, String region) {
    def credentials = description.credentials
    AutoScalingClient autoScaling = amazonClientProvider.getAutoScalingV2(credentials, region)
    task.updateStatus BASE_PHASE, "Looking up instance ids for $asgName in $region..."

    def result = autoScaling.describeAutoScalingGroups(
        DescribeAutoScalingGroupsRequest.builder().autoScalingGroupNames([asgName]).build())
    if (!result.autoScalingGroups()) {
      return // Okay, there is no auto scaling group. Let's be idempotent and not complain about that.
    }
    if (result.autoScalingGroups().size() > 1) {
      throw new IllegalStateException(
          "There should only be one ASG in ${credentials}:${region} named ${asgName}")
    }
    AutoScalingGroup autoScalingGroup = result.autoScalingGroups()[0]
    List<String> instanceIds = autoScalingGroup.instances().instanceId

    task.updateStatus BASE_PHASE, "Force deleting $asgName in $region."
    autoScaling.deleteAutoScalingGroup(DeleteAutoScalingGroupRequest.builder()
        .autoScalingGroupName(asgName).forceDelete(true).build())

    Ec2Client ec2 = amazonClientProvider.getAmazonEC2V2(credentials, region)
    deleteLaunchSetting(autoScalingGroup, autoScaling, ec2, region)

    for (int i = 0; i < instanceIds.size(); i += MAX_SIMULTANEOUS_TERMINATIONS) {
      int end = Math.min(instanceIds.size(), i + MAX_SIMULTANEOUS_TERMINATIONS)
      try {
        task.updateStatus BASE_PHASE, "Issuing terminate instances request for ${end - i} instances."
        ec2.terminateInstances(TerminateInstancesRequest.builder().instanceIds(instanceIds.subList(i, end)).build())
      } catch (SdkClientException e) {
        task.updateStatus BASE_PHASE, "Unable to terminate instances, reason: '${e.message}'"
      }
    }
  }

  private void deleteLaunchSetting(
    AutoScalingGroup autoScalingGroup, AutoScalingClient autoScaling, Ec2Client amazonEC2, String region) {
    retrySupport.retry({
      if (autoScalingGroup.launchConfigurationName()) {
        String lcName = autoScalingGroup.launchConfigurationName()

        getTask().updateStatus BASE_PHASE, "Deleting launch config $lcName in $region."
        try {
          autoScaling.deleteLaunchConfiguration(
            DeleteLaunchConfigurationRequest.builder().launchConfigurationName(lcName).build())
        } catch (AutoScalingException e) {
          if (!e.message.toLowerCase().contains("launch configuration name not found")) {
            throw e
          }
        }
      } else if (autoScalingGroup.launchTemplate() || autoScalingGroup.mixedInstancesPolicy()) {
        String launchTemplateId = autoScalingGroup.launchTemplate()
          ? autoScalingGroup.launchTemplate().launchTemplateId()
          : autoScalingGroup.mixedInstancesPolicy()?.launchTemplate()?.launchTemplateSpecification().launchTemplateId()

        getTask().updateStatus BASE_PHASE, "Deleting launch template $launchTemplateId in $region."
        try {
          amazonEC2.deleteLaunchTemplate(
            DeleteLaunchTemplateRequest.builder().launchTemplateId(launchTemplateId).build())
        } catch (Ec2Exception e) {
          // Ignore not found exception
          if (!e.message.toLowerCase().contains("does not exist")) {
            throw e
          }
        }
      }
    }, 5, Duration.ofSeconds(1), true)
  }
}
