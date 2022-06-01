/*
 * Copyright 2021 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
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
import com.netflix.spinnaker.clouddriver.azure.resources.common.model.KeyVaultSecret
import com.netflix.spinnaker.clouddriver.azure.resources.network.view.AzureNetworkProvider
import com.netflix.spinnaker.clouddriver.azure.resources.servergroup.model.AzureServerGroupDescription
import com.netflix.spinnaker.clouddriver.azure.templates.AzureServerGroupResourceTemplate
import com.netflix.spinnaker.clouddriver.data.task.Task
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperationException
import org.springframework.beans.factory.annotation.Autowired

import static com.netflix.spinnaker.clouddriver.azure.resources.servergroup.ops.CreateAzureServerGroupAtomicOperation.*

class CreateAzureServerGroupWithoutLoadBalancersAtomicOperation implements AtomicOperation<Map> {
  private static final String BASE_PHASE = "CREATE_SERVER_GROUP"

  private static Task getTask() {
    TaskRepository.threadLocalTask.get()
  }

  private final AzureServerGroupDescription description

  @Autowired
  AzureNetworkProvider networkProvider

  CreateAzureServerGroupWithoutLoadBalancersAtomicOperation(AzureServerGroupDescription description) {
    this.description = description
  }

  @Override
  Map operate(List<Map> priorOutputs) {
    def errList = new ArrayList<String>()
    String resourceGroupName = null
    String virtualNetworkName = null
    String subnetName = null
    String subnetId
    String serverGroupName = null

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
      // Create corresponding ResourceGroup if it's not created already
      description.credentials.resourceManagerClient.initializeResourceGroupAndVNet(resourceGroupName, null, description.region)

      virtualNetworkName = description.vnet

      def vnetDescription = networkProvider.get(description.accountName, description.region, description.vnetResourceGroup, virtualNetworkName)

      if (!vnetDescription) {
        throw new RuntimeException("Selected virtual network $virtualNetworkName does not exist")
      }

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

      AzureServerGroupNameResolver nameResolver = new AzureServerGroupNameResolver(description.accountName, description.region, description.credentials)
      description.name = nameResolver.resolveNextServerGroupName(description.application, description.stack, description.detail, false)
      description.clusterName = description.getClusterName()
      description.appName = description.application
      description.vnet = virtualNetworkName
      description.subnet = subnetName

      Map<String, Object> templateParameters = [:]

      templateParameters[AzureServerGroupResourceTemplate.subnetParameterName] = subnetId
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
        String template = AzureServerGroupResourceTemplate.getTemplate(description)
        Deployment deployment = description.credentials.resourceManagerClient.createResourceFromTemplate(
          template,
          resourceGroupName,
          description.region,
          description.name,
          "serverGroup",
          templateParameters)

        def healthy = description.credentials.computeClient.waitForScaleSetHealthy(resourceGroupName, description.name, SERVER_WAIT_TIMEOUT);

        if (healthy) {
          getTask().updateStatus(BASE_PHASE, String.format(
            "Done enabling Azure server group %s in %s.",
            description.getName(), description.getRegion()))
        } else {
          errList.add("Server group did not come up in time")
        }

        errList.addAll(AzureDeploymentOperation.checkDeploymentOperationStatus(task, BASE_PHASE, description.credentials, resourceGroupName, deployment.name()))
        serverGroupName = errList.isEmpty() ? description.name : null
      }
    } catch (Exception e) {
      task.updateStatus(BASE_PHASE, "Unexpected exception: Deployment of server group ${description.name} failed: ${e.message}")
      errList.add(e.message)
    }
    if (errList.isEmpty()) {
      // There is no concept of "disabled" for a server group that is not fronted by a load balancer.
      // Because of that, we leave it up to the user to decide how to handle it via their pipeline
      // (either resize/disable or destroy)

      task.updateStatus(BASE_PHASE, "Deployment for server group ${description.name} in ${description.region} has succeeded.")
    } else {
      // cleanup any resources that might have been created prior to server group failing to deploy
      task.updateStatus(BASE_PHASE, "Cleanup any resources created as part of server group upsert")
      try {
        if (description.name) {
          def sgDescription = description.credentials
            .computeClient
            .getServerGroup(resourceGroupName, description.name)
          if (sgDescription) {
            description.credentials
              .computeClient
              .destroyServerGroup(resourceGroupName, description.name)

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
            .deleteSubnet(description.vnetResourceGroup, virtualNetworkName, subnetName)
        }
      } catch (Exception e) {
        def errMessage = "Unexpected exception: ${e.message}! Please log in into Azure Portal and manually delete any resource associated with the ${description.name} server group such as storage accounts, internal load balancer, public IP and subnets"
        task.updateStatus(BASE_PHASE, errMessage)
        errList.add(errMessage)
      }

      throw new AtomicOperationException("${description.name} deployment failed", errList)
    }

    [serverGroups: [(description.region): [name: description.name]],
     serverGroupNames: ["${description.region}:${description.name}".toString()]]
  }
}
