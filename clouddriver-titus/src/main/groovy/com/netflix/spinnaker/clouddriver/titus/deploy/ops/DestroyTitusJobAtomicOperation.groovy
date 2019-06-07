/*
 *
 * Copyright 2019 Netflix, Inc.
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
 *
 */
package com.netflix.spinnaker.clouddriver.titus.deploy.ops

import com.netflix.spinnaker.clouddriver.data.task.Task
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation
import com.netflix.spinnaker.clouddriver.orchestration.events.DeleteServerGroupEvent
import com.netflix.spinnaker.clouddriver.orchestration.events.OperationEvent
import com.netflix.spinnaker.clouddriver.titus.TitusClientProvider
import com.netflix.spinnaker.clouddriver.titus.TitusCloudProvider
import com.netflix.spinnaker.clouddriver.titus.client.TitusClient
import com.netflix.spinnaker.clouddriver.titus.client.model.Job
import com.netflix.spinnaker.clouddriver.titus.client.model.TerminateJobRequest
import com.netflix.spinnaker.clouddriver.titus.deploy.description.DestroyTitusJobDescription

class DestroyTitusJobAtomicOperation implements AtomicOperation<Void> {
  private static final String PHASE = "DESTROY_TITUS_JOB"
  private final TitusClientProvider titusClientProvider
  private final DestroyTitusJobDescription description
  private final Collection<DeleteServerGroupEvent> events = []

  DestroyTitusJobAtomicOperation(TitusClientProvider titusClientProvider, DestroyTitusJobDescription description) {
    this.titusClientProvider = titusClientProvider
    this.description = description
  }

  @Override
  Void operate(List priorOutputs) {
    task.updateStatus PHASE, "Destroying job: ${description.jobId}..."
    TitusClient titusClient = titusClientProvider.getTitusClient(description.credentials, description.region)
    Job job = titusClient.getJobAndAllRunningAndCompletedTasks(description.jobId)
    if (job) {
      titusClient.terminateJob((TerminateJobRequest) new TerminateJobRequest().withJobId(job.id).withUser(description.user))
      events << new DeleteServerGroupEvent(
        TitusCloudProvider.ID, description.credentials.name, description.region, description.jobId
      )
      task.updateStatus PHASE, "Successfully issued terminate job request to titus for ${job.id}"
    } else {
      task.updateStatus PHASE, "No titus job found wit id ${description.jobId}"
    }

    task.updateStatus PHASE, "Completed destroy job operation for ${description.jobId}"
    null
  }

  @Override
  Collection<OperationEvent> getEvents() {
    return events
  }

  private static Task getTask() {
    TaskRepository.threadLocalTask.get()
  }
}
