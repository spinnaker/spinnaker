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
import com.netflix.spinnaker.kato.titan.deploy.description.DestroyTitanServerGroupDescription
import com.netflix.titanclient.TitanClient
import com.netflix.titanclient.model.Job
import groovy.util.logging.Slf4j

@Slf4j
class DestroyTitanServerGroupAtomicOperation implements AtomicOperation<Void> {

  private static final String PHASE = "DESTROY_TITAN_SERVER_GROUP"
  private final TitanClientProvider titanClientProvider
  private final DestroyTitanServerGroupDescription description

  DestroyTitanServerGroupAtomicOperation(TitanClientProvider titanClientProvider,
                                         DestroyTitanServerGroupDescription description) {
    this.titanClientProvider = titanClientProvider
    this.description = description
  }

  @Override
  Void operate(List priorOutputs) {
    task.updateStatus PHASE, "Destroying server group: ${description.serverGroupName}..."
    TitanClient titanClient = titanClientProvider.getTitanClient(description.credentials, description.region)
    Job job = titanClient.findJobByName(description.serverGroupName)

    /**
     * TODO: Remove this workaround once the bulk read APIs for tasks and jobs is available in titan api service
     * Temporary workaround: findJobByName() hits a temporary calypso service endpoint which has significantly greater
     * delay (~ 1 min) in surfacing newly launched jobs compared to the titan api service and hence these retries.
     */
    int i = 6
    while (job == null && --i >= 0) {
      Thread.sleep(15*1000L)
      task.updateStatus PHASE, "Did NOT find titan server group named '${description.serverGroupName}'. Retrying..."
      job = titanClient.findJobByName(description.serverGroupName)
    }

    if (!job) {
      throw new IllegalArgumentException("No titan server group named '${description.serverGroupName}' found")
    }

    titanClient.terminateJob(job.id)
    task.updateStatus PHASE, "Successfully issued terminate job request to titan for ${job.id} which corresponds to ${description.serverGroupName}"

    task.updateStatus PHASE, "Completed destroy server group operation for ${description.serverGroupName}"
    null
  }

  private static Task getTask() {
    TaskRepository.threadLocalTask.get()
  }
}
