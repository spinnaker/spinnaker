/*
 * Copyright 2017 Cerner Corporation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package com.netflix.spinnaker.clouddriver.dcos.deploy.ops.servergroup

import com.netflix.spinnaker.clouddriver.data.task.Task
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository
import com.netflix.spinnaker.clouddriver.dcos.DcosClientProvider
import com.netflix.spinnaker.clouddriver.dcos.deploy.description.servergroup.ResizeDcosServerGroupDescription
import com.netflix.spinnaker.clouddriver.dcos.deploy.util.id.DcosSpinnakerAppId
import com.netflix.spinnaker.clouddriver.dcos.deploy.util.monitor.DcosDeploymentMonitor
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation
import mesosphere.marathon.client.model.v2.App

class ResizeDcosServerGroupAtomicOperation implements AtomicOperation<Void> {
  private static final String BASE_PHASE = "RESIZE"

  private final DcosClientProvider dcosClientProvider
  private final DcosDeploymentMonitor deploymentMonitor
  private final ResizeDcosServerGroupDescription description

  ResizeDcosServerGroupAtomicOperation(DcosClientProvider dcosClientProvider,
                                       DcosDeploymentMonitor deploymentMonitor,
                                       ResizeDcosServerGroupDescription description) {
    this.dcosClientProvider = dcosClientProvider
    this.deploymentMonitor = deploymentMonitor
    this.description = description
  }

  private static Task getTask() {
    TaskRepository.threadLocalTask.get()
  }

  /*
   * curl -X POST -H "Content-Type: application/json" -d '[ { "resizeServerGroup": { "serverGroupName": "kub-test-v000", "capacity": { "desired": 7 }, "account": "my-kubernetes-account" }} ]' localhost:7002/kubernetes/ops
   */

  @Override
  Void operate(List priorOutputs) {
    task.updateStatus BASE_PHASE, "Initializing resize of server group $description.serverGroupName..."

    def dcosClient = dcosClientProvider.getDcosClient(description.credentials, description.dcosCluster)
    def appId = DcosSpinnakerAppId.fromVerbose(description.credentials.account, description.group, description.serverGroupName).get()
    def size = description.targetSize

    task.updateStatus BASE_PHASE, "Checking to see if $appId already exists..."

    def maybeApp = dcosClient.maybeApp(appId.toString())

    if (!maybeApp.present) {
      throw new RuntimeException("$appId does not exist in DCOS definitions.")
    }

    task.updateStatus BASE_PHASE, "Setting size to $size..."

    def app = new App()

    app.instances = description.targetSize

    def result = dcosClient.updateApp(appId.toString(), app, description.forceDeployment)
    def deploymentId = result.deploymentId

    task.updateStatus BASE_PHASE, "Waiting for $appId to be resized..."

    deploymentMonitor.waitForAppResize(dcosClient, appId.toString(), deploymentId, description.targetSize, null, task, BASE_PHASE)

    task.updateStatus BASE_PHASE, "Completed resize operation."
  }
}

