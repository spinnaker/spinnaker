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
import com.netflix.spinnaker.clouddriver.titus.client.model.ResizeJobRequest
import com.netflix.spinnaker.clouddriver.titus.client.model.TerminateTasksAndShrinkJobRequest
import com.netflix.spinnaker.clouddriver.titus.deploy.description.DetachTitusInstancesDescription

class DetachTitusInstancesAtomicOperation implements AtomicOperation<Void> {

  private static final String BASE_PHASE = "DETACH_TITUS_INSTANCES"
  private final TitusClientProvider titusClientProvider
  private final DetachTitusInstancesDescription description

  DetachTitusInstancesAtomicOperation(TitusClientProvider titusClientProvider,
                                      DetachTitusInstancesDescription description) {
    this.titusClientProvider = titusClientProvider
    this.description = description
  }

  @Override
  Void operate(List priorOutputs) {
    task.updateStatus BASE_PHASE, "Detaching instances: ${description.instanceIds}..."
    TitusClient titusClient = titusClientProvider.getTitusClient(description.credentials, description.region)

    def job = titusClient.findJobByName(description.asgName)
    if (!job) {
      task.updateStatus BASE_PHASE, "job not found"
      return
    }

    def validInstanceIds = description.instanceIds.intersect(job.tasks*.id)

    if (validInstanceIds.isEmpty()) {
      task.updateStatus BASE_PHASE, "No detachable instances"
      return
    }

    int newMin = job.instances - validInstanceIds.size()
    if (newMin < job.instancesMin) {
      if (description.adjustMinIfNecessary) {
        if (newMin < 0) {
          task.updateStatus BASE_PHASE, "Cannot adjust min size below 0"
        } else {
          titusClient.resizeJob(
            new ResizeJobRequest()
              .withInstancesDesired(job.instancesDesired)
              .withInstancesMax(job.instancesMax)
              .withInstancesMin(newMin)
              .withJobId(job.id)
              .withUser(description.user)
          )
        }
      } else {
        task.updateStatus BASE_PHASE, "Cannot decrement ASG below minSize - set adjustMinIfNecessary to resize down minSize before detaching instances"
        throw new IllegalStateException("Invalid ASG capacity for detachInstances (min: $job.instancesMin, max: $job.instancesMax, desired: $job.instancesDesired)")
      }
    }

    task.updateStatus BASE_PHASE, "Detaching instances (${validInstanceIds.join(", ")}) from ASG (${description.asgName})."
    titusClient.terminateTasksAndShrink(
      new TerminateTasksAndShrinkJobRequest().withUser(description.user).withShrink(true).withTaskIds(validInstanceIds)
    )
    task.updateStatus BASE_PHASE, "Detached instances (${validInstanceIds.join(", ")}) from ASG (${description.asgName})."

  }

  private static Task getTask() {
    TaskRepository.threadLocalTask.get()
  }
}
