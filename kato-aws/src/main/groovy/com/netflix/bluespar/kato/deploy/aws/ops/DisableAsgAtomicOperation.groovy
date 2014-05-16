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
import com.amazonaws.services.autoscaling.model.SuspendProcessesRequest
import com.netflix.bluespar.kato.data.task.Task
import com.netflix.bluespar.kato.data.task.TaskRepository
import com.netflix.bluespar.kato.deploy.aws.description.DisableAsgDescription
import com.netflix.bluespar.kato.orchestration.AtomicOperation
import com.netflix.bluespar.kato.security.aws.AmazonClientProvider
import com.netflix.frigga.Names
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.web.client.RestTemplate

class DisableAsgAtomicOperation implements AtomicOperation<Void> {
  private static final String BASE_PHASE = "DELETE_ASG"

  private static Task getTask() {
    TaskRepository.threadLocalTask.get()
  }

  private final DisableAsgDescription description
  private RestTemplate restTemplate

  DisableAsgAtomicOperation(DisableAsgDescription description, RestTemplate restTemplate = new RestTemplate()) {
    this.description = description
    this.restTemplate = restTemplate
  }

  @Value('${discovery.host.format:#{null}}')
  String discoveryHostFormat

  @Autowired
  AmazonClientProvider amazonClientProvider

  @Override
  Void operate(List priorOutputs) {
    task.updateStatus BASE_PHASE, "Initializing Disable ASG operation for $description.asgName..."
    for (region in description.regions) {
      def autoScaling = amazonClientProvider.getAutoScaling(description.credentials, region)
      def result = autoScaling.describeAutoScalingGroups(new DescribeAutoScalingGroupsRequest().withAutoScalingGroupNames(description.asgName))
      if (!result.autoScalingGroups) {
        task.updateStatus BASE_PHASE, "No ASG named $description.asgName found in $region"
        continue
      }
      task.updateStatus BASE_PHASE, "Disabling Launch, Terminate, and AddToLoadBalancer for $description.asgName in $region."
      def request = new SuspendProcessesRequest().withScalingProcesses("Launch", "Terminate", "AddToLoadBalancer").withAutoScalingGroupName(description.asgName)
      autoScaling.suspendProcesses(request)
      if (discoveryHostFormat) {
        task.updateStatus BASE_PHASE, "Beginning discovery disable for $description.asgName"
        def asg = result.autoScalingGroups.getAt(0)
        def names = Names.parseName(asg.autoScalingGroupName)
        if (!names.app) {
          task.updateStatus BASE_PHASE, "! Couldn't figure out app name from ASG name. Can't disable in Discovery!"
        }
        for (instance in asg.instances) {
          task.updateStatus BASE_PHASE, " > Disabling $instance.instanceId"
          try {
            disableInDiscovery names.app, region, description.credentials.environment, instance.instanceId
          } catch (e) {
            e.printStackTrace()
            task.updateStatus BASE_PHASE, "  ! Couldn't disable $instance.instanceId in discovery! Reason: $e.message"
          }
        }
      }
    }
    task.updateStatus BASE_PHASE, "Done disabling ASG $description.asgName."
    null
  }

  void disableInDiscovery(String app, String env, String region, String instanceId) {
    def discovery = String.format(discoveryHostFormat, region, env)
    restTemplate.put("$discovery/eureka/v2/apps/$app/$instanceId/status?value=OUT_OF_SERVICE", [:])
  }
}
