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

package com.netflix.spinnaker.clouddriver.azure.resources.network.model

import com.fasterxml.jackson.core.util.DefaultIndenter
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.microsoft.azure.management.network.implementation.VirtualNetworkInner
import spock.lang.Shared
import spock.lang.Specification

class AzureVirtualNetworkDescriptionSpec extends Specification {

  @Shared
  ObjectMapper mapper = new ObjectMapper()

  VirtualNetworkInner vnet

  void "Create a simple AzureVirtualNetworkDescription from a given input"() {
    setup:
    mapper.configure(SerializationFeature.INDENT_OUTPUT, true)
    mapper.configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false)

    def input = [
      name: "vnet-test-westus",
      location: "westus",
      id: "vnet-test-westus-id",
    ]
    def vnet = mapper.convertValue(input, VirtualNetworkInner) as VirtualNetworkInner

    when:
    def vnetDescription = AzureVirtualNetworkDescription.getDescriptionForVirtualNetwork(vnet)

    then:
    vnetDescription instanceof AzureVirtualNetworkDescription
    mapper.writeValueAsString(vnetDescription).replace('\r', '') == expectedSimpleDescription
  }

  void "Create a full AzureVirtualNetworkDescription from a given input and calculate next subnet address prefix"() {
    setup:
    mapper.configure(SerializationFeature.INDENT_OUTPUT, true)
    mapper.configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false)

    def input = [
      name: "vnet-test-westus",
      location: "westus",
      id: "vnet-test-westus-id",
      "properties.addressSpace": [
        addressPrefixes: ["10.0.0.0/8"]
      ],
      "properties.subnets": [
        [
          name: "vnet-test-westus-subnet-10_0_1_0_24",
          "properties.addressPrefix": "10.0.1.0/24"
        ],
        [
          name: "vnet-test-westus-subnet-10_0_2_0_24",
          "properties.addressPrefix": "10.0.2.0/24"
        ],
        [
          name: "vnet-test-westus-subnet-10_0_30_0_24",
          "properties.addressPrefix": "10.0.30.0/24"
        ]
      ],
    ]
    def vnet = mapper.convertValue(input, VirtualNetworkInner) as VirtualNetworkInner

    when:
    def vnetDescription = AzureVirtualNetworkDescription.getDescriptionForVirtualNetwork(vnet)
    def nextSubnetAddressPrefix1 = AzureVirtualNetworkDescription.getNextSubnetAddressPrefix(vnetDescription, 1)
    def nextSubnetAddressPrefix2 = AzureVirtualNetworkDescription.getNextSubnetAddressPrefix(vnetDescription, 30)
    def nextSubnetAddressPrefix3 = AzureVirtualNetworkDescription.getNextSubnetAddressPrefix(vnetDescription, 10)

    then:
    vnetDescription instanceof AzureVirtualNetworkDescription
    mapper.writeValueAsString(vnetDescription).replace('\r', '') == expectedFullDescription
    nextSubnetAddressPrefix1 == "10.0.3.0/24"
    nextSubnetAddressPrefix2 == "10.0.31.0/24"
    nextSubnetAddressPrefix3 == "10.0.10.0/24"
  }

  private static String expectedSimpleDescription = '''{
  "name" : "vnet-test-westus",
  "cloudProvider" : null,
  "accountName" : null,
  "appName" : null,
  "stack" : null,
  "detail" : null,
  "credentials" : null,
  "region" : "westus",
  "user" : null,
  "createdTime" : null,
  "lastReadTime" : 0,
  "tags" : null,
  "id" : "vnet-test-westus",
  "type" : null,
  "addressSpace" : null,
  "resourceId" : "vnet-test-westus-id",
  "resourceGroup" : null,
  "subnets" : null,
  "maxSubnets" : 0,
  "subnetAddressPrefixLength" : 24
}'''

  private static String expectedFullDescription = '''{
  "name" : "vnet-test-westus",
  "cloudProvider" : null,
  "accountName" : null,
  "appName" : null,
  "stack" : null,
  "detail" : null,
  "credentials" : null,
  "region" : "westus",
  "user" : null,
  "createdTime" : null,
  "lastReadTime" : 0,
  "tags" : null,
  "id" : "vnet-test-westus",
  "type" : null,
  "addressSpace" : [ "10.0.0.0/8" ],
  "resourceId" : "vnet-test-westus-id",
  "resourceGroup" : null,
  "subnets" : [ {
    "name" : "vnet-test-westus-subnet-10_0_1_0_24",
    "cloudProvider" : "azure",
    "accountName" : null,
    "appName" : null,
    "stack" : null,
    "detail" : null,
    "credentials" : null,
    "region" : "westus",
    "user" : null,
    "createdTime" : null,
    "lastReadTime" : 0,
    "tags" : { },
    "id" : "vnet-test-westus-subnet-10_0_1_0_24",
    "addressPrefix" : "10.0.1.0/24",
    "resourceId" : null,
    "ipConfigurations" : [ ],
    "networkSecurityGroup" : null,
    "connectedDevices" : [ ],
    "vnet" : "vnet-test-westus",
    "ipv4" : 167772416,
    "addressPrefixLength" : 24
  }, {
    "name" : "vnet-test-westus-subnet-10_0_2_0_24",
    "cloudProvider" : "azure",
    "accountName" : null,
    "appName" : null,
    "stack" : null,
    "detail" : null,
    "credentials" : null,
    "region" : "westus",
    "user" : null,
    "createdTime" : null,
    "lastReadTime" : 0,
    "tags" : { },
    "id" : "vnet-test-westus-subnet-10_0_2_0_24",
    "addressPrefix" : "10.0.2.0/24",
    "resourceId" : null,
    "ipConfigurations" : [ ],
    "networkSecurityGroup" : null,
    "connectedDevices" : [ ],
    "vnet" : "vnet-test-westus",
    "ipv4" : 167772672,
    "addressPrefixLength" : 24
  }, {
    "name" : "vnet-test-westus-subnet-10_0_30_0_24",
    "cloudProvider" : "azure",
    "accountName" : null,
    "appName" : null,
    "stack" : null,
    "detail" : null,
    "credentials" : null,
    "region" : "westus",
    "user" : null,
    "createdTime" : null,
    "lastReadTime" : 0,
    "tags" : { },
    "id" : "vnet-test-westus-subnet-10_0_30_0_24",
    "addressPrefix" : "10.0.30.0/24",
    "resourceId" : null,
    "ipConfigurations" : [ ],
    "networkSecurityGroup" : null,
    "connectedDevices" : [ ],
    "vnet" : "vnet-test-westus",
    "ipv4" : 167779840,
    "addressPrefixLength" : 24
  } ],
  "maxSubnets" : 65536,
  "subnetAddressPrefixLength" : 24
}'''

}
