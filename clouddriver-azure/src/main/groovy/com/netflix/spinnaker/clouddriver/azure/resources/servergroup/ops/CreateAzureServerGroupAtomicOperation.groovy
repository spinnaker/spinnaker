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

import com.microsoft.azure.management.resources.Deployment
import com.netflix.spinnaker.clouddriver.azure.common.AzureUtilities
import com.netflix.spinnaker.clouddriver.azure.resources.common.model.AzureDeploymentOperation
import com.netflix.spinnaker.clouddriver.azure.resources.network.model.AzureVirtualNetworkDescription
import com.netflix.spinnaker.clouddriver.azure.resources.network.view.AzureNetworkProvider
import com.netflix.spinnaker.clouddriver.azure.resources.common.model.KeyVaultSecret
import com.netflix.spinnaker.clouddriver.azure.resources.servergroup.model.AzureServerGroupDescription
import com.netflix.spinnaker.clouddriver.azure.templates.AzureServerGroupResourceTemplate
import com.netflix.spinnaker.clouddriver.data.task.Task
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperationException
import org.springframework.beans.factory.annotation.Autowired

class CreateAzureServerGroupAtomicOperation implements AtomicOperation<Map> {
  private static final String BASE_PHASE = "CREATE_SERVER_GROUP"

  private static Task getTask() {
    TaskRepository.threadLocalTask.get()
  }

  private final AzureServerGroupDescription description

  @Autowired
  AzureNetworkProvider networkProvider

  CreateAzureServerGroupAtomicOperation(AzureServerGroupDescription description) {
    this.description = description
  }

