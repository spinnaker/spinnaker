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

import com.microsoft.azure.CloudException
import com.microsoft.azure.management.resources.Deployment
import com.netflix.spinnaker.clouddriver.azure.common.AzureUtilities
import com.netflix.spinnaker.clouddriver.azure.resources.common.model.AzureDeploymentOperation
import com.netflix.spinnaker.clouddriver.azure.resources.appgateway.model.AzureAppGatewayDescription
import com.netflix.spinnaker.clouddriver.azure.resources.network.model.AzureVirtualNetworkDescription
import com.netflix.spinnaker.clouddriver.azure.resources.network.view.AzureNetworkProvider
import com.netflix.spinnaker.clouddriver.azure.templates.AzureAppGatewayResourceTemplate
import com.netflix.spinnaker.clouddriver.data.task.Task
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperationException
import org.springframework.beans.factory.annotation.Autowired

class UpsertAzureAppGatewayAtomicOperation implements AtomicOperation<Map> {
  private static final String BASE_PHASE = "UPSERT_APP_GATEWAY"
  // TODO: we change this later to be the Spinnaker load balancer
  // private static final String BASE_PHASE = "UPSERT_LOAD_BALANCER"

  private static Task getTask() {
    TaskRepository.threadLocalTask.get()
  }

  private final AzureAppGatewayDescription description

  @Autowired
  AzureNetworkProvider networkProvider

  UpsertAzureAppGatewayAtomicOperation(AzureAppGatewayDescription description) {
    this.description = description
  }

