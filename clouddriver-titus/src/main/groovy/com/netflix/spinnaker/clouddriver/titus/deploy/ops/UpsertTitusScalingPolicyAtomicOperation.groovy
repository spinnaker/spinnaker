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
import com.netflix.spinnaker.clouddriver.titus.deploy.description.UpsertTitusScalingPolicyDescription
import com.netflix.spinnaker.kork.core.RetrySupport
import com.netflix.titus.grpc.protogen.*
import com.netflix.titus.grpc.protogen.PutPolicyRequest.Builder
import com.netflix.titus.grpc.protogen.ScalingPolicyStatus.ScalingPolicyState
import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Autowired

@Slf4j
class UpsertTitusScalingPolicyAtomicOperation implements AtomicOperation<Map> {

  UpsertTitusScalingPolicyDescription description

  UpsertTitusScalingPolicyAtomicOperation(UpsertTitusScalingPolicyDescription description) {
    this.description = description
  }

  private static final String BASE_PHASE = "UPSERT_SCALING_POLICY"

  private static Task getTask() {
    TaskRepository.threadLocalTask.get()
  }

  @Autowired
  TitusClientProvider titusClientProvider

  @Autowired
  RetrySupport retrySupport

  @Override
  Map operate(List priorOutputs) {
    task.updateStatus BASE_PHASE, "Initializing Upsert Scaling Policy..."
    def client = titusClientProvider.getTitusAutoscalingClient(description.credentials, description.region)

    if (!client) {
      throw new UnsupportedOperationException("Autoscaling is not supported for this account/region")
    }

    if (description.scalingPolicyID) {

      retrySupport.retry({ ->
        client.updateScalingPolicy(
          UpdatePolicyRequest.newBuilder()
            .setScalingPolicy(description.toScalingPolicyBuilder().build())
            .setPolicyId(ScalingPolicyID.newBuilder().setId(description.scalingPolicyID).build())
            .build()
        )
      }, 10, 3000, false)

      task.updateStatus BASE_PHASE, "Scaling policy successfully updated"

      return [scalingPolicyID: description.scalingPolicyID]
    } else {
      ScalingPolicy.Builder builder = description.toScalingPolicyBuilder()

      Builder requestBuilder = PutPolicyRequest.newBuilder()
        .setScalingPolicy(builder)
        .setJobId(description.jobId)

      task.updateStatus BASE_PHASE, "Create Scaling Policy request constructed, sending..."

      ScalingPolicyID result = client.createScalingPolicy(requestBuilder.build())

      task.updateStatus BASE_PHASE, "Create Scaling Policy succeeded; new policy ID: ${result.id}; monitoring creation..."

      // make sure the new policy was applied
      verifyNewPolicyState(client, result)

      task.updateStatus BASE_PHASE, "Scaling policy successfully created"

      return [scalingPolicyID: result.id]
    }

  }

  private void verifyNewPolicyState(client, result) {
    retrySupport.retry({ ->
      ScalingPolicyResult updatedPolicy = client.getScalingPolicy(result.id)
      if (!updatedPolicy || (updatedPolicy.getPolicyState().state != ScalingPolicyState.Applied)) {
        throw new IllegalStateException("New policy did not transition to applied state within 45 seconds")
      }
    }, 5000, 10, false)
  }

}
