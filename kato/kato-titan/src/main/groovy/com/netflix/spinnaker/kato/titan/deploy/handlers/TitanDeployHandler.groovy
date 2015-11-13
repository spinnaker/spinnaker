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

package com.netflix.spinnaker.kato.titan.deploy.handlers
import com.netflix.spinnaker.kato.data.task.Task
import com.netflix.spinnaker.kato.data.task.TaskRepository
import com.netflix.spinnaker.kato.deploy.DeployDescription
import com.netflix.spinnaker.kato.deploy.DeployHandler
import com.netflix.spinnaker.kato.deploy.DeploymentResult
import com.netflix.spinnaker.clouddriver.titan.TitanClientProvider
import com.netflix.spinnaker.kato.titan.model.DockerImage
import com.netflix.spinnaker.kato.titan.deploy.TitanServerGroupNameResolver
import com.netflix.spinnaker.kato.titan.deploy.description.TitanDeployDescription
import com.netflix.titanclient.TitanClient
import com.netflix.titanclient.model.SubmitJobRequest

class TitanDeployHandler implements DeployHandler<TitanDeployDescription> {

  private static final String BASE_PHASE = "DEPLOY"

  private final TitanClientProvider titanClientProvider

  TitanDeployHandler(TitanClientProvider titanClientProvider) {
    this.titanClientProvider = titanClientProvider
  }

  private static Task getTask() {
    TaskRepository.threadLocalTask.get()
  }

  @Override
  DeploymentResult handle(TitanDeployDescription description, List priorOutputs) {

    task.updateStatus BASE_PHASE, "Initializing handler..."
    TitanClient titanClient = titanClientProvider.getTitanClient(description.credentials, description.region)
    DeploymentResult deploymentResult = new DeploymentResult()
    String account = description.account
    String region = description.region
    String subnet = description.subnet

    task.updateStatus BASE_PHASE, "Preparing deployment to ${account}:${region}${subnet ? ':' + subnet : ''}..."
    DockerImage dockerImage = new DockerImage(description.imageId)

    TitanServerGroupNameResolver serverGroupNameResolver = new TitanServerGroupNameResolver(titanClient)
    String nextServerGroupName = serverGroupNameResolver.resolveNextServerGroupName(description.application, description.stack, description.freeFormDetails, false)
    task.updateStatus BASE_PHASE, "Resolved server group name to ${nextServerGroupName}"

    if (!description.env) description.env = [:]
    description.env << [source: "spinnaker"]  // TODO: TEMPORARY param so that Titan calypso service can filter Spinnaker tasks/jobs out of millions launched by other systems (Dagobah, Meson, etc.)

    SubmitJobRequest submitJobRequest = new SubmitJobRequest()
      .withJobName(nextServerGroupName)
      .withApplication(description.application)
      .withDockerImageName(dockerImage.imageName)
      .withDockerImageVersion(dockerImage.imageVersion)
      .withInstances(description.capacity.desired)
      .withCpu(description.resources.cpu)
      .withMemory(description.resources.memory)
      .withDisk(description.resources.disk)
      .withPorts(description.resources.ports)
      .withEnv(description.env)
      .withAllocateIpAddress(description.resources.allocateIpAddress)

    task.updateStatus BASE_PHASE, "Submitting job request to Titan..."
    String jobUri = titanClient.submitJob(submitJobRequest)

    task.updateStatus BASE_PHASE, "Successfully submitted job request to Titan (Job URI: ${jobUri})"

    deploymentResult.serverGroupNames = ["${region}:${nextServerGroupName}".toString()]
    deploymentResult.serverGroupNameByRegion = [(description.region): nextServerGroupName]
    deploymentResult.messages = task.history.collect { "${it.phase} : ${it.status}".toString() }

    return deploymentResult
  }

  @Override
  boolean handles(DeployDescription description) {
    return description instanceof TitanDeployDescription
  }
}
