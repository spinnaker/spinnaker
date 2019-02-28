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

import com.microsoft.azure.management.network.implementation.SubnetInner
import com.microsoft.azure.management.network.implementation.VirtualNetworkInner
import com.netflix.spinnaker.clouddriver.azure.common.AzureUtilities
import com.netflix.spinnaker.clouddriver.azure.resources.common.AzureResourceOpsDescription
import groovy.transform.CompileStatic

@CompileStatic
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

  static AzureSubnetDescription getDescriptionForAzureSubnet(VirtualNetworkInner vnet, SubnetInner subnet) {
    AzureSubnetDescription description = new AzureSubnetDescription(name: subnet.name())
    description.name = subnet.name()
    description.region = vnet.location()
    description.cloudProvider = "azure"
    description.vnet = vnet.name()
    description.resourceId = subnet.id()
    description.id = subnet.name()
    description.addressPrefix = subnet.addressPrefix()
    description.ipv4 = AzureUtilities.convertIpv4PrefixToInt(subnet.addressPrefix())

    description.addressPrefixLength = AzureUtilities.getAddressPrefixLength(subnet.addressPrefix())
    subnet.ipConfigurations()?.each {resourceId ->
      description.ipConfigurations += resourceId.id()
      description.connectedDevices += new SubnetConnectedDevices(
        name: AzureUtilities.getResourceNameFromId(resourceId.id()),
        resourceId: resourceId.id(),
        type: AzureUtilities.getResourceTypeFromId(resourceId.id())
      )
      // TODO: iterate through applicationGatewayIPConfigurations which contains the ApplicationGateway related associations
      // This property is not yet exposed in the current Azure Java SDK
    }
    description.networkSecurityGroup = subnet.networkSecurityGroup()?.id()

    description
  }

  static Collection<AzureSubnetDescription> getSubnetsForVirtualNetwork(VirtualNetworkInner vnet) {
    // sort the list of subnet based on their ivp4 vals in order to speed the search when computing the next subnet
    vnet.subnets()?.collect {
      getDescriptionForAzureSubnet(vnet, it)
    }?.sort { a,b -> a.ipv4 <=> b.ipv4}
  }

  static class SubnetConnectedDevices {
    String name
    String type
    String resourceId
  }
}
