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
import com.netflix.spinnaker.clouddriver.titus.deploy.description.TerminateTitusInstancesDescription
import com.netflix.spinnaker.clouddriver.titus.client.TitusClient
/**
 *
 *
 */
class TerminateTitusInstancesAtomicOperation implements AtomicOperation<Void> {

  private static final String PHASE = "TERMINATE_TITUS_INSTANCES"

  private final TitusClientProvider titusClientProvider
  private final TerminateTitusInstancesDescription description

  TerminateTitusInstancesAtomicOperation(TitusClientProvider titusClientProvider, TerminateTitusInstancesDescription description) {
    this.titusClientProvider = titusClientProvider
    this.description = description
  }

  @Override
  Void operate(List priorOutputs) {
    TitusClient titusClient = titusClientProvider.getTitusClient(description.credentials, description.region)
    task.updateStatus PHASE, "Terminating titus tasks: ${description.instanceIds}..."

    description.instanceIds.each {
      titusClient.terminateTask(it)
      task.updateStatus PHASE, "Successfully issued terminate task request to titus for task: ${it}"
    }

    task.updateStatus PHASE, "Completed terminate instances operation for ${description.instanceIds}"
    null
  }

  private static Task getTask() {
    TaskRepository.threadLocalTask.get()
  }
}
