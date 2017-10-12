/*
 * Copyright 2017 Netflix, Inc.
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
import com.netflix.spinnaker.clouddriver.titus.deploy.description.DeleteTitusScalingPolicyDescription
import com.netflix.titus.grpc.protogen.DeletePolicyRequest
import com.netflix.titus.grpc.protogen.ScalingPolicyID
import org.springframework.beans.factory.annotation.Autowired

class DeleteTitusScalingPolicyAtomicOperation implements AtomicOperation<Void> {

  DeleteTitusScalingPolicyDescription description

  DeleteTitusScalingPolicyAtomicOperation(DeleteTitusScalingPolicyDescription description) {
    this.description = description
  }

  private static final String BASE_PHASE = "DELETE_SCALING_POLICY"

  private static Task getTask() {
    TaskRepository.threadLocalTask.get()
  }

  @Autowired
  TitusClientProvider titusClientProvider

  @Override
  Void operate(List priorOutputs) {
    task.updateStatus BASE_PHASE, "Initializing Delete Scaling Policy ${description.scalingPolicyID}..."
    def client = titusClientProvider.getTitusAutoscalingClient(description.credentials, description.region)
    if (!client) {
      throw new UnsupportedOperationException("Autoscaling is not supported for this account/region")
    }

    ScalingPolicyID id = ScalingPolicyID.newBuilder().setId(description.scalingPolicyID).build()
    client.deleteScalingPolicy(DeletePolicyRequest.newBuilder().setId(id).build())
    task.updateStatus BASE_PHASE, "Delete Scaling Policy ${description.scalingPolicyID} completed."
  }
}
