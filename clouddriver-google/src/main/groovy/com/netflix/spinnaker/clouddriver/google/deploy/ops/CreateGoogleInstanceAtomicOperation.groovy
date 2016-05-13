/*
 * Copyright 2015 Google, Inc.
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

package com.netflix.spinnaker.clouddriver.google.deploy.ops

import com.google.api.services.compute.model.Instance
import com.netflix.spinnaker.clouddriver.data.task.Task
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository
import com.netflix.spinnaker.clouddriver.deploy.DeploymentResult
import com.netflix.spinnaker.clouddriver.google.GoogleConfiguration
import com.netflix.spinnaker.clouddriver.google.config.GoogleConfigurationProperties
import com.netflix.spinnaker.clouddriver.google.deploy.GCEUtil
import com.netflix.spinnaker.clouddriver.google.deploy.description.CreateGoogleInstanceDescription
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation
import org.springframework.beans.factory.annotation.Autowired

class CreateGoogleInstanceAtomicOperation implements AtomicOperation<DeploymentResult> {
  private static final String BASE_PHASE = "DEPLOY"

  // TODO(duftler): These should be exposed/configurable.
  private static final String DEFAULT_NETWORK_NAME = "default"
  private static final String accessConfigName = "External NAT"
  private static final String accessConfigType = "ONE_TO_ONE_NAT"

  @Autowired
  private GoogleConfigurationProperties googleConfigurationProperties

  @Autowired
  private GoogleConfiguration.DeployDefaults googleDeployDefaults

  @Autowired
  String googleApplicationName

  private static Task getTask() {
    TaskRepository.threadLocalTask.get()
  }

  private final CreateGoogleInstanceDescription description

  CreateGoogleInstanceAtomicOperation(CreateGoogleInstanceDescription description) {
    this.description = description
  }

  /**
   * curl -X POST -H "Content-Type: application/json" -d '[ { "createGoogleInstanceDescription": { "instanceName": "myapp-dev-v000-abcd", "image": "ubuntu-1404-trusty-v20160509a", "instanceType": "f1-micro", "zone": "us-central1-f", "credentials": "my-account-name" }} ]' localhost:7002/ops
   */
  @Override
  DeploymentResult operate(List priorOutputs) {
    task.updateStatus BASE_PHASE, "Initializing creation of instance $description.instanceName in $description.zone..."

    if (!description.credentials) {
      throw new IllegalArgumentException("Unable to resolve credentials for Google account '${description.accountName}'.")
    }

    def compute = description.credentials.compute
    def project = description.credentials.project
    def zone = description.zone
    def region = GCEUtil.getRegionFromZone(project, zone, compute)

    def machineType = GCEUtil.queryMachineType(project, description.instanceType, compute, task, BASE_PHASE)

    def sourceImage = GCEUtil.querySourceImage(project,
                                               description,
                                               compute,
                                               task,
                                               BASE_PHASE,
                                               googleApplicationName,
                                               googleConfigurationProperties.baseImageProjects)

    def network = GCEUtil.queryNetwork(project, description.network ?: DEFAULT_NETWORK_NAME, compute, task, BASE_PHASE)

    def subnet =
      description.subnet ? GCEUtil.querySubnet(project, region, description.subnet, compute, task, BASE_PHASE) : null

    task.updateStatus BASE_PHASE, "Composing instance..."

    def attachedDisks = GCEUtil.buildAttachedDisks(project,
                                                   zone,
                                                   sourceImage,
                                                   description.disks,
                                                   true,
                                                   description.instanceType,
                                                   googleDeployDefaults)

    def networkInterface = GCEUtil.buildNetworkInterface(network, subnet, accessConfigName, accessConfigType)

    def metadata = GCEUtil.buildMetadataFromMap(description.instanceMetadata)

    def tags = GCEUtil.buildTagsFromList(description.tags)

    def serviceAccount = GCEUtil.buildServiceAccount(description.authScopes)

    def scheduling = GCEUtil.buildScheduling(description)

    def instance = new Instance(name: description.instanceName,
                                machineType: "zones/$zone/machineTypes/$machineType.name",
                                disks: attachedDisks,
                                networkInterfaces: [networkInterface],
                                metadata: metadata,
                                tags: tags,
                                scheduling: scheduling,
                                serviceAccounts: [serviceAccount])

    task.updateStatus BASE_PHASE, "Creating instance $description.instanceName..."
    compute.instances().insert(project, zone, instance).execute()

    task.updateStatus BASE_PHASE, "Done creating instance $description.instanceName in $description.zone."
    new DeploymentResult(serverGroupNames: [description.instanceName])
  }
}
