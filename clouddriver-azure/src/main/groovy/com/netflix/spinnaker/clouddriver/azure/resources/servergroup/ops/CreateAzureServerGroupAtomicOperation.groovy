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
import com.netflix.spinnaker.clouddriver.azure.common.AzureUtilities
import com.netflix.spinnaker.clouddriver.azure.resources.common.model.AzureDeploymentOperation
import com.netflix.spinnaker.clouddriver.azure.resources.servergroup.model.AzureServerGroupDescription
import com.netflix.spinnaker.clouddriver.azure.templates.AzureServerGroupResourceTemplate
import com.netflix.spinnaker.clouddriver.data.task.Task
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperationException

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
    task.updateStatus(BASE_PHASE, "Initializing deployment of server group ${description.name} " +
      "in ${description.region}...")

    def errList = new ArrayList<String>()
    String resourceGroupName = null
    String virtualNetworkName = null
    String subnetName = null
    String serverGroupName = null
    String appGatewayPoolID = null

    try {

      task.updateStatus(BASE_PHASE, "Beginning server group deployment")

      resourceGroupName = AzureUtilities.getResourceGroupName(description.application, description.region)
      virtualNetworkName = AzureUtilities.getVirtualNetworkName(resourceGroupName)

      description.credentials.resourceManagerClient.initializeResourceGroupAndVNet(description.credentials, resourceGroupName, virtualNetworkName, description.region)

      // TODO We just try to grab the next subnet, which fails if the largest possible subnet is already taken.
      // TODO We also just assume that a vnet can only have one address range.
      task.updateStatus(BASE_PHASE, "Creating subnet for server group")
      def vnet = description.credentials.networkClient.getVirtualNetwork(resourceGroupName, virtualNetworkName)
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
      subnetName = AzureUtilities.getSubnetName(virtualNetworkName, nextSubnet)
      String subnetId = description.credentials.networkClient.createSubnet(resourceGroupName,
        virtualNetworkName,
        subnetName,
        nextSubnet,
        description.securityGroup?.name)

      AzureServerGroupNameResolver nameResolver = new AzureServerGroupNameResolver(description.accountName, description.region, description.credentials)
      description.name = nameResolver.resolveNextServerGroupName(description.application, description.stack, description.detail, false)
      description.clusterName = description.getClusterName()
      description.appName = description.application

      // get the app gateway. Verify that it can be used for this server group/cluster. add an app pool if it doesn't already exist
      // TODO: replace appGatewayName with loadBalancerName
      if (!description.appGatewayName) {
        description.appGatewayName = description.loadBalancerName
      }
      appGatewayPoolID = description.credentials
        .networkClient
        .createAppGatewayBAPforServerGroup(resourceGroupName, description.appGatewayName, description.name)

      if (!appGatewayPoolID) {
        throw new RuntimeException("Selected Load Balancer $description.appGatewayName does not exist")
      }

      // TODO: Debug only; can be removed as part of tags cleanup
      description.appGatewayBapId = appGatewayPoolID

      // if this is not a custom image, then we need to go get the OsType from Azure
      if (!description.image.isCustom) {

        def virtualMachineImage = description.credentials.computeClient.getVMImage(description.region,
          description.image.publisher, description.image.offer, description.image.sku, description.image.version)
        description.image.imageName ?: virtualMachineImage.name
        description.image.ostype = virtualMachineImage?.osDiskImage?.operatingSystem
      }

      // If Linux, set up connection on port 22 (ssh) otherwise use port 3389 (rdp)
      def backendPort = description.image.ostype.toLowerCase() == "linux" ? 22 : 3389
      description.addInboundPortConfig(AzureUtilities.INBOUND_NATPOOL_PREFIX + description.name, 50000, 50099, "tcp", backendPort)

      if (errList.isEmpty()) {
        description.securityGroupName = description.securityGroup?.name
        description.subnetId = subnetId
        task.updateStatus(BASE_PHASE, "Deploying server group")
        DeploymentExtended deployment = description.credentials.resourceManagerClient.createResourceFromTemplate(description.credentials,
          AzureServerGroupResourceTemplate.getTemplate(description),
          resourceGroupName,
          description.region,
          description.name,
          "serverGroup",
          [
            subnetId: subnetId,
            appGatewayAddressPoolID: appGatewayPoolID
          ])

        errList.addAll(AzureDeploymentOperation.checkDeploymentOperationStatus(task, BASE_PHASE, description.credentials, resourceGroupName, deployment.name))
        serverGroupName = errList.isEmpty() ? description.name : null
      }
    } catch (Exception e) {
      task.updateStatus(BASE_PHASE, "Deployment of server group ${description.name} failed: ${e.message}")
      errList.add(e.message)
    }
    if (errList.isEmpty()) {
      task.updateStatus(BASE_PHASE, "Deployment for server group ${description.name} in ${description.region} has succeeded.")
    }
    else {
      if (description.image?.imageName) {
        // cleanup any resources that might have been created prior to server group failing to deploy
        task.updateStatus(BASE_PHASE, "Cleanup any resources created as part of server group upsert")
        try {
          if (serverGroupName) {
            description.credentials
              .computeClient
              .destroyServerGroup(resourceGroupName, serverGroupName)
          }
          if (appGatewayPoolID) {
            description.credentials
              .networkClient
              .removeAppGatewayBAPforServerGroup(resourceGroupName, description.appGatewayName, description.name)
          }
          if (subnetName) {
            description.credentials
              .networkClient
              .deleteSubnet(resourceGroupName, virtualNetworkName, subnetName)
          }
        } catch (Exception e) {
          def errMessage = "Unexpected exception: ${e.message}; please log in into the Azure Portal and manually remove the following resources: ${subnetName}"
          task.updateStatus(BASE_PHASE, errMessage)
          errList.add(errMessage)
        }
      } else {
        // can not automatically delete a server group
        errList.add("Please log in into Azure Portal and manualy delete any resource associated with the ${description.name} server group such as storage accounts and subnets (${subnetName})")
      }

      throw new AtomicOperationException(
        error: "${description.name} deployment failed",
        errors: errList)
    }

    [serverGroups: [(description.region): [name: description.name]],
    serverGroupNames: ["${description.region}:${description.name}".toString()]]
  }
}
