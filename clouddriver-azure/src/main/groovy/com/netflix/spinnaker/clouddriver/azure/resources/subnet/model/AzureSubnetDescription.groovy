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

package com.netflix.spinnaker.clouddriver.azure.resources.subnet.model

import com.microsoft.azure.management.network.models.ApplicationGateway
import com.microsoft.azure.management.network.models.Subnet
import com.microsoft.azure.management.network.models.VirtualNetwork
import com.netflix.spinnaker.clouddriver.azure.common.AzureUtilities
import com.netflix.spinnaker.clouddriver.azure.resources.common.AzureResourceOpsDescription
import com.netflix.spinnaker.clouddriver.azure.resources.network.model.AzureVirtualNetworkDescription

import java.util.regex.Pattern

class AzureSubnetDescription extends AzureResourceOpsDescription {
  String id = "unknown"
  String addressPrefix = "unknown"
  String resourceId /*Azure resource ID*/
  List<String> ipConfigurations = []
  String networkSecurityGroup
  List<SubnetConnectedDevices> connectedDevices = []

  String vnet = "unknown"
  int ipv4
  int addressPrefixLength

  static AzureSubnetDescription getDescriptionForAzureSubnet(VirtualNetwork vnet, Subnet subnet) {
    AzureSubnetDescription description = new AzureSubnetDescription(name: subnet.name)
    description.name = subnet.name
    description.region = vnet.location
    description.cloudProvider = "azure"
    description.vnet = vnet.name
    description.resourceId = subnet.id
    description.id = subnet.name
    description.addressPrefix = subnet.addressPrefix
    description.ipv4 = AzureUtilities.convertIpv4PrefixToInt(subnet.addressPrefix)

    description.addressPrefixLength = AzureUtilities.getAddressPrefixLength(subnet.addressPrefix)
    subnet.ipConfigurations?.each {resourceId ->
      description.ipConfigurations += resourceId.id
      description.connectedDevices += new SubnetConnectedDevices(
        name: AzureUtilities.getResourceNameFromId(resourceId.id),
        resourceId: resourceId.id,
        type: AzureUtilities.getResourceTypeFromId(resourceId.id)
      )
      // TODO: iterate through applicationGatewayIPConfigurations which contains the ApplicationGateway related associations
      // This property is not yet exposed in the current Azure Java SDK
    }
    description.networkSecurityGroup = subnet.networkSecurityGroup?.id

    description
  }

  static Collection<AzureSubnetDescription> getSubnetsForVirtualNetwork(VirtualNetwork vnet) {
    // sort the list of subnet based on their ivp4 vals in order to speed the search when computing the next subnet
    vnet.subnets?.collect {
      getDescriptionForAzureSubnet(vnet, it)
    }?.sort { a,b -> a.ipv4 <=> b.ipv4}
  }

  // This is a temporary workaround for a missing API in Azure Java SDK which should retrieve any Azure ApplicationGateway
  //   associations with the subnet via applicationGatewayIPConfigurations is captured in the Azure Subnet. It should be
  //   later replaced by a simple iteration through applicationGatewayIPConfigurations once that property becomes available
  //   in the Azure JSDK
  static void getAppGatewaysConnectedResources(AzureVirtualNetworkDescription vnet, List<ApplicationGateway> appGateways) {
    if (vnet.subnets && appGateways) {
      appGateways.each { appGateway ->
        // Iterate through the gatewayIPConfigurations and extract the subnet id which will be compared with the subnets within the vnet
        appGateway?.gatewayIPConfigurations?.each { gatewayConfig ->
          def subnetDescription = vnet.subnets.find { it.resourceId == gatewayConfig?.subnet?.id }
          if (subnetDescription) {
            subnetDescription.connectedDevices += new SubnetConnectedDevices(
              name: appGateway.name,
              resourceId: gatewayConfig?.id,
              type: "applicationGateways",
            )
          }
        }
      }
    }
  }

  static class SubnetConnectedDevices {
    String name
    String type
    String resourceId
  }
}
