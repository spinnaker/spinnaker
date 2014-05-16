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

package com.netflix.bluespar.kato.deploy.aws.handlers

import com.netflix.bluespar.kato.data.task.Task
import com.netflix.bluespar.kato.data.task.TaskRepository
import com.netflix.bluespar.kato.deploy.DeployDescription
import com.netflix.bluespar.kato.deploy.DeployHandler
import com.netflix.bluespar.kato.deploy.DeploymentResult
import com.netflix.bluespar.kato.deploy.aws.AutoScalingWorker
import com.netflix.bluespar.kato.deploy.aws.description.BasicAmazonDeployDescription
import com.netflix.bluespar.kato.deploy.aws.ops.loadbalancer.CreateAmazonLoadBalancerResult
import com.netflix.bluespar.kato.deploy.aws.userdata.UserDataProvider
import com.netflix.bluespar.kato.security.aws.AmazonClientProvider
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

@Component
class BasicAmazonDeployHandler implements DeployHandler<BasicAmazonDeployDescription> {
  private static final String BASE_PHASE = "DEPLOY"

  private static Task getTask() {
    TaskRepository.threadLocalTask.get()
  }

  @Autowired
  List<UserDataProvider> userDataProviders

  @Autowired
  AmazonClientProvider amazonClientProvider

  @Override
  boolean handles(DeployDescription description) {
    description instanceof BasicAmazonDeployDescription
  }

  @Override
  DeploymentResult handle(BasicAmazonDeployDescription description, List priorOutputs) {
    task.updateStatus BASE_PHASE, "Initializing handler..."
    def deploymentResult = new DeploymentResult()
    task.updateStatus BASE_PHASE, "Preparing deployment to ${description.availabilityZones}..."
    for (Map.Entry<String, List<String>> entry : description.availabilityZones) {
      String region = entry.key
      List<String> availabilityZones = entry.value

      // Get the properly typed version of the description's subnetType
      def subnetType = description.subnetType ? AutoScalingWorker.SubnetType.fromString(description.subnetType) : null

      // Get the list of load balancers that were created as part of this conglomerate job to apply to the ASG.
      List<CreateAmazonLoadBalancerResult.LoadBalancer> suppliedLoadBalancers = (List<CreateAmazonLoadBalancerResult.LoadBalancer>) priorOutputs.findAll {
        it instanceof CreateAmazonLoadBalancerResult
      }?.loadBalancers?.getAt(region)

      if (!description.loadBalancers) {
        description.loadBalancers = []
      }
      description.loadBalancers.addAll suppliedLoadBalancers?.name

      def amazonEC2 = amazonClientProvider.getAmazonEC2(description.credentials, region)
      def autoScaling = amazonClientProvider.getAutoScaling(description.credentials, region)

      def autoScalingWorker = new AutoScalingWorker(
        application: description.application,
        region: region,
        environment: description.credentials.environment,
        stack: description.stack,
        ami: description.amiName,
        minInstances: description.capacity.min,
        maxInstances: description.capacity.max,
        desiredInstances: description.capacity.desired,
        securityGroups: description.securityGroups,
        instanceType: description.instanceType,
        availabilityZones: availabilityZones,
        subnetType: subnetType,
        amazonEC2: amazonEC2,
        autoScaling: autoScaling,
        loadBalancers: description.loadBalancers,
        userDataProviders: userDataProviders
      )

      def asgName = autoScalingWorker.deploy()

      deploymentResult.serverGroupNames << "${region}:${asgName}"
    }

    deploymentResult
  }
}
