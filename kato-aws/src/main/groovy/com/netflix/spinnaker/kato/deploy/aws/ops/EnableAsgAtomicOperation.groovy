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
package com.netflix.spinnaker.kato.deploy.aws.ops

import com.amazonaws.services.autoscaling.model.DescribeAutoScalingGroupsRequest
import com.amazonaws.services.autoscaling.model.ResumeProcessesRequest
import com.amazonaws.services.elasticloadbalancing.model.Instance
import com.amazonaws.services.elasticloadbalancing.model.RegisterInstancesWithLoadBalancerRequest
import com.netflix.amazoncomponents.security.AmazonClientProvider
import com.netflix.frigga.Names
import com.netflix.spinnaker.kato.data.task.Task
import com.netflix.spinnaker.kato.data.task.TaskRepository
import com.netflix.spinnaker.kato.deploy.aws.description.EnableAsgDescription
import com.netflix.spinnaker.kato.model.aws.AutoScalingProcessType
import com.netflix.spinnaker.kato.orchestration.AtomicOperation
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.web.client.RestTemplate

class EnableAsgAtomicOperation implements AtomicOperation<Void> {
  private static final String BASE_PHASE = "ENABLE_ASG"

  private static Task getTask() {
    TaskRepository.threadLocalTask.get()
  }

  private final EnableAsgDescription description
  private RestTemplate restTemplate

  EnableAsgAtomicOperation(EnableAsgDescription description, RestTemplate restTemplate = new RestTemplate()) {
    this.description = description
    this.restTemplate = restTemplate
  }

  @Value('${discovery.host.format:#{null}}')
  String discoveryHostFormat

  @Autowired
  AmazonClientProvider amazonClientProvider

  @Override
  Void operate(List priorOutputs) {
    task.updateStatus BASE_PHASE, "Initializing Enable ASG operation for $description.asgName..."
    for (region in description.regions) {
      def autoScaling = amazonClientProvider.getAutoScaling(description.credentials, region)
      def loadBalancing = amazonClientProvider.getAmazonElasticLoadBalancing(description.credentials, region)

      def result = autoScaling.describeAutoScalingGroups(new DescribeAutoScalingGroupsRequest().withAutoScalingGroupNames(description.asgName))
      if (!result.autoScalingGroups) {
        task.updateStatus BASE_PHASE, "No ASG named $description.asgName found in $region"
        continue
      }

      task.updateStatus BASE_PHASE, "Registering instances with Load Balancers..."
      def asg = result.autoScalingGroups.getAt(0)
      if (asg.loadBalancerNames) {
        def lbInstances = asg.instances.collect { new Instance().withInstanceId(it.instanceId) }
        for (loadBalancerName in asg.loadBalancerNames) {
          loadBalancing.registerInstancesWithLoadBalancer(new RegisterInstancesWithLoadBalancerRequest(loadBalancerName, lbInstances))
        }
      }

      List<String> disableProcessNames = AutoScalingProcessType.getDisableProcesses()*.name().sort()
      task.updateStatus BASE_PHASE, "Enabling processes (${disableProcessNames.join(", ")}) for $description.asgName in $region."
      def request = new ResumeProcessesRequest().withScalingProcesses(disableProcessNames).withAutoScalingGroupName(description.asgName)
      autoScaling.resumeProcesses(request)
      if (discoveryHostFormat) {
        task.updateStatus BASE_PHASE, "Beginning discovery enable for $description.asgName"
        def names = Names.parseName(asg.autoScalingGroupName)
        if (!names.app) {
          task.updateStatus BASE_PHASE, "! Couldn't figure out app name from ASG name. Can't enable in Discovery!"
        }
        for (instance in asg.instances) {
          task.updateStatus BASE_PHASE, " > Enabling $instance.instanceId"
          try {
            enableInDiscovery names.app, region, description.credentials.environment, instance.instanceId
          } catch (e) {
            e.printStackTrace()
            task.updateStatus BASE_PHASE, "  ! Couldn't enable $instance.instanceId in discovery! Reason: $e.message"
          }
        }
      }
    }
    task.updateStatus BASE_PHASE, "Done enabling ASG $description.asgName."
    null
  }

  void enableInDiscovery(String app, String env, String region, String instanceId) {
    def discovery = String.format(discoveryHostFormat, region, env)
    restTemplate.put("$discovery/eureka/v2/apps/$app/$instanceId/status?value=UP", [:])
  }
}
