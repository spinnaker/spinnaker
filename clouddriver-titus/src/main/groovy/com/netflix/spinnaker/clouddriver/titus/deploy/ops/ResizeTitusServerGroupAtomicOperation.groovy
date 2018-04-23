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
import com.netflix.spinnaker.clouddriver.titus.client.model.ResizeJobRequest
import com.netflix.spinnaker.clouddriver.titus.deploy.description.ResizeTitusServerGroupDescription

class ResizeTitusServerGroupAtomicOperation implements AtomicOperation<Void> {

  private static final String PHASE = "RESIZE_TITUS_SERVER_GROUP"
  private final TitusClientProvider titusClientProvider
  private final ResizeTitusServerGroupDescription description

  ResizeTitusServerGroupAtomicOperation(TitusClientProvider titusClientProvider,
                                        ResizeTitusServerGroupDescription description) {
    this.titusClientProvider = titusClientProvider
    this.description = description
  }

  @Override
  Void operate(List priorOutputs) {
    task.updateStatus PHASE, "Resizing server group: ${description.serverGroupName}..."
    TitusClient titusClient = titusClientProvider.getTitusClient(description.credentials, description.region)
    Job job = titusClient.findJobByName(description.serverGroupName)

    if (!job) {
      throw new IllegalArgumentException("No titus server group named '${description.serverGroupName}' found")
    }

    Boolean shouldToggleScalingFlags = !job.inService
    if (shouldToggleScalingFlags) {
      titusClient.setAutoscaleEnabled(job.id, true)
    }

    titusClient.resizeJob(
      new ResizeJobRequest()
        .withUser(description.user)
        .withJobId(job.id)
        .withInstancesDesired(description.capacity.desired)
        .withInstancesMin(description.capacity.min)
        .withInstancesMax(description.capacity.max)
    )

    if (shouldToggleScalingFlags) {
      titusClient.setAutoscaleEnabled(job.id, false)
    }

    task.updateStatus PHASE, "Completed resize server group operation for ${description.serverGroupName}"
    null
  }

  private static Task getTask() {
    TaskRepository.threadLocalTask.get()
  }
}