  /**
   * curl -X POST -H "Content-Type: application/json" -d '[ { "upsertLoadBalancer": { "cloudProvider" : "azure", "appName" : "tappgw1", "loadBalancerName" : "tappgw1-st1-d1", "stack" : "st1", "detail" : "d1", "credentials" : "azure-cred1", "region" : "westus", "probes" : [ { "probeName" : "healthcheck1", "probeProtocol" : "HTTP", "probePort" : "www.bing.com", "probePath" : "/", "probeInterval" : 120, "unhealthyThreshold" : 8, "timeout" : 30 } ], "rules" : [ { "ruleName" : "lbRule1", "protocol" : "HTTP", "externalPort" : "80", "backendPort" : "8080" }, { "ruleName" : "lbRule2", "protocol" : "HTTP", "externalPort" : "8080", "backendPort" : "8080" } ], "name" : "tappgw1-st1-d1", "user" : "[anonymous]" }} ]' localhost:7002/azure/ops
   *
   * @param priorOutputs
   * @return
   */
  @Override
  Map operate(List priorOutputs) {
    task.updateStatus(BASE_PHASE, "Initializing upsert of load balancer ${description.loadBalancerName} " +
      "in ${description.region}...")

    def errList = new ArrayList<String>()
    String resourceGroupName = null
    String virtualNetworkName = null
    String subnetName = null
    String loadBalancerName = null

    try {
      task.updateStatus(BASE_PHASE, "Beginning load balancer deployment")

      description.name = description.loadBalancerName
      resourceGroupName = AzureUtilities.getResourceGroupName(description.appName, description.region)
      virtualNetworkName = AzureUtilities.getVirtualNetworkName(resourceGroupName)

      // Check if we are executing an edit operation on an existing application gateway (it returns null if it does not exist)
      // def appGatewayDescription = description.credentials.networkClient.editAppGateway(description)
      def appGatewayDescription = description.credentials.networkClient.getAppGateway(resourceGroupName, description.name)

      if (appGatewayDescription) {
        // We are executing an edit operation on an existing application gateway; update application gateway using a template deployment
        task.updateStatus(BASE_PHASE, "Update existing application gateway ${appGatewayDescription.loadBalancerName} in ${appGatewayDescription.region}...")

        // We need to retain some of the settings from the current application gateway
        description.publicIpName = appGatewayDescription.publicIpName
        description.subnetResourceId = appGatewayDescription.subnetResourceId
        description.serverGroups = appGatewayDescription.serverGroups
        description.trafficEnabledSG = appGatewayDescription.trafficEnabledSG
        description.vnetResourceGroup = appGatewayDescription.vnetResourceGroup

        Deployment deployment = description.credentials.resourceManagerClient.createResourceFromTemplate(
          AzureAppGatewayResourceTemplate.getTemplate(description),
          resourceGroupName,
          description.region,
          "appGateway")

        errList = AzureDeploymentOperation.checkDeploymentOperationStatus(task, BASE_PHASE, description.credentials, resourceGroupName, deployment.name())
      } else {
        // We are attempting to create a new application gateway
        if (!description.useDefaultVnet) {
          task.updateStatus(BASE_PHASE, "Create ApplicationGateway using virtual network $description.vnet and subnet $description.subnet for server group $description.name")

          // Create corresponding ResourceGroup if it's not created already
          description.credentials.resourceManagerClient.initializeResourceGroupAndVNet(resourceGroupName, null, description.region)

          // We will try to associate the server group with the selected virtual network and subnet
          description.hasNewSubnet = false

          def vnetDescription = networkProvider.get(description.accountName, description.region, description.vnetResourceGroup, description.vnet)

          if (!vnetDescription) {
            throw new RuntimeException("Selected virtual network $description.vnet does not exist")
          }

          // subnet is valid only if it exists within the selected vnet and it's unassigned or all the associations are ALSO application gateways
          description.subnetResourceId = vnetDescription.subnets?.find { subnet ->
            (subnet.name == description.subnet) && (!subnet.connectedDevices || !subnet.connectedDevices.find {it.type != "applicationGateways"})
          }?.resourceId

          if (!description.subnetResourceId) {
            task.updateStatus(BASE_PHASE, "Failed to select subnet for Application Gateway ${description.name}")
            throw new RuntimeException("Selected subnet $description.subnet in virtual network $description.vnet is not valid")
          }
        } else {
          // Create ResourceGroup and default VirtualNetwork if they are not created already
          description.credentials.resourceManagerClient.initializeResourceGroupAndVNet(resourceGroupName, virtualNetworkName, description.region)

          task.updateStatus(BASE_PHASE, "Creating subnet for application gateway")

          // Compute the next subnet address prefix using the cached vnet and a random generated seed
          def vnetDescription = networkProvider.get(description.accountName, description.region, description.vnetResourceGroup, virtualNetworkName)
          Random rand = new Random()
          def nextSubnetAddressPrefix = AzureVirtualNetworkDescription.getNextSubnetAddressPrefix(vnetDescription, rand.nextInt(vnetDescription?.maxSubnets ?: 1))
          subnetName = AzureUtilities.getSubnetName(virtualNetworkName, nextSubnetAddressPrefix)

          // we'll do a final check to make sure that the subnet can be created before we pass it in the deployment template
          def vnet = description.credentials.networkClient.getVirtualNetwork(resourceGroupName, virtualNetworkName)

          if (!subnetName || vnet?.subnets?.find { it.name == subnetName }) {
            // virtualNetworkName is not yet in the cache or the subnet we try to create already exists; we'll use the current vnet
            //   we just got to re-compute the next subnet
            vnetDescription = AzureVirtualNetworkDescription.getDescriptionForVirtualNetwork(vnet)
            nextSubnetAddressPrefix = AzureVirtualNetworkDescription.getNextSubnetAddressPrefix(vnetDescription, rand.nextInt(vnetDescription?.maxSubnets ?: 1))
            subnetName = AzureUtilities.getSubnetName(virtualNetworkName, nextSubnetAddressPrefix)
          }

          task.updateStatus(BASE_PHASE, "Creating new subnet ${subnetName} for ${description.loadBalancerName}")
          description.subnetResourceId = description.credentials.networkClient.createSubnet(resourceGroupName,
            virtualNetworkName,
            subnetName,
            nextSubnetAddressPrefix,
            description.securityGroup)

          if (!description.subnetResourceId) {
            task.updateStatus(BASE_PHASE, "Failed to create new subnet for Application Gateway ${description.name}")
            throw new RuntimeException("Could not create subnet $subnetName in virtual network $virtualNetworkName")
          }

          description.vnet = virtualNetworkName
          description.subnet = subnetName
          description.vnetResourceGroup = resourceGroupName

          description.hasNewSubnet = true
        }

        task.updateStatus(BASE_PHASE, "Create new application gateway ${description.loadBalancerName} in ${description.region}...")
        Deployment deployment = description.credentials.resourceManagerClient.createResourceFromTemplate(
          AzureAppGatewayResourceTemplate.getTemplate(description),
          resourceGroupName,
          description.region,
          "appGateway")

        errList = AzureDeploymentOperation.checkDeploymentOperationStatus(task, BASE_PHASE, description.credentials, resourceGroupName, deployment.name())
        loadBalancerName = description.name
      }
    } catch (CloudException ce) {
      task.updateStatus(BASE_PHASE, "One or more deployment operations have failed. Please see Azure portal for more information. Resource Group: ${resourceGroupName} Application Gateway: ${description.loadBalancerName}")
      errList.add(ce.message)
    } catch (Throwable e) {
      task.updateStatus(BASE_PHASE, "Deployment of application gateway ${description.loadBalancerName} failed: ${e.message}")
      errList.add(e.message)
    }

    if (errList.isEmpty()) {
      task.updateStatus(BASE_PHASE, "Deployment for load balancer ${description.loadBalancerName} in ${description.region} has succeeded.")
    }
    else {
      // cleanup any resources that might have been created prior to server group failing to deploy
      task.updateStatus(BASE_PHASE, "Cleanup any resources created as part of server group upsert")
      try {
        if (loadBalancerName) description.credentials.networkClient.deleteAppGateway(resourceGroupName, loadBalancerName)
        if (subnetName) description.credentials.networkClient.deleteSubnet(resourceGroupName, virtualNetworkName, subnetName)
      } catch (Exception e) {
        def errMessage = "Unexpected exception: ${e.message}; please log in into the Azure Portal and manually remove the following resources: ${subnetName} ${loadBalancerName} ${AzureUtilities.PUBLICIP_NAME_PREFIX + loadBalancerName}"
        task.updateStatus(BASE_PHASE, errMessage)
        errList.add(errMessage)
      }

      throw new AtomicOperationException("${description.loadBalancerName} deployment failed", errList)
    }

    [loadBalancers: [(description.region): [name: description.loadBalancerName]]]
  }
}
