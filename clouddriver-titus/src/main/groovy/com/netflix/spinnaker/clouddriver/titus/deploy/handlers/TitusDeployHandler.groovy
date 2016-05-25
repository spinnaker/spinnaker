/*
 * Copyright 2015 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
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

package com.netflix.spinnaker.clouddriver.titus.deploy.handlers
import com.netflix.spinnaker.clouddriver.data.task.Task
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository
import com.netflix.spinnaker.clouddriver.deploy.DeployDescription
import com.netflix.spinnaker.clouddriver.deploy.DeployHandler
import com.netflix.spinnaker.clouddriver.deploy.DeploymentResult
import com.netflix.spinnaker.clouddriver.titus.TitusClientProvider
import com.netflix.spinnaker.clouddriver.titus.model.DockerImage
import com.netflix.spinnaker.clouddriver.titus.deploy.TitusServerGroupNameResolver
import com.netflix.spinnaker.clouddriver.titus.deploy.description.TitusDeployDescription
import com.netflix.spinnaker.clouddriver.titus.client.TitusClient
import com.netflix.spinnaker.clouddriver.titus.client.model.SubmitJobRequest
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class TitusDeployHandler implements DeployHandler<TitusDeployDescription> {

  private final Logger logger = LoggerFactory.getLogger(TitusDeployHandler)

  private static final String BASE_PHASE = "DEPLOY"

  private final TitusClientProvider titusClientProvider

  TitusDeployHandler(TitusClientProvider titusClientProvider) {
    this.titusClientProvider = titusClientProvider
  }

  private static Task getTask() {
    TaskRepository.threadLocalTask.get()
  }

  @Override
  DeploymentResult handle(TitusDeployDescription description, List priorOutputs) {

    try {
      task.updateStatus BASE_PHASE, "Initializing handler..."
      TitusClient titusClient = titusClientProvider.getTitusClient(description.credentials, description.region)
      DeploymentResult deploymentResult = new DeploymentResult()
      String account = description.account
      String region = description.region
      String subnet = description.subnet

      task.updateStatus BASE_PHASE, "Preparing deployment to ${account}:${region}${subnet ? ':' + subnet : ''}..."
      DockerImage dockerImage = new DockerImage(description.imageId)

      TitusServerGroupNameResolver serverGroupNameResolver = new TitusServerGroupNameResolver(titusClient, description.region)
      String nextServerGroupName = serverGroupNameResolver.resolveNextServerGroupName(description.application, description.stack, description.freeFormDetails, false)
      task.updateStatus BASE_PHASE, "Resolved server group name to ${nextServerGroupName}"

      if (!description.env) description.env = [:]
      if (!description.labels) description.labels = [:]

      SubmitJobRequest submitJobRequest = new SubmitJobRequest()
        .withJobName(nextServerGroupName)
        .withApplication(description.application)
        .withDockerImageName(dockerImage.imageName)
        .withDockerImageVersion(dockerImage.imageVersion)
        .withInstancesMin(description.capacity.min)
        .withInstancesMax(description.capacity.max)
        .withInstancesDesired(description.capacity.desired)
        .withCpu(description.resources.cpu)
        .withMemory(description.resources.memory)
        .withDisk(description.resources.disk)
        .withPorts(description.resources.ports)
        .withEnv(description.env)
        .withAllocateIpAddress(description.resources.allocateIpAddress)
        .withStack(description.stack)
        .withDetail(description.freeFormDetails)
        .withConstraint(SubmitJobRequest.Constraint.soft(SubmitJobRequest.Constraint.ZONE_BALANCE))
        .withEntryPoint(description.entryPoint)
        .withIamProfile(description.iamProfile)
        .withLabels(description.labels)

      if(description.securityGroups && !description.securityGroups.empty) {
        submitJobRequest.withSecurityGroups(description.securityGroups)
      }

      if(description.user){
        submitJobRequest.withUser(description.user)
      }

      task.updateStatus BASE_PHASE, "Submitting job request to Titus..."
      String jobUri = titusClient.submitJob(submitJobRequest)

      task.updateStatus BASE_PHASE, "Successfully submitted job request to Titus (Job URI: ${jobUri})"

      deploymentResult.serverGroupNames = ["${region}:${nextServerGroupName}".toString()]
      deploymentResult.serverGroupNameByRegion = [(description.region): nextServerGroupName]
      deploymentResult.messages = task.history.collect { "${it.phase} : ${it.status}".toString() }

      return deploymentResult
    } catch (t) {
      task.updateStatus(BASE_PHASE, "Task failed $t.message")
      task.fail()
      logger.error("Deploy failed", t)
      throw t
    }
  }

  @Override
  boolean handles(DeployDescription description) {
    return description instanceof TitusDeployDescription
  }
}
