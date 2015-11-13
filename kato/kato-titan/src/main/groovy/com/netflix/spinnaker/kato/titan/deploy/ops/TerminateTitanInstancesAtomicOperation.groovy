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

package com.netflix.spinnaker.kato.titan.deploy.ops
import com.netflix.spinnaker.clouddriver.titan.TitanClientProvider
import com.netflix.spinnaker.kato.data.task.Task
import com.netflix.spinnaker.kato.data.task.TaskRepository
import com.netflix.spinnaker.kato.orchestration.AtomicOperation
import com.netflix.spinnaker.kato.titan.deploy.description.TerminateTitanInstancesDescription
import com.netflix.titanclient.TitanClient
/**
 *
 *
 */
class TerminateTitanInstancesAtomicOperation implements AtomicOperation<Void> {

  private static final String PHASE = "TERMINATE_TITAN_INSTANCES"

  private final TitanClientProvider titanClientProvider
  private final TerminateTitanInstancesDescription description

  TerminateTitanInstancesAtomicOperation(TitanClientProvider titanClientProvider, TerminateTitanInstancesDescription description) {
    this.titanClientProvider = titanClientProvider
    this.description = description
  }

  @Override
  Void operate(List priorOutputs) {
    TitanClient titanClient = titanClientProvider.getTitanClient(description.credentials, description.region)
    task.updateStatus PHASE, "Terminating titan tasks: ${description.instanceIds}..."

    description.instanceIds.each {
      titanClient.terminateTask(it)
      task.updateStatus PHASE, "Successfully issued terminate task request to titan for task: ${it}"
    }

    task.updateStatus PHASE, "Completed terminate instances operation for ${description.instanceIds}"
    null
  }

  private static Task getTask() {
    TaskRepository.threadLocalTask.get()
  }
}
