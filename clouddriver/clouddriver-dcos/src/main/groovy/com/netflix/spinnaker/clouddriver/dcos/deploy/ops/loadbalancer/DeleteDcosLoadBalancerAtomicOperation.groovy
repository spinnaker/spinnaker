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

package com.netflix.spinnaker.clouddriver.dcos.deploy.ops.loadbalancer

import com.netflix.spinnaker.clouddriver.data.task.Task
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository
import com.netflix.spinnaker.clouddriver.dcos.DcosClientProvider
import com.netflix.spinnaker.clouddriver.dcos.deploy.description.loadbalancer.DeleteDcosLoadBalancerAtomicOperationDescription
import com.netflix.spinnaker.clouddriver.dcos.deploy.util.id.DcosSpinnakerLbId
import com.netflix.spinnaker.clouddriver.dcos.deploy.util.monitor.DcosDeploymentMonitor
import com.netflix.spinnaker.clouddriver.dcos.exception.DcosOperationException
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation
import mesosphere.marathon.client.model.v2.App

class DeleteDcosLoadBalancerAtomicOperation implements AtomicOperation<Void> {
  private static final String BASE_PHASE = "DESTROY_LOAD_BALANCER"

  private final DcosClientProvider dcosClientProvider
  private final DcosDeploymentMonitor deploymentMonitor
  private final DeleteDcosLoadBalancerAtomicOperationDescription description

  DeleteDcosLoadBalancerAtomicOperation(DcosClientProvider dcosClientProvider, DcosDeploymentMonitor deploymentMonitor,
                                        DeleteDcosLoadBalancerAtomicOperationDescription description) {
    this.dcosClientProvider = dcosClientProvider
    this.deploymentMonitor = deploymentMonitor
    this.description = description
  }

  private static Task getTask() {
    TaskRepository.threadLocalTask.get()
  }

  @Override
  Void operate(List priorOutputs) {
    task.updateStatus BASE_PHASE, "Initializing delete of load balancer $description.loadBalancerName..."

    def dcosClient = dcosClientProvider.getDcosClient(description.credentials, description.dcosCluster)

    def appId = DcosSpinnakerLbId.from(description.credentials.account, description.loadBalancerName).get()

    App existingLb = dcosClient.maybeApp(appId.toString())
            .orElseThrow({
      throw new DcosOperationException("Unable to find an instance of load balancer with name $description.loadBalancerName")
    })

    dcosClient.deleteApp(existingLb.id)
    deploymentMonitor.waitForAppDestroy(dcosClient, appId.toString(), null, task, BASE_PHASE)

    task.updateStatus BASE_PHASE, "Successfully deleted load balancer $description.loadBalancerName."
  }
}
