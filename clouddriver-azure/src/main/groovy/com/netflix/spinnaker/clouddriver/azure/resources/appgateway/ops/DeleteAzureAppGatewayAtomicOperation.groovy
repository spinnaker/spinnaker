/*
 * Copyright 2016 The original authors.
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

package com.netflix.spinnaker.clouddriver.azure.resources.appgateway.ops

import com.netflix.spinnaker.clouddriver.azure.common.AzureUtilities
import com.netflix.spinnaker.clouddriver.azure.resources.appgateway.model.AzureAppGatewayDescription
import com.netflix.spinnaker.clouddriver.data.task.Task
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperationException

class DeleteAzureAppGatewayAtomicOperation implements AtomicOperation<Void> {
  private static final String BASE_PHASE = "DELETE_APP_GATEWAY"
  // TODO: we change this later to be the Spinnaker load balancer
  // private static final String BASE_PHASE = "DELETE_LOAD_BALANCER"

  private static Task getTask() {
    TaskRepository.threadLocalTask.get()
  }

  private final AzureAppGatewayDescription description

  DeleteAzureAppGatewayAtomicOperation(AzureAppGatewayDescription description) {
    this.description = description
  }

  /**
   * curl -X POST -H "Content-Type: application/json" -d '[ { "deleteLoadBalancer": { "cloudProvider" : "azure", "appName" : "tappgw1", "loadBalancerName" : "tappgw1-st1-d1", "credentials" : "azure-cred1", "region" : "westus", "name" : "tappgw1-st1-d1", "user" : "[anonymous]" }} ]' localhost:7002/azure/ops
   *
   * @param priorOutputs
   * @return
   */
  @Override
  Void operate(List priorOutputs) {
    task.updateStatus(BASE_PHASE, "Initializing deletion of load balancer ${description.loadBalancerName} " +
      "in ${description.region}...")

    if (!description.credentials) {
      throw new IllegalArgumentException("Unable to resolve credentials for the given Azure account.")
    }

    try {
      task.updateStatus(BASE_PHASE, "Deleting Azure Application Gateway ${description.loadBalancerName} " + "in ${description.region}...")
      String resourceGroupName = AzureUtilities.getResourceGroupName(description.appName, description.region)

      description
        .credentials
        .networkClient
        .deleteAppGateway(resourceGroupName, description.loadBalancerName)

      // TODO: check response to ensure operation succeeded
      task.updateStatus(BASE_PHASE, "Deletion of load balancer ${description.loadBalancerName} in ${description.region} has succeeded.")
    } catch (Exception e) {
      task.updateStatus(BASE_PHASE, "Deletion of load balancer ${description.loadBalancerName} failed: e.message")
      throw new AtomicOperationException(
        error: "Failed to delete ${description.name}",
        errors: [e.message])
    }

    null
  }
}
