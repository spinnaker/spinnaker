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

package com.netflix.spinnaker.clouddriver.azure.resources.servergroup.ops

import com.microsoft.azure.management.resources.models.DeploymentExtended
import com.microsoft.azure.management.resources.models.DeploymentOperation
import com.netflix.spinnaker.clouddriver.azure.client.AzureResourceManagerClient
import com.netflix.spinnaker.clouddriver.azure.common.AzureUtilities
import com.netflix.spinnaker.clouddriver.azure.resources.servergroup.model.AzureServerGroupDescription
import com.netflix.spinnaker.clouddriver.azure.templates.AzureServerGroupResourceTemplate
import com.netflix.spinnaker.clouddriver.data.task.Task
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation

class CreateAzureServerGroupAtomicOperation implements AtomicOperation<Map> {
  private static final String BASE_PHASE = "CREATE_SERVER_GROUP"

  private static Task getTask() {
    TaskRepository.threadLocalTask.get()
  }

  private final AzureServerGroupDescription description

  CreateAzureServerGroupAtomicOperation(AzureServerGroupDescription description) {
    this.description = description
  }

  /**
   * curl -X POST -H "Content-Type: application/json" -d '[{"createServerGroup":{"name":"a4-s2-d1","cloudProvider":"azure","application":"azure1","stack":"s2","detail":"d1","credentials":"azure-account","region":"westus","user":"[anonymous]","upgradePolicy":"Manual","image":{"publisher":"Canonical","offer":"UbuntuServer","sku":"15.04","version":"latest"},"sku":{"name":"Standard_A1","tier":"Standard","capacity":2},"osConfig":{"adminUsername":"spinnakeruser","adminPassword":"!Qnti**234"},"type":"createServerGroup"}}]' localhost:7002/ops
   *
   * @param priorOutputs
   * @return
   */
  @Override
  Map operate(List priorOutputs) {
    task.updateStatus BASE_PHASE, "Initializing deployment of server group $description.name " +
      "in $description.region..."

    Map<String, Boolean> resourceCompletedState = new HashMap<String, Boolean>()

    try {

      task.updateStatus(BASE_PHASE, "Beginning server group deployment")

      String resourceGroupName = AzureUtilities.getResourceGroupName(description.application, description.region)
      String virtualNetworkName = AzureUtilities.getVirtualNetworkName(resourceGroupName)

      description.credentials.resourceManagerClient.initializeResourceGroupAndVNet(description.credentials, resourceGroupName, virtualNetworkName, description.region)

      // TODO We just try to grab the next subnet, which fails if the largest possible subnet is already taken.
      // TODO We also just assume that a vnet can only have one address range.
      task.updateStatus(BASE_PHASE, "Creating subnet for server group")
      def vnet = description.credentials.networkClient.getVirtualNetwork(description.credentials,
        resourceGroupName,
        virtualNetworkName)
      if (vnet.addressSpace.addressPrefixes.size() != 1) {
        throw new RuntimeException(
          "Virtual Network found with ${vnet.addressSpace.addressPrefixes.size()} address spaces; expected: 1")
      }
      String vnetPrefix = vnet.addressSpace.addressPrefixes[0]
      String subnetPrefix = null
      if (vnet.subnets.size() > 0) {
        subnetPrefix = vnet.subnets.max({ a, b -> AzureUtilities.compareIpv4AddrPrefixes(a.addressPrefix, b.addressPrefix) }).addressPrefix
      }
      String nextSubnet = AzureUtilities.getNextSubnet(vnetPrefix, subnetPrefix)
      String subnetName = AzureUtilities.getSubnetName(virtualNetworkName, nextSubnet)
      description.credentials.networkClient.createSubnet(description.credentials,
        resourceGroupName,
        virtualNetworkName,
        subnetName,
        nextSubnet)
      String subnetId = description.credentials.networkClient.getSubnet(description.credentials, resourceGroupName, subnetName).id

      task.updateStatus(BASE_PHASE, "Deploying server group")
      DeploymentExtended deployment = description.credentials.resourceManagerClient.createResourceFromTemplate(description.credentials,
        AzureServerGroupResourceTemplate.getTemplate(description),
        resourceGroupName,
        description.region,
        description.name,
        [subnetId: subnetId])

      String deploymentState = deployment.properties.provisioningState

      while (deploymentIsRunning(deploymentState)) {
        for (DeploymentOperation d : description.credentials.resourceManagerClient.getDeploymentOperations(description.credentials, resourceGroupName, deployment.name)) {
          if (!resourceCompletedState.containsKey(d.id)) {
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

      task.updateStatus BASE_PHASE, "Deployment for server group $description.name in $description.region has succeeded."
    } catch (Exception e) {
      task.updateStatus BASE_PHASE, String.format("Deployment of server group $description.name failed: %s", e.message)
      throw e
    }
    [serverGroups: [(description.region): [name: description.name]]]
  }

  private static boolean deploymentIsRunning(String deploymentState) {
    deploymentState != AzureResourceManagerClient.DeploymentState.CANCELED &&
      deploymentState != AzureResourceManagerClient.DeploymentState.DELETED &&
      deploymentState != AzureResourceManagerClient.DeploymentState.FAILED &&
      deploymentState != AzureResourceManagerClient.DeploymentState.SUCCEEDED
  }
}
