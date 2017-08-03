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
import com.netflix.spinnaker.clouddriver.dcos.deploy.description.servergroup.DisableDcosServerGroupDescription
import com.netflix.spinnaker.clouddriver.dcos.deploy.description.servergroup.ResizeDcosServerGroupDescription
import com.netflix.spinnaker.clouddriver.dcos.deploy.util.id.DcosSpinnakerAppId
import com.netflix.spinnaker.clouddriver.dcos.deploy.util.monitor.DcosDeploymentMonitor
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation
import mesosphere.marathon.client.model.v2.App

class DisableDcosServerGroupAtomicOperation implements AtomicOperation<Void> {

  private static final String BASE_PHASE = "DISABLE"

  private final DcosClientProvider dcosClientProvider
  private final DcosDeploymentMonitor deploymentMonitor
  private final DisableDcosServerGroupDescription description

  DisableDcosServerGroupAtomicOperation(DcosClientProvider dcosClientProvider,
                                        DcosDeploymentMonitor deploymentMonitor,
                                        DisableDcosServerGroupDescription description) {
    this.dcosClientProvider = dcosClientProvider
    this.description = description
    this.deploymentMonitor = deploymentMonitor
  }

  private static Task getTask() {
    TaskRepository.threadLocalTask.get()
  }

  @Override
  Void operate(List priorOutputs) {
    task.updateStatus BASE_PHASE, "Initializing disable of server group $description.serverGroupName..."

    task.updateStatus BASE_PHASE, "Setting number of instances to 0 for $description.serverGroupName..."

    // TODO Most providers just take the instances out of load. Probably what we'd rather do.
    // TODO pull this out into a common place instead and reuse it both places? pass in BASE_PHASE
    def resizeDesc = new ResizeDcosServerGroupDescription().with {
      account = description.account
      credentials = description.credentials
      region = description.region
      dcosCluster = description.dcosCluster
      group = description.group
      serverGroupName = description.serverGroupName
      forceDeployment = description.forceDeployment
      targetSize = 0
      it
    }

    def resizeOp = new ResizeDcosServerGroupAtomicOperation(dcosClientProvider, deploymentMonitor, resizeDesc)
    resizeOp.operate([])
  }
}
