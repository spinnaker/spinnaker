/*
 * Copyright 2015 Netflix, Inc.
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

package com.netflix.spinnaker.clouddriver.titus.deploy.ops

import com.netflix.spinnaker.clouddriver.data.task.Task
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation
import com.netflix.spinnaker.clouddriver.titus.TitusClientProvider
import com.netflix.spinnaker.clouddriver.titus.deploy.description.DestroyTitusServerGroupDescription
import com.netflix.spinnaker.clouddriver.titus.client.TitusClient
import com.netflix.spinnaker.clouddriver.titus.client.model.Job
import groovy.util.logging.Slf4j

@Slf4j
class DestroyTitusServerGroupAtomicOperation implements AtomicOperation<Void> {

  private static final String PHASE = "DESTROY_TITUS_SERVER_GROUP"
  private final TitusClientProvider titusClientProvider
  private final DestroyTitusServerGroupDescription description

  DestroyTitusServerGroupAtomicOperation(TitusClientProvider titusClientProvider,
                                         DestroyTitusServerGroupDescription description) {
    this.titusClientProvider = titusClientProvider
    this.description = description
  }

  @Override
  Void operate(List priorOutputs) {
    task.updateStatus PHASE, "Destroying server group: ${description.serverGroupName}..."
    TitusClient titusClient = titusClientProvider.getTitusClient(description.credentials, description.region)
    Job job = titusClient.findJobByName(description.serverGroupName)

    if (!job) {
      throw new IllegalArgumentException("No titus server group named '${description.serverGroupName}' found")
    }

    titusClient.terminateJob(job.id)
    task.updateStatus PHASE, "Successfully issued terminate job request to titus for ${job.id} which corresponds to ${description.serverGroupName}"

    task.updateStatus PHASE, "Completed destroy server group operation for ${description.serverGroupName}"
    null
  }

  private static Task getTask() {
    TaskRepository.threadLocalTask.get()
  }
}
