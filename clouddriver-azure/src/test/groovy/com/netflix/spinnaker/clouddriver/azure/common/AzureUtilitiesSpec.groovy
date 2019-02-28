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

package com.netflix.spinnaker.clouddriver.azure.common

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.netflix.spinnaker.clouddriver.azure.resources.common.model.KeyVaultSecret
import com.netflix.spinnaker.clouddriver.azure.templates.AzureServerGroupResourceTemplate
import spock.lang.Specification

class AzureUtilitiesSpec extends Specification {
  ObjectMapper objectMapper

  void setup() {
    objectMapper = new ObjectMapper().configure(SerializationFeature.INDENT_OUTPUT, true)
    objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
  }

  def "CompareIpv4AddrPrefixes == 0"() {
    expect:
    AzureUtilities.compareIpv4AddrPrefixes(left, right) == 0

    where:
    left                  | right
    '255.255.255.254/31'  | '255.255.255.254/31'
    '0.0.0.0/24'          | '0.0.0.0/24'
    '10.0.0.1/24'         | '10.0.0.2/24'
  }

  def "CompareIpv4AddrPrefixes > 0"() {
    expect:
    AzureUtilities.compareIpv4AddrPrefixes(left, right) > 0

    where:
    left                  | right
    '10.0.2.0/24'         | '10.0.1.0/24'
    '255.255.255.0/24'    | '10.0.1.0/24'
    '255.255.255.0/24'    | '255.255.254.0/24'
  }

  def "CompareIpv4AddrPrefixes < 0"() {
    expect:
    AzureUtilities.compareIpv4AddrPrefixes(left, right) < 0

    where:
    left                  | right
    '10.0.1.0/24'         | '10.0.2.0/24'
    '10.0.1.0/24'         | '255.255.255.0/24'
    '255.255.254.0/24'    | '255.255.255.0/24'
  }

  def "CompareIpv4AddrPrefixes => IllegalArgumentException"() {
    when:
    AzureUtilities.compareIpv4AddrPrefixes(left, right)

    then:
    thrown(IllegalArgumentException)

    where:
    left                  | right
    ''                    | '10.0.1.0/24'
    '10.0.1.0/24'         | ''
    '256.0.1.0/24'        | '10.0.1.0/24'
    '10.0.1.0/24'         | '10.0.256.0/24'
    '10.0.1.0/33'         | '10.0.1.0/24'
    '10.0.1.0/24'         | '10.0.1.0/33'
  }

  def "GetNextSubnet"() {
    expect:
    AzureUtilities.getNextSubnet(vnet, subnet) == next

    where:
    vnet                  | subnet                | next
    '10.0.0.0/16'         | '10.0.1.0/24'         | '10.0.2.0/24'
    '10.0.0.0/8'          | '10.0.255.0/24'       | '10.1.0.0/24'
    '10.0.0.0/8'          | '10.1.0.0/16'         | '10.2.0.0/16'
    '10.0.0.0/16'         | '10.0.0.0/24'         | '10.0.1.0/24'
    '128.0.0.0/1'         | '128.255.0.0/16'      | '129.0.0.0/16'
    '10.0.0.0/16'         | ''                    | '10.0.1.0/24'
    '10.0.0.0/16'         | null                  | '10.0.1.0/24'

  }

  def "GetNextSubnet => IllegalArgumentException"() {
    when:
    AzureUtilities.getNextSubnet(vnet, subnet)

    then:
    thrown(IllegalArgumentException)

    where:
    vnet                  | subnet
    ''                    | '10.0.1.0/24'
    '256.0.1.0/24'        | '10.0.1.0/24'
    '10.0.0.0/16'         | '10.0.256.0/24'
    '10.0.0.0/33'         | '10.0.1.0/24'
    '10.0.0.0/24'         | '10.0.1.0/33'
    '10.0.0.0/16'         | '10.1.1.0/24'
  }

  def "GetNextSubnet => Overflow"() {
    when:
    AzureUtilities.getNextSubnet(vnet, subnet)

    then:
    thrown(ArithmeticException)

    where:
    vnet                  | subnet
    '10.0.0.0/16'         | '10.0.255.0/24'
    '10.0.0.0/8'          | '10.255.0.0/16'
    '10.0.0.0/8'          | '10.255.255.0/24'
    '255.0.0.0/8'         | '255.255.255.0/24'
    '254.255.255.0/24'    | '254.255.255.0/24'
  }

