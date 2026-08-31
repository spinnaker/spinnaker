/*
 * Copyright 2026 The original authors.
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

package com.netflix.spinnaker.clouddriver.azure.resources.instance.ops

import com.netflix.frigga.Names
import com.netflix.spinnaker.clouddriver.azure.common.AzureUtilities
import com.netflix.spinnaker.clouddriver.azure.resources.instance.model.AzureInstanceDescription
import com.netflix.spinnaker.clouddriver.data.task.Task
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperationException

/**
 * Deleting a VMSS instance decrements the scale set's capacity, which is exactly the
 * semantics this operation wants. Contrast TerminateAzureInstancesAtomicOperation, which
 * reimages so capacity is preserved.
 */
class TerminateAzureInstanceAndDecrementServerGroupAtomicOperation implements AtomicOperation<Void> {
  private static final String BASE_PHASE = "TERMINATE_INSTANCE_AND_DECREMENT"

  private static Task getTask() {
    TaskRepository.threadLocalTask.get()
  }

  private final AzureInstanceDescription description

  TerminateAzureInstanceAndDecrementServerGroupAtomicOperation(AzureInstanceDescription description) {
    this.description = description
  }

  /**
   * curl -X POST -H "Content-Type: application/json" -d '[ { "terminateInstanceAndDecrementServerGroup": { "instance": "myapp-dev-v086_3", "serverGroupName": "myapp-dev-v086", "region": "westus", "credentials": "azure-cred1" }} ]' localhost:7002/azure/ops
   */
  @Override
  Void operate(List priorOutputs) {
    def serverGroupName = description.targetServerGroupName
    def instanceIds = description.targetInstanceIds

    if (!serverGroupName) {
      throw new IllegalArgumentException("A serverGroupName is required to terminate Azure instances.")
    }
    if (!instanceIds) {
      throw new IllegalArgumentException("No instances were supplied to terminate.")
    }

    def appName = description.appName ?: Names.parseName(serverGroupName).app
    def resourceGroupName = AzureUtilities.getResourceGroupName(appName, description.region)

    task.updateStatus BASE_PHASE,
      "Terminating instances ${instanceIds.join(", ")} and shrinking ${serverGroupName}..."

    try {
      description.credentials.computeClient.deleteInstances(resourceGroupName, serverGroupName, instanceIds)
    } catch (Exception e) {
      task.updateStatus BASE_PHASE, "Termination of ${instanceIds.join(", ")} failed: ${e.message}"
      throw new AtomicOperationException("Failed to terminate instances in ${serverGroupName}", [e.message])
    }

    task.updateStatus BASE_PHASE,
      "Done terminating instances ${instanceIds.join(", ")} and shrinking ${serverGroupName}."
    null
  }
}
