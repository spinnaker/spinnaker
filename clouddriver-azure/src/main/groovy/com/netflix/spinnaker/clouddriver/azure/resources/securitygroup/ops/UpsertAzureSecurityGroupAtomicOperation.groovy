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

import com.microsoft.azure.management.resources.Deployment
import com.netflix.spinnaker.clouddriver.azure.common.AzureUtilities
import com.netflix.spinnaker.clouddriver.azure.resources.common.model.AzureDeploymentOperation
import com.netflix.spinnaker.clouddriver.azure.resources.securitygroup.model.UpsertAzureSecurityGroupDescription
import com.netflix.spinnaker.clouddriver.azure.templates.AzureSecurityGroupResourceTemplate
import com.netflix.spinnaker.clouddriver.data.task.Task
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperationException

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
    task.updateStatus(BASE_PHASE, "Initializing upsert of security group ${description.securityGroupName} " +
      "in ${description.region}...")

    def errList = new ArrayList<String>()

    try {

      task.updateStatus(BASE_PHASE, "Beginning security group deployment")

      String resourceGroupName = AzureUtilities.getResourceGroupName(description.appName, description.region)

      // Create corresponding ResourceGroup if it's not created already
      description.credentials.resourceManagerClient.initializeResourceGroupAndVNet(resourceGroupName, null, description.region)

      def templateParamMap = [
        location : description.region,
        networkSecurityGroupName : description.securityGroupName,
        networkSecurityGroupResourceGroupName : resourceGroupName,
        virtualNetworkName : description.vnet,
        virtualNetworkResourceGroupName : description.vnetResourceGroup,
        subnetName : description.subnet
      ]

      Deployment deployment = description.credentials.resourceManagerClient.createResourceFromTemplate(
        AzureSecurityGroupResourceTemplate.getTemplate(description),
        resourceGroupName,
        description.region,
        "securityGroup",
        templateParamMap)

      errList = AzureDeploymentOperation.checkDeploymentOperationStatus(task,BASE_PHASE, description.credentials, resourceGroupName, deployment.name())
    } catch (Throwable e) {
      task.updateStatus(BASE_PHASE, "Deployment of security group $description.securityGroupName failed: ${e.message}")
      errList.add(e.message)
    }
    if (errList.isEmpty()) {
      task.updateStatus(BASE_PHASE, "Deployment for security group ${description.securityGroupName} in ${description.region} has succeeded.")
    }
    else {
      throw new AtomicOperationException("${description.securityGroupName} deployment failed", errList)
    }

    [securityGroups: [(description.region): [name: description.securityGroupName]]]
  }
}