  def "getSubnetRangeMax"() {
    expect:
    AzureUtilities.getSubnetRangeMax(vnet, 24) == rangeMax

    where:
    vnet                  | rangeMax
    null                  | 0
    ''                    | 0
    '10.0.0.0/25'         | 0
    '10.0.0.0/8'          | 0x10000
    '10.0.0.0/16'         | 0x100
    '10.0.0.0/23'         | 2
    '255.0.0.0/8'         | 0x10000
    '254.255.255.0/24'    | 0
  }

  def "convertIpv4PrefixToInt"() {
    expect:
    AzureUtilities.convertIpv4PrefixToInt(addrPrefix) == val

    where:
    addrPrefix            | val
    null                  | -1
    ''                    | -1
    '10.0.0.0/24'         | 0x0A000000
    '10.0.10.0/24'        | 0x0A000A00
    '10.0.0.0/8'          | 0x0A000000
    '10.0.0.0/16'         | 0x0A000000
    '255.0.0.0/8'         | -16777216
    '255.255.0.0/16'      | -65536
  }

  def "getAddressPrefixLength"() {
    expect:
    AzureUtilities.getAddressPrefixLength(addrPrefix) == val

    where:
    addrPrefix            | val
    null                  | 0
    ''                    | 0
    '10.0.0.0/24'         | 24
    '10.0.10.0/24'        | 24
    '10.0.0.0/8'          | 8
    '10.0.0.0/16'         | 16
    '255.0.0.0/8'         | 8
    '255.255.0.0/16'      | 16
  }

  def "getResourceNameFromId"() {
    expect:
    AzureUtilities.getResourceNameFromId(resourceId) == val

    where:
    resourceId            | val
    null                  | null
    ''                    | null
    '/'                   | null
    'someText'            | null
    '/subscriptions/***-***-***/resourceGroups/***/providers/Microsoft.Network/networkInterfaces/nic1/ipConfigurations/ipconfig1'          | 'nic1'
    '/subscriptions/***-***-***/resourceGroups/***/providers/Microsoft.Compute/virtualMachineScaleSets/vmss000/virtualMachines/0/networkInterfaces/nic1/ipConfigurations/ipc1'   | 'vmss000'
  }

  def "getResourceTypeFromId"() {
    expect:
    AzureUtilities.getResourceTypeFromId(resourceId) == val

    where:
    resourceId            | val
    null                  | null
    ''                    | null
    '/'                   | null
    'someText'            | null
    '/subscriptions/***-***-***/resourceGroups/***/providers/Microsoft.Network/networkInterfaces/nic1/ipConfigurations/ipconfig1'                                              | 'networkInterfaces'
    '/subscriptions/***-***-***/resourceGroups/***/providers/Microsoft.Compute/virtualMachineScaleSets/vmss000/virtualMachines/0/networkInterfaces/nic1/ipConfigurations/ipc1' | 'virtualMachineScaleSets'
  }

  def 'verify parameters JSON'() {

    def parameters = [:]
    parameters[AzureServerGroupResourceTemplate.subnetParameterName] = subnetId
    parameters[AzureServerGroupResourceTemplate.vmPasswordParameterName] = new KeyVaultSecret(secretName, subscriptionId, defaultResourceGroup, defaultVaultName)
    String parametersJSON = AzureUtilities.convertParametersToTemplateJSON(objectMapper, parameters)

    expect: parametersJSON.replace('\r', '') == expectedParameters
  }

  private static String expectedParameters = """{
  "subnetId" : {
    "value" : "$subnetId"
  },
  "vmPassword" : {
    "reference" : {
      "keyVault" : {
        "id" : "/subscriptions/$subscriptionId/resourceGroups/$defaultResourceGroup/providers/Microsoft.KeyVault/vaults/$defaultVaultName"
      },
      "secretName" : "$secretName"
    }
  }
}"""

  private static final String subscriptionId = "testSubscriptionID"
  private static final String subnetId = "SubNetTestID"
  private static final String defaultResourceGroup = "defaultResourceGroup"
  private static final String defaultVaultName = "defaultKeyVault"
  private static final String secretName = "VMPassword"
}
