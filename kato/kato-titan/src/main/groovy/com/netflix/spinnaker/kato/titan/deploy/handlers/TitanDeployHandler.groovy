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
import com.netflix.spinnaker.kato.titan.TitanClientProvider
import com.netflix.spinnaker.kato.titan.model.DockerImage
import com.netflix.spinnaker.kato.titan.deploy.TitanServerGroupNameResolver
import com.netflix.spinnaker.kato.titan.deploy.description.TitanDeployDescription
import com.netflix.titanclient.TitanClient
import com.netflix.titanclient.model.SubmitJobRequest
/**
 * @author sthadeshwar
 */
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
    TitanClient titanClient = titanClientProvider.getTitanClient(description.source.account, description.source.region)
    DeploymentResult deploymentResult = new DeploymentResult()
    String account = description.source.account
    String region = description.source.region
    String subnetType = description.subnetType

    task.updateStatus BASE_PHASE, "Preparing deployment to ${account}:${region}:${subnetType}..."
    DockerImage dockerImage = DockerImage.DockerImageResolver.resolveImage(description.dockerImage)

    TitanServerGroupNameResolver serverGroupNameResolver = new TitanServerGroupNameResolver(titanClient, description.source.account, description.subnetType)
    String nextServerGroupName = serverGroupNameResolver.resolveNextServerGroupName(description.application, description.stack, description.details)
    task.updateStatus BASE_PHASE, "Resolved server group name to ${nextServerGroupName}"

    SubmitJobRequest submitJobRequest = new SubmitJobRequest()
      .withName(nextServerGroupName)
      .withApplication(description.application)
      .withImageName(dockerImage.imageName)
      .withImageName(dockerImage.imageVersion)
      .withEntryPoint(description.entryPoint)
      .withInstances(description.capacity.desired)
      .withCpu(description.cpu)
      .withMemory(description.memory)
      .withDisk(description.disk)
      .withPorts(description.ports)
      .withEnv(description.env)
      .withRetries(description.retries)
      .withRestartOnSuccess(description.restartOnSuccess)

    task.updateStatus BASE_PHASE, "Submitting job request to Titan..."
    String jobUri = titanClient.submitJob(submitJobRequest)

    task.updateStatus BASE_PHASE, "Successfully submitted job request to Titan (Job URI: ${jobUri})"

    deploymentResult.serverGroupNames = [nextServerGroupName]
    deploymentResult.serverGroupNameByRegion = [(description.source.region): nextServerGroupName]
    deploymentResult.messages = task.history.collect { "${it.phase} : ${it.status}" }

    return deploymentResult
  }

  @Override
  boolean handles(DeployDescription description) {
    return description instanceof TitanDeployDescription
  }
}
