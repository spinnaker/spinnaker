/*
 * Copyright 2016 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
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
 */

package com.netflix.spinnaker.clouddriver.titus.deploy.ops

import com.netflix.spinnaker.clouddriver.data.task.Task
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation
import com.netflix.spinnaker.clouddriver.titus.TitusClientProvider
import com.netflix.spinnaker.clouddriver.titus.client.TitusClient
import com.netflix.spinnaker.clouddriver.titus.client.model.Job
import com.netflix.spinnaker.clouddriver.titus.deploy.description.DisableTitusServerGroupDescription

class DisableTitusServerGroupAtomicOperation implements AtomicOperation<Void> {

  private static final String PHASE = "DISABLE_TITUS_SERVER_GROUP"
  private final TitusClientProvider titusClientProvider
  private final DisableTitusServerGroupDescription description

  DisableTitusServerGroupAtomicOperation(TitusClientProvider titusClientProvider,
                                                             DisableTitusServerGroupDescription description) {
    this.titusClientProvider = titusClientProvider
    this.description = description
  }

  @Override
  Void operate(List priorOutputs) {
    task.updateStatus PHASE, "Disabling server group: ${description.serverGroupName}..."
    TitusClient titusClient = titusClientProvider.getTitusClient(description.credentials, description.region)
    Job job = titusClient.findJobByName(description.serverGroupName)

    if (!job) {
      throw new IllegalArgumentException("No titus server group named '${description.serverGroupName}' found")
    }

    //TODO(cfieber) - this exists to support the 'disable before destroy' flow but pretty much just destroys before destroy...
    for (t in job.tasks) {
      titusClient.terminateTask(t.id)
    }

    task.updateStatus PHASE, "Completed disable server group operation for ${description.serverGroupName}"
    null
  }

  private static Task getTask() {
    TaskRepository.threadLocalTask.get()
  }
}
