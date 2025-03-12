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

package com.netflix.spinnaker.clouddriver.dcos.deploy.ops.instance

import com.netflix.spinnaker.clouddriver.data.task.Task
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository
import com.netflix.spinnaker.clouddriver.dcos.DcosClientProvider
import com.netflix.spinnaker.clouddriver.dcos.deploy.description.instance.TerminateDcosInstancesAndDecrementDescription
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation
import mesosphere.marathon.client.model.v2.DeleteTaskCriteria

class TerminateDcosInstancesAndDecrementAtomicOperation implements AtomicOperation<Void> {
  private static final String BASE_PHASE = "TERMINATE_AND_DECREMENT"

  final DcosClientProvider dcosClientProvider
  final TerminateDcosInstancesAndDecrementDescription description

  TerminateDcosInstancesAndDecrementAtomicOperation(DcosClientProvider dcosClientProvider, TerminateDcosInstancesAndDecrementDescription description) {
    this.dcosClientProvider = dcosClientProvider
    this.description = description
  }

  private static Task getTask() {
    TaskRepository.threadLocalTask.get()
  }

  /*
   * curl -X POST -H "Content-Type: application/json" -d '[ { "terminateInstanceAndDecrementServerGroup": { "appId": "dcos-test-v000", "hostId": "192.168.10.100", "account": "my-dcos-account" }} ]' localhost:7002/dcos/ops
   * curl -X POST -H "Content-Type: application/json" -d '[ { "terminateInstanceAndDecrementServerGroup": { "appId": "dcos-test-v000", "hostId": "192.168.10.100", "force": true, "account": "my-dcos-account" }} ]' localhost:7002/dcos/ops
   * curl -X POST -H "Content-Type: application/json" -d '[ { "terminateInstanceAndDecrementServerGroup": { "appId": "dcos-test-v000", "taskIds": ["dcos-test-v000.asdfkjashdfkjashd"], "account": "my-dcos-account" }} ]' localhost:7002/dcos/ops
   * curl -X POST -H "Content-Type: application/json" -d '[ { "terminateInstanceAndDecrementServerGroup": { "appId": "dcos-test-v000", "taskIds": ["dcos-test-v000.asdfkjashdfkjashd"], "force": true, "account": "my-dcos-account" }} ]' localhost:7002/dcos/ops
   * curl -X POST -H "Content-Type: application/json" -d '[ { "terminateInstanceAndDecrementServerGroup": { "taskIds": ["dcos-test-v000.asdf1", "dcos-test-v000.asdf2"], "account": "my-dcos-account" }} ]' localhost:7002/dcos/ops
   * curl -X POST -H "Content-Type: application/json" -d '[ { "terminateInstanceAndDecrementServerGroup": { "taskIds": ["dcos-test-v000.asdf1", "dcos-test-v000.asdf2"], "force": true, "account": "my-dcos-account" }} ]' localhost:7002/dcos/ops
   */

  @Override
  Void operate(List priorOutputs) {
    task.updateStatus BASE_PHASE, "Initializing termination of instances..."

    def dcosClient = dcosClientProvider.getDcosClient(description.credentials, description.dcosCluster)

    def deleteTaskCriteria = new DeleteTaskCriteria().with {
      ids = description.instanceIds
      it
    }

    dcosClient.deleteTaskAndScale(false, deleteTaskCriteria)

    task.updateStatus BASE_PHASE, "Completed termination operation."
  }
}
