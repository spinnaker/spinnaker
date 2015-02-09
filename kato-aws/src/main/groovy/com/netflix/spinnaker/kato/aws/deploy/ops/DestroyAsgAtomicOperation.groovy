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
import com.amazonaws.services.autoscaling.model.DeleteAutoScalingGroupRequest
import com.amazonaws.services.autoscaling.model.DeleteLaunchConfigurationRequest
import com.amazonaws.services.autoscaling.model.DescribeAutoScalingGroupsRequest
import com.amazonaws.services.elasticloadbalancing.model.DeregisterInstancesFromLoadBalancerRequest
import com.amazonaws.services.elasticloadbalancing.model.Instance
import com.netflix.amazoncomponents.security.AmazonClientProvider
import com.netflix.spinnaker.kato.aws.deploy.description.DestroyAsgDescription
import com.netflix.spinnaker.kato.data.task.Task
import com.netflix.spinnaker.kato.data.task.TaskRepository
import com.netflix.spinnaker.kato.orchestration.AtomicOperation
import org.springframework.beans.factory.annotation.Autowired

class DestroyAsgAtomicOperation implements AtomicOperation<Void> {
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
    task.updateStatus BASE_PHASE, "Initializing ASG Destroy Operation..."
    for (region in description.regions) {
      def autoScaling = amazonClientProvider.getAutoScaling(description.credentials, region)
      task.updateStatus BASE_PHASE, "Looking up instance ids for $description.asgName in $region..."

      def result = autoScaling.describeAutoScalingGroups(
              new DescribeAutoScalingGroupsRequest(autoScalingGroupNames: [description.asgName]))
      if (!result.autoScalingGroups) {
        return null // Okay, there is no auto scaling group. Let's be idempotent and not complain about that.
      }
      if (result.autoScalingGroups.size() > 1) {
        throw new IllegalStateException(
                "There should only be one ASG in ${description.credentials}:${region} named ${description.asgName}")
      }
      AutoScalingGroup autoScalingGroup = result.autoScalingGroups[0]

      // Deregister instances from ELB. This avoids an AWS traffic routing bug that may no longer be a problem.
      def elbClient = amazonClientProvider.getAmazonElasticLoadBalancing(description.credentials, region)
      List<String> loadBalancerNames = autoScalingGroup.loadBalancerNames
      for (String loadBalancerName in loadBalancerNames) {
        task.updateStatus BASE_PHASE, "Deregistering instances from load balancer ${loadBalancerName}"
        List<Instance> instances = autoScalingGroup.instances*.instanceId.collect { new Instance(instanceId: it) }
        def request = new DeregisterInstancesFromLoadBalancerRequest(
                loadBalancerName: loadBalancerName, instances: instances)
        elbClient.deregisterInstancesFromLoadBalancer(request)
      }

      task.updateStatus BASE_PHASE, "Force deleting $description.asgName in $region."
      autoScaling.deleteAutoScalingGroup(new DeleteAutoScalingGroupRequest(
              autoScalingGroupName: description.asgName, forceDelete: true))

      task.updateStatus BASE_PHASE, "Deleting launch config ${autoScalingGroup.launchConfigurationName} in $region."
      autoScaling.deleteLaunchConfiguration(new DeleteLaunchConfigurationRequest(
              launchConfigurationName: autoScalingGroup.launchConfigurationName))
    }

    task.updateStatus BASE_PHASE, "Done destroying $description.asgName in $description.regions."
    null
  }

}