  /**
   * curl -X POST -H "Content-Type: application/json" -d '[{"createServerGroup":{"name":"taz-st1-d1","cloudProvider":"azure","application":"taz","stack":"st1","detail":"d1","vnet":"vnet-select","subnet":"subnet1","account":"azure-cred1","selectedProvider":"azure","capacity":{"useSourceCapacity":false,"min":1,"max":1},"credentials":"azure-cred1","region":"westus","loadBalancerName":"taz-ag1-d1","securityGroupName":"taz-secg1","user":"[anonymous]","upgradePolicy":"Manual","image":{"account":"azure-cred1","imageName":"UbuntuServer-14.04.3-LTS(Recommended)","isCustom":false,"offer":"UbuntuServer","ostype":null,"publisher":"Canonical","region":null,"sku":"14.04.3-LTS","uri":null,"version":"14.04.201602171"},"sku":{"name":"Standard_DS1_v2","tier":"Standard","capacity":1},"osConfig":{},"type":"createServerGroup"}}]' localhost:7002/ops
   *
   * @param priorOutputs
   * @return
   */
  @Override
  Map operate(List priorOutputs) {
    task.updateStatus(BASE_PHASE, "Initializing deployment of server group ${description.name} " +
      "in ${description.region}...")

    def errList = new ArrayList<String>()
    String resourceGroupName = null
    String virtualNetworkName = null
    String subnetName = null
    String subnetId
    String serverGroupName = null
    String appGatewayPoolID = null

    try {

      task.updateStatus(BASE_PHASE, "Beginning server group deployment")

      // if this is not a custom image, then we need to go get the OsType from Azure
      if (!description.image.isCustom) {
        def virtualMachineImage = description.credentials.computeClient.getVMImage(description.region,
          description.image.publisher, description.image.offer, description.image.sku, description.image.version)

        if (!virtualMachineImage) {
          throw new RuntimeException("Invalid published image was selected; $description.image.publisher:$description.image.offer:$description.image.sku:$description.image.version does not exist")
        }

        description.image.imageName ?: virtualMachineImage.name
        description.image.ostype = virtualMachineImage?.osDiskImage?.operatingSystem
      }

      resourceGroupName = AzureUtilities.getResourceGroupName(description.application, description.region)

      // TODO: replace appGatewayName with loadBalancerName
      if (!description.appGatewayName) {
        description.appGatewayName = description.loadBalancerName
      }
      def appGatewayDescription = description.credentials.networkClient.getAppGateway(resourceGroupName, description.appGatewayName)

      if (!appGatewayDescription) {
        throw new RuntimeException("Invalid load balancer was selected; $description.appGatewayName does not exist")
      }

      virtualNetworkName = appGatewayDescription.vnet
      if (description.vnet && description.vnet != virtualNetworkName) {
        throw new RuntimeException("Invalid load balancer was selected; $description.appGatewayName does not exist")
      }

      def vnetDescription = networkProvider.get(description.accountName, description.region, appGatewayDescription.vnetResourceGroup, virtualNetworkName)

      if (!vnetDescription) {
        throw new RuntimeException("Selected virtual network $virtualNetworkName does not exist")
      }

      if (!description.createNewSubnet) {
        task.updateStatus(BASE_PHASE, "Using virtual network $virtualNetworkName and subnet $description.subnet for server group $description.name")

        // we will try to associate the server group with the selected virtual network and subnet
        description.hasNewSubnet = false

        // subnet is valid only if it exists within the selected vnet and it's unassigned or all the associations are NOT application gateways
        subnetId = vnetDescription.subnets?.find { subnet ->
          (subnet.name == description.subnet) && (!subnet.connectedDevices || !subnet.connectedDevices.find {it.type == "applicationGateways"})
        }?.resourceId

        if (!subnetId) {
          throw new RuntimeException("Selected subnet $description.subnet in virtual network $description.vnet is not valid")
        }
      } else {
        task.updateStatus(BASE_PHASE, "Creating subnet for server group")

        // Compute the next subnet address prefix using the cached vnet and a random generated seed
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

        subnetId = description.credentials.networkClient.createSubnet(resourceGroupName,
          virtualNetworkName,
          subnetName,
          nextSubnetAddressPrefix,
          description.securityGroupName)

        if (!subnetId) {
          throw new RuntimeException("Could not create subnet $subnetName in virtual network $virtualNetworkName")
        }

        description.hasNewSubnet = true
      }

      AzureServerGroupNameResolver nameResolver = new AzureServerGroupNameResolver(description.accountName, description.region, description.credentials)
      description.name = nameResolver.resolveNextServerGroupName(description.application, description.stack, description.detail, false)
      description.clusterName = description.getClusterName()
      description.appName = description.application
      description.vnet = virtualNetworkName
      description.subnet = subnetName
      description.vnetResourceGroup = appGatewayDescription.vnetResourceGroup

      // Verify that it can be used for this server group/cluster. create a backend address pool entry if it doesn't already exist
      task.updateStatus(BASE_PHASE, "Create new backend address pool in $description.appGatewayName")
      appGatewayPoolID = description.credentials
        .networkClient
        .createAppGatewayBAPforServerGroup(resourceGroupName, description.appGatewayName, description.name)

      if (!appGatewayPoolID) {
        throw new RuntimeException("Selected Load Balancer $description.appGatewayName does not exist")
      }

      // TODO: Debug only; can be removed as part of tags cleanup
      description.appGatewayBapId = appGatewayPoolID

      // If Linux, set up connection on port 22 (ssh) otherwise use port 3389 (rdp)
      def backendPort = description.image.ostype.toLowerCase() == "linux" ? 22 : 3389
      description.addInboundPortConfig(AzureUtilities.INBOUND_NATPOOL_PREFIX + description.name, 50000, 50099, "tcp", backendPort)

      Map<String, Object> templateParameters = [:]

      templateParameters[AzureServerGroupResourceTemplate.subnetParameterName] = subnetId
      templateParameters[AzureServerGroupResourceTemplate.appGatewayAddressPoolParameterName] = appGatewayPoolID
      templateParameters[AzureServerGroupResourceTemplate.vmUserNameParameterName] = new KeyVaultSecret("VMUsername",
        description.credentials.subscriptionId,
        description.credentials.defaultResourceGroup,
        description.credentials.defaultKeyVault)

      if(description.credentials.useSshPublicKey) {
        templateParameters[AzureServerGroupResourceTemplate.vmSshPublicKeyParameterName] = new KeyVaultSecret("VMSshPublicKey",
          description.credentials.subscriptionId,
          description.credentials.defaultResourceGroup,
          description.credentials.defaultKeyVault)
      }
      else {
        templateParameters[AzureServerGroupResourceTemplate.vmPasswordParameterName] = new KeyVaultSecret("VMPassword",
          description.credentials.subscriptionId,
          description.credentials.defaultResourceGroup,
          description.credentials.defaultKeyVault)
      }

      // The empty "" cannot be assigned to the custom data otherwise Azure service will run into error complaining "custom data must be in Base64".
      // So once there is no custom data, remove this template section rather than assigning a "".
      if(description.osConfig.customData){
        templateParameters[AzureServerGroupResourceTemplate.customDataParameterName] = description.osConfig.customData
      }

      if (errList.isEmpty()) {
        description.subnetId = subnetId
        task.updateStatus(BASE_PHASE, "Deploying server group")
        Deployment deployment = description.credentials.resourceManagerClient.createResourceFromTemplate(
          AzureServerGroupResourceTemplate.getTemplate(description),
          resourceGroupName,
          description.region,
          description.name,
          "serverGroup",
          templateParameters)

        errList.addAll(AzureDeploymentOperation.checkDeploymentOperationStatus(task, BASE_PHASE, description.credentials, resourceGroupName, deployment.name()))
        serverGroupName = errList.isEmpty() ? description.name : null
      }
    } catch (Exception e) {
      task.updateStatus(BASE_PHASE, "Unexpected exception: Deployment of server group ${description.name} failed: ${e.message}")
      errList.add(e.message)
    }
    if (errList.isEmpty()) {
      if (description.credentials.networkClient.isServerGroupDisabled(resourceGroupName, description.appGatewayName, description.name)) {
        description
          .credentials
          .networkClient
          .enableServerGroup(resourceGroupName, description.appGatewayName, description.name)
        task.updateStatus BASE_PHASE, "Done enabling Azure server group ${description.name} in ${description.region}."
      } else {
        task.updateStatus BASE_PHASE, "Azure server group ${description.name} in ${description.region} is already enabled."
      }

      task.updateStatus(BASE_PHASE, "Deployment for server group ${description.name} in ${description.region} has succeeded.")
    }
    else {
      // cleanup any resources that might have been created prior to server group failing to deploy
      task.updateStatus(BASE_PHASE, "Cleanup any resources created as part of server group upsert")
      try {
        if (serverGroupName) {
          def sgDescription = description.credentials
            .computeClient
            .getServerGroup(resourceGroupName, serverGroupName)
          if (sgDescription) {
            description.credentials
              .computeClient
              .destroyServerGroup(resourceGroupName, serverGroupName)

            // If this an Azure Market Store image, delete the storage that was created for it as well
            if (!sgDescription.image.isCustom) {
              sgDescription.storageAccountNames?.each { def storageAccountName ->
                description.credentials
                  .storageClient
                  .deleteStorageAccount(resourceGroupName, storageAccountName)
              }
            }
          }
        }
        if (description.hasNewSubnet) {
          description.credentials
            .networkClient
            .deleteSubnet(resourceGroupName, virtualNetworkName, subnetName)
        }
      } catch (Exception e) {
        def errMessage = "Unexpected exception: ${e.message}! Please log in into Azure Portal and manually delete any resource associated with the ${description.name} server group such as storage accounts, internal load balancer, public IP and subnets"
        task.updateStatus(BASE_PHASE, errMessage)
        errList.add(errMessage)
      }

      try {
        if (appGatewayPoolID) {
          description.credentials
            .networkClient
            .removeAppGatewayBAPforServerGroup(resourceGroupName, description.appGatewayName, description.name)
        }
      } catch (Exception e) {
        def errMessage = "Unexpected exception: ${e.message}! Application Gateway backend address pool entry ${appGatewayPoolID} associated with the ${description.name} server group could not be deleted"
        task.updateStatus(BASE_PHASE, errMessage)
        errList.add(errMessage)
      }

      throw new AtomicOperationException("${description.name} deployment failed", errList)
    }

    [serverGroups: [(description.region): [name: description.name]],
    serverGroupNames: ["${description.region}:${description.name}".toString()]]
  }
}
