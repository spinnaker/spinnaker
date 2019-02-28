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

package com.netflix.spinnaker.clouddriver.azure.resources.network.model

import com.microsoft.azure.management.network.implementation.VirtualNetworkInner
import com.netflix.spinnaker.clouddriver.azure.common.AzureUtilities
import com.netflix.spinnaker.clouddriver.azure.resources.common.AzureResourceOpsDescription
import com.netflix.spinnaker.clouddriver.azure.resources.subnet.model.AzureSubnetDescription
import groovy.transform.CompileStatic

@CompileStatic
class AzureVirtualNetworkDescription extends AzureResourceOpsDescription {
  String id
  String type
  List<String> addressSpace /* see addressPrefix */
  String resourceId /* Azure resource ID */
  String resourceGroup /* the Azure resource group where virtual network was created */
  Map<String, String> tags
  List<AzureSubnetDescription> subnets
  int maxSubnets
  int subnetAddressPrefixLength

  static AzureVirtualNetworkDescription getDescriptionForVirtualNetwork(VirtualNetworkInner vnet) {
    if (!vnet) {
      return null
    }

    AzureVirtualNetworkDescription description = new AzureVirtualNetworkDescription()
    description.name = vnet.name()
    description.region = vnet.location()
    // TODO We assume that the vnet first address space matters; we'll revise this later if we need to support more then one
    description.addressSpace = vnet.addressSpace()?.addressPrefixes()
    description.subnets = AzureSubnetDescription.getSubnetsForVirtualNetwork(vnet)?.toList()
    description.resourceId = vnet.id()
    description.resourceGroup = AzureUtilities.getResourceGroupNameFromResourceId(vnet.id())
    description.id = vnet.name()
    description.tags = vnet.getTags()
    description.subnetAddressPrefixLength = description.subnets?.min {it.addressPrefixLength}?.addressPrefixLength ?: AzureUtilities.SUBNET_DEFAULT_ADDRESS_PREFIX_LENGTH
    description.maxSubnets = AzureUtilities.getSubnetRangeMax(
      description.addressSpace?.first(),
      description.subnetAddressPrefixLength
    )

    description
  }

  static String getNextSubnetAddressPrefix(AzureVirtualNetworkDescription vnet, int seed) {
    // We generate a random number within the max range of address prefixies for the given vnet
    // This random number is the seed to calculate the next subnet address prefix (and later the name of the subnet)

    if (!vnet?.maxSubnets || vnet.subnets.size() >= vnet.maxSubnets) {
      return null
    }

    int vnetIpv4 = AzureUtilities.convertIpv4PrefixToInt(vnet?.addressSpace?.first())

    if (vnetIpv4 <= 0) {
      return null
    }

    long leftShift = 32 - vnet.subnetAddressPrefixLength
    int nextIpv4 = vnetIpv4 | (seed << leftShift)
    int loopCount = 0

    while (loopCount < vnet.maxSubnets && vnet.subnets.find {it.ipv4 == nextIpv4}) {
      seed = (seed + 1) % vnet.maxSubnets
      nextIpv4 = vnetIpv4 | (seed << leftShift)
      loopCount += 1
    }

    AzureUtilities.convertIntToIpv4Prefix(nextIpv4, vnet.subnetAddressPrefixLength)
  }
}
