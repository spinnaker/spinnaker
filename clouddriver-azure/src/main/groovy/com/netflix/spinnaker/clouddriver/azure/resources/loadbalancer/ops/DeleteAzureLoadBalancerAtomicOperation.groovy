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
   * curl -X POST -H "Content-Type: application/json" -d '[ { "deleteLoadBalancer": { "cloudProvider" : "azure", "providerType" : "azure", "appName" : "azure1", "loadBalancerName" : "azure1-st1-d1", "region": "West US", "credentials": "azure-cred1" }} ]' localhost:7002/gce/ops
   */
  @Override
  Void operate(List priorOutputs) {
    task.updateStatus BASE_PHASE, "Initializing deletion of load balancer $description.loadBalancerName " +
      "in $description.region..."

    if (!description.credentials) {
      throw new IllegalArgumentException("Unable to resolve credentials for the selected Azure account.")
    }

    try {
      //TODO: insert real call to Azure resource manager object to delete the selected load balancer
      String resourceGroupName = AzureUtilities.getResourceGroupName(description.appName, description.region)

      def op = description.credentials.networkClient.deleteLoadBalancer(description.credentials,
                                                                        resourceGroupName,
                                                                        description.loadBalancerName)

      task.updateStatus BASE_PHASE, "Done deleting load balancer $description.loadBalancerName in $description.region."
    } catch (Exception e) {
      task.updateStatus BASE_PHASE, String.format("Deployment of load balancer $description.loadBalancerName failed: %s", e.message)
      throw e
    }

    null
  }

}
