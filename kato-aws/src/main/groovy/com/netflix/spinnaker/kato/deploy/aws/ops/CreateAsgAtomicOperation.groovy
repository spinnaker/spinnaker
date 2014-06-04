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

import com.amazonaws.services.autoscaling.model.CreateAutoScalingGroupRequest
import com.amazonaws.services.autoscaling.model.SuspendProcessesRequest
import com.netflix.amazoncomponents.security.AmazonClientProvider
import com.netflix.spinnaker.kato.data.task.Task
import com.netflix.spinnaker.kato.data.task.TaskRepository
import com.netflix.spinnaker.kato.deploy.aws.description.CreateAsgDescription
import com.netflix.spinnaker.kato.deploy.aws.handlers.BasicAmazonDeployHandler
import com.netflix.spinnaker.kato.orchestration.AtomicOperation
import com.netflix.spinnaker.kato.model.aws.AutoScalingGroupOptions
import com.netflix.spinnaker.kato.model.aws.Subnets
import org.springframework.beans.factory.annotation.Autowired

class CreateAsgAtomicOperation implements AtomicOperation<Void> {
  private static final String BASE_PHASE = "CREATE_ASG"

  private static Task getTask() {
    TaskRepository.threadLocalTask.get()
  }

  @Autowired
  BasicAmazonDeployHandler basicAmazonDeployHandler

  @Autowired
  AmazonClientProvider amazonClientProvider

  final CreateAsgDescription description

  CreateAsgAtomicOperation(CreateAsgDescription description) {
    this.description = description
  }

  @Override
  Void operate(List priorOutputs) {
    task.updateStatus BASE_PHASE, "Initializing Create ASG Operation..."
    for (String region : description.regions) {
      def asgOptions = description.asgOptions
      def autoScalingGroupWithNoInstances = AutoScalingGroupOptions.from(asgOptions)
      autoScalingGroupWithNoInstances.with {
        minSize = 0
        desiredCapacity = 0
      }
      def amazonEc2 = amazonClientProvider.getAmazonEC2(description.credentials, region)
      def autoScaling = amazonClientProvider.getAutoScaling(description.credentials, region)
      String defaultVpc = amazonEc2.describeVpcs().vpcs.find { it.isDefault }?.vpcId
      def subnets = Subnets.from(amazonEc2.describeSubnets().subnets, defaultVpc)
      CreateAutoScalingGroupRequest request = asgOptions.getCreateAutoScalingGroupRequest(subnets)
      autoScaling.createAutoScalingGroup(request)
      SuspendProcessesRequest suspendProcessesRequest = new SuspendProcessesRequest(
        autoScalingGroupName: asgOptions.autoScalingGroupName,
        scalingProcesses: asgOptions.suspendedProcesses.collect { it.name() }
      )
      autoScaling.suspendProcesses(suspendProcessesRequest)
    }
    null
  }

}
