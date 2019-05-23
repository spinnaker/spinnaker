/*
 * Copyright 2015 The original authors.
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

package com.netflix.spinnaker.clouddriver.azure.resources.loadbalancer.ops

import com.netflix.spinnaker.clouddriver.azure.common.AzureUtilities
import com.netflix.spinnaker.clouddriver.data.task.Task
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation
import com.netflix.spinnaker.clouddriver.azure.resources.loadbalancer.model.DeleteAzureLoadBalancerDescription
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperationException

class DeleteAzureLoadBalancerAtomicOperation implements AtomicOperation<Void> {
  private static final String BASE_PHASE = "DELETE_LOAD_BALANCER"

  private static Task getTask() {
    TaskRepository.threadLocalTask.get()
  }

  private final DeleteAzureLoadBalancerDescription description

  DeleteAzureLoadBalancerAtomicOperation(DeleteAzureLoadBalancerDescription description) {
    this.description = description
  }

  /**
   * curl -X POST -H "Content-Type: application/json" -d '[ { "deleteLoadBalancer": { "cloudProvider" : "azure", "providerType" : "azure", "appName" : "azure1", "loadBalancerName" : "azure1-st1-d1", "regions": ["westus"], "region": "westus", "credentials": "azure-cred1" }} ]' localhost:7002/ops
   */
  @Override
  Void operate(List priorOutputs) {
    task.updateStatus(BASE_PHASE, "Initializing Delete Azure Load Balancer Operation...")
    task.updateStatus(BASE_PHASE, "Deleting ${description.loadBalancerName}...")

    if (!description.credentials) {
      throw new IllegalArgumentException("Unable to resolve credentials for the selected Azure account.")
    }

    try {
      String resourceGroupName = AzureUtilities.getResourceGroupName(description.appName, description.region)

      description
        .credentials
        .networkClient
        .deleteLoadBalancer(resourceGroupName, description.loadBalancerName)

      // TODO: check response to ensure operation succeeded
      task.updateStatus(BASE_PHASE, "Deletion of Azure load balancer ${description.loadBalancerName} in ${description.region} has succeeded.")
    } catch (Exception e) {
      task.updateStatus(BASE_PHASE, "Deletion of load balancer ${description.loadBalancerName} failed: e.message")
      throw new AtomicOperationException("Failed to delete ${description.name}", [e.message])
    }

    null
  }

}
