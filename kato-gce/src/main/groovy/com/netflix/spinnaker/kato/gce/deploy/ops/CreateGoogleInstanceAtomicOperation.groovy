/*
 * Copyright 2014 Google, Inc.
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

package com.netflix.spinnaker.kato.gce.deploy.ops

import com.google.api.services.compute.model.Instance
import com.netflix.spinnaker.kato.config.GceConfig
import com.netflix.spinnaker.kato.data.task.Task
import com.netflix.spinnaker.kato.data.task.TaskRepository
import com.netflix.spinnaker.kato.deploy.DeploymentResult
import com.netflix.spinnaker.kato.gce.deploy.GCEUtil
import com.netflix.spinnaker.kato.gce.deploy.description.CreateGoogleInstanceDescription
import com.netflix.spinnaker.kato.orchestration.AtomicOperation
import org.springframework.beans.factory.annotation.Autowired

class CreateGoogleInstanceAtomicOperation implements AtomicOperation<DeploymentResult> {
  private static final String BASE_PHASE = "DEPLOY"

  // TODO(duftler): These should be exposed/configurable.
  private static final String networkName = "default"
  private static final String accessConfigName = "External NAT"
  private static final String accessConfigType = "ONE_TO_ONE_NAT"

  @Autowired
  private GceConfig.DeployDefaults gceDeployDefaults

  private static Task getTask() {
    TaskRepository.threadLocalTask.get()
  }

  private final CreateGoogleInstanceDescription description

  CreateGoogleInstanceAtomicOperation(CreateGoogleInstanceDescription description) {
    this.description = description
  }

  /**
   * curl -X POST -H "Content-Type: application/json" -d '[ { "createGoogleInstanceDescription": { "instanceName": "myapp-dev-v000-abcd", "image": "debian-7-wheezy-v20141108", "instanceType": "f1-micro", "zone": "us-central1-b", "credentials": "my-account-name" }} ]' localhost:8501/ops
   */
  @Override
  DeploymentResult operate(List priorOutputs) {
    task.updateStatus BASE_PHASE, "Initializing deployment..."

    if (!description.credentials) {
      throw new IllegalArgumentException("Unable to resolve credentials for Google account '${description.accountName}'.")
    }

    def compute = description.credentials.compute
    def project = description.credentials.project
    def zone = description.zone

    def machineType = GCEUtil.queryMachineType(project, zone, description.instanceType, compute, task, BASE_PHASE)

    def sourceImage = GCEUtil.querySourceImage(project, description.image, compute, task, BASE_PHASE)

    def network = GCEUtil.queryNetwork(project, networkName, compute, task, BASE_PHASE)

    task.updateStatus BASE_PHASE, "Composing instance..."

    def rootDrive = GCEUtil.buildAttachedDisk(project,
                                              zone,
                                              sourceImage,
                                              description.diskSizeGb,
                                              description.diskType,
                                              true,
                                              description.instanceType,
                                              gceDeployDefaults)

    def networkInterface = GCEUtil.buildNetworkInterface(network, accessConfigName, accessConfigType)

    def instance = new Instance(name: description.instanceName,
                                machineType: machineType.getSelfLink(),
                                disks: [rootDrive],
                                networkInterfaces: [networkInterface])

    task.updateStatus BASE_PHASE, "Creating instance $description.instanceName..."
    compute.instances().insert(project, zone, instance).execute()
    task.updateStatus BASE_PHASE, "Done."
    new DeploymentResult(serverGroupNames: [description.instanceName])
  }
}
