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

package com.netflix.spinnaker.clouddriver.azure.resources.securitygroup.ops

import com.netflix.spinnaker.clouddriver.azure.common.AzureUtilities
import com.netflix.spinnaker.clouddriver.data.task.Task
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation
import com.netflix.spinnaker.clouddriver.azure.resources.securitygroup.model.DeleteAzureSecurityGroupDescription
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperationException

class DeleteAzureSecurityGroupAtomicOperation implements AtomicOperation<Void> {
  private static final String BASE_PHASE = "DELETE_LOAD_BALANCER"

  private static Task getTask() {
    TaskRepository.threadLocalTask.get()
  }

  private final DeleteAzureSecurityGroupDescription description

  DeleteAzureSecurityGroupAtomicOperation(DeleteAzureSecurityGroupDescription description) {
    this.description = description
  }

  /**
   * curl -X POST -H "Content-Type: application/json" -d '[ { "deleteSecurityGroup": { "cloudProvider" : "azure", "providerType" : "azure", "appName" : "testazure4", "securityGroupName" : "testazure4-sg22-d11", "regions": ["westus"], "credentials": "azure-cred1" }} ]' localhost:7002/ops
   */
  @Override
  Void operate(List priorOutputs) {
    task.updateStatus BASE_PHASE, "Initializing Delete Azure Network Security Group Operation..."
    for (region in description.regions) {
      task.updateStatus BASE_PHASE, "Deleting ${description.securityGroupName} " + "in ${region}..."

      if (!description.credentials) {
        throw new IllegalArgumentException("Unable to resolve credentials for the selected Azure account.")
      }

      try {
        String resourceGroupName = AzureUtilities.getResourceGroupName(description.appName, region)

        description.credentials.networkClient.deleteSecurityGroup(description.credentials,
          resourceGroupName,
          description.securityGroupName)

        task.updateStatus BASE_PHASE, "Done deleting Azure network security group ${description.securityGroupName} in ${region}."
      } catch (Exception e) {
        task.updateStatus BASE_PHASE, String.format("Deletion of Azure network security group ${description.securityGroupName} failed: %s", e.message)
        throw new AtomicOperationException(
          error: "Failed to delete ${description.name}",
          errors: [e.message])
      }
    }

    null
  }
}
