/*
 * Copyright 2015-2016 Pivotal Inc.
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

package com.netflix.spinnaker.clouddriver.cf.deploy.ops

import com.netflix.spinnaker.clouddriver.data.task.Task
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository
import com.netflix.spinnaker.clouddriver.helpers.OperationPoller
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation
import com.netflix.spinnaker.clouddriver.cf.deploy.description.DestroyCloudFoundryServerGroupDescription
import com.netflix.spinnaker.clouddriver.cf.utils.CloudFoundryClientFactory
import org.cloudfoundry.client.lib.domain.CloudApplication
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier

class DestroyCloudFoundryServerGroupAtomicOperation implements AtomicOperation<Void> {

  private static final String BASE_PHASE = "DESTROY_SERVER_GROUP"

  @Autowired
  CloudFoundryClientFactory cloudFoundryClientFactory

  @Autowired
  @Qualifier('cloudFoundryOperationPoller')
  OperationPoller operationPoller

  DestroyCloudFoundryServerGroupDescription description

  private static Task getTask() {
    TaskRepository.threadLocalTask.get()
  }

  DestroyCloudFoundryServerGroupAtomicOperation(DestroyCloudFoundryServerGroupDescription description) {
    this.description = description
  }

  @Override
  Void operate(List priorOutputs) {
    task.updateStatus BASE_PHASE, "Initializing destruction of server group $description.serverGroupName in $description.region..."

    def client = cloudFoundryClientFactory.createCloudFoundryClient(description.credentials, true)

    try {
      client.deleteApplication(description.serverGroupName)

      operationPoller.waitForOperation(
          {client.applications},
          {List<CloudApplication> apps -> !apps.find {it.name == description.serverGroupName}},
          null, task, description.serverGroupName, BASE_PHASE)

      task.updateStatus BASE_PHASE, "Done destroying server group $description.serverGroupName in $description.region."
    } catch (Exception e) {
      task.updateStatus BASE_PHASE, "Failed to delete server group $description.serverGroupName => $e.message"
    }

    null
  }
}
