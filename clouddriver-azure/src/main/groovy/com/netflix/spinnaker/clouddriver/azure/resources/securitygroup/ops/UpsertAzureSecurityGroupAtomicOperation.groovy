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

import com.microsoft.azure.management.resources.models.DeploymentExtended
import com.microsoft.azure.management.resources.models.DeploymentOperation
import com.netflix.spinnaker.clouddriver.azure.client.AzureResourceManagerClient
import com.netflix.spinnaker.clouddriver.azure.common.AzureUtilities
import com.netflix.spinnaker.clouddriver.azure.resources.securitygroup.model.UpsertAzureSecurityGroupDescription
import com.netflix.spinnaker.clouddriver.azure.templates.AzureSecurityGroupResourceTemplate
import com.netflix.spinnaker.clouddriver.data.task.Task
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation

class UpsertAzureSecurityGroupAtomicOperation implements AtomicOperation<Map> {
  private static final String BASE_PHASE = "UPSERT_SECURITY_GROUP"

  private static Task getTask() {
    TaskRepository.threadLocalTask.get()
  }

  private final UpsertAzureSecurityGroupDescription description

  UpsertAzureSecurityGroupAtomicOperation(UpsertAzureSecurityGroupDescription description) {
    this.description = description
  }

  /**
   * curl -X POST -H "Content-Type: application/json" -d '[ { "upsertSecurityGroup": { "cloudProvider" : "azure", "providerType" : "azure", "appName" : "testazure4", "securityGroupName" : "testazure4-sg22-d11", "stack" : "sg22", "detail" : "d11", "credentials" : "azure-cred1", "region" : "westus", "vnet" : "none", "tags" : { "appName" : "testazure4", "stack" : "sg22", "detail" : "d11"}, "securityRules" : [ { "name" : "rule1", "description" : "Allow FE Subnet", "access" : "Allow", "destinationAddressPrefix" : "*", "destinationPortRange" : "433", "direction" : "Inbound", "priority" : 100, "protocol" : "TCP", "sourceAddressPrefix" : "10.0.0.0/24", "sourcePortRange" : "*" }, { "name" : "rule2", "description" : "Block RDP", "access" : "Deny", "destinationAddressPrefix" : "*", "destinationPortRange" : "3389", "direction" : "Inbound", "priority" : 101, "protocol" : "TCP", "sourceAddressPrefix" : "Internet", "sourcePortRange" : "*" } ], "name" : "testazure4-sg22-d11", "user" : "[anonymous]" }} ]' localhost:7002/ops
   *
   * @param priorOutputs
   * @return
   */
  @Override
  Map operate(List priorOutputs) {
    task.updateStatus BASE_PHASE, "Initializing upsert of security group $description.securityGroupName " +
      "in $description.region..."

    Map<String, Boolean> resourceCompletedState = new HashMap<String, Boolean>()

    try {

      task.updateStatus(BASE_PHASE, "Beginning security group deployment")

      String resourceGroupName = AzureUtilities.getResourceGroupName(description.appName, description.region)
      DeploymentExtended deployment = description.credentials.resourceManagerClient.createResourceFromTemplate(description.credentials,
        AzureSecurityGroupResourceTemplate.getTemplate(description),
        resourceGroupName,
        description.region,
        description.securityGroupName)

      String deploymentState = deployment.properties.provisioningState

      while (deploymentIsRunning(deploymentState)) {
        for (DeploymentOperation d : description.credentials.resourceManagerClient.getDeploymentOperations(description.credentials, resourceGroupName, deployment.name)) {
          if (!resourceCompletedState.containsKey(d.id)){
            resourceCompletedState[d.id] = false
          }
          if (d.properties.provisioningState == AzureResourceManagerClient.DeploymentState.SUCCEEDED) {
            if (!resourceCompletedState[d.id]) {
              task.updateStatus BASE_PHASE, String.format("Resource %s created", d.properties.targetResource.resourceName)
              resourceCompletedState[d.id] = true
            }
          }
          else if (d.properties.provisioningState == AzureResourceManagerClient.DeploymentState.FAILED) {
            if (!resourceCompletedState[d.id]) {
              task.updateStatus BASE_PHASE, String.format("Failed to create resource %s: %s", d.properties.targetResource.resourceName, d.properties.statusMessage)
              resourceCompletedState[d.id] = true
            }
          }
        }
        deploymentState = description.credentials.resourceManagerClient.getDeployment(description.credentials, resourceGroupName, deployment.name).properties.provisioningState
      }

      task.updateStatus BASE_PHASE, "Deployment for security group $description.securityGroupName in $description.region has succeeded."
    } catch (Exception e) {
      task.updateStatus BASE_PHASE, String.format("Deployment of security group $description.securityGroupName failed: %s", e.message)
      throw e
    }
    [securityGroups: [(description.region): [name: description.securityGroupName]]]
  }

  private static boolean deploymentIsRunning(String deploymentState) {
    deploymentState != AzureResourceManagerClient.DeploymentState.CANCELED &&
      deploymentState != AzureResourceManagerClient.DeploymentState.DELETED &&
      deploymentState != AzureResourceManagerClient.DeploymentState.FAILED &&
      deploymentState != AzureResourceManagerClient.DeploymentState.SUCCEEDED
  }
}
