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

package com.netflix.spinnaker.clouddriver.azure.common

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.clouddriver.azure.resources.common.AzureResourceOpsDescription

import java.util.regex.Matcher
import java.util.regex.Pattern

class AzureUtilities {

  static final String PATH_SEPARATOR = "/"
  static final String NAME_SEPARATOR = "-"
  static final String VNET_NAME_PREFIX = "vnet-"
  static final String SUBNET_NAME_PREFIX = "subnet-"
  static final String PUBLICIP_NAME_PREFIX = "pip-"
  static final String LBFRONTEND_NAME_PREFIX = "fe-"
  static final String LBBACKEND_NAME_PREFIX = "be-"
  static final String DNS_NAME_PREFIX = "dns-"
  static final String IPCONFIG_NAME_PREFIX = "ipc-"
  static final String NETWORK_INTERFACE_PREFIX = "nic-"
  static final Pattern IPV4_PREFIX_REGEX = ~/^(?<addr3>\d+)\.(?<addr2>\d+)\.(?<addr1>\d+)\.(?<addr0>\d+)\/(?<length>\d+)$/
  static final String LB_NAME_PREFIX = "lb-"
  static final String INBOUND_NATPOOL_PREFIX = "np-"
  static final String VNET_DEFAULT_ADDRESS_PREFIX = "10.0.0.0/8"
  static final int SUBNET_DEFAULT_ADDRESS_PREFIX_LENGTH = 24
  static final int PROVIDER_TYPE_INDEX_IN_RESOURCEID = 6
  static final String AZURE_CUSTOM_SCRIPT_EXT_TYPE_LINUX="CustomScript"
  static final String AZURE_CUSTOM_SCRIPT_EXT_VERSION_LINUX="2.0"
  static final String AZURE_CUSTOM_SCRIPT_EXT_PUBLISHER_LINUX="Microsoft.Azure.Extensions"
  static final String AZURE_CUSTOM_SCRIPT_EXT_TYPE_WINDOWS="CustomScriptExtension"
  static final String AZURE_CUSTOM_SCRIPT_EXT_PUBLISHER_WINDOWS="Microsoft.Compute"
  static final String AZURE_CUSTOM_SCRIPT_EXT_VERSION_WINDOWS="1.8"

  static String getResourceGroupName(AzureResourceOpsDescription description) {
    if (description == null) {
      return null
    }
    description.appName + NAME_SEPARATOR + description.region
  }

  static String getVirtualNetworkName(String resourceGroupName) {
    if (resourceGroupName == null) {
      return null
    }
    VNET_NAME_PREFIX + resourceGroupName
  }

  static String getSubnetName(String virtualNetworkName, String addressPrefix) {
    if (virtualNetworkName == null || addressPrefix == null) {
      return null
    }

    String addressPrefixSanitized = addressPrefix.replaceAll('[\\./]', '_')
    virtualNetworkName + NAME_SEPARATOR + SUBNET_NAME_PREFIX + addressPrefixSanitized
  }

  static String getResourceGroupName(String appName, String region) {
    if (appName == null || region == null) {
      return null
    }
    appName + NAME_SEPARATOR + region.replace(' ', '').toLowerCase()
  }

  static String getResourceGroupNameFromResourceId(String resourceId) {
    if (resourceId == null) {
      return null
    }

    def parts = resourceId.split(PATH_SEPARATOR)
    def idx = parts.findIndexOf {it == "resourceGroups"}

    if (idx > 0) {
      return parts[idx + 1]
    } else {
      return null
    }
  }

  static String getAppNameFromAzureResourceName(String azureResourceName) {
    if (azureResourceName == null) {
      return null
    }

    azureResourceName.split(NAME_SEPARATOR).first()
  }

  // For resourceId = "/subscriptions/***-***-***/resourceGroups/***/providers/Microsoft.Network/networkInterfaces/nic1"
  //   this method will return "nic1"
  static String getNameFromResourceId(String resourceId) {
    if (resourceId == null) {
      return null
    }

    resourceId.split(PATH_SEPARATOR).last()
  }

  // For id = "/subscriptions/***-***-***/resourceGroups/***/providers/Microsoft.Network/networkInterfaces/nic1/ipConfigurations/ipconfig1"
  //   this method return "nic1"
  static String getResourceNameFromId(String id) {
    if (id == null) {
      return null
    }

    def vals = id.split(PATH_SEPARATOR)

    if (vals.length > PROVIDER_TYPE_INDEX_IN_RESOURCEID + 2) {
      return vals[PROVIDER_TYPE_INDEX_IN_RESOURCEID + 2] // see vals.findIndexOf { it == "Microsoft.Network"} + 2
    } else {
      return null
    }
  }

  // For id = "/subscriptions/***-***-***/resourceGroups/***/providers/Microsoft.Network/networkInterfaces/nic1/ipConfigurations/ipconfig1"
  //   this method will return "networkInterfaces"
  static String getResourceTypeFromId(String id) {
    if (id == null) {
      return null
    }

    def vals = id.split(PATH_SEPARATOR)
    if (vals.length > PROVIDER_TYPE_INDEX_IN_RESOURCEID + 1) {
      return vals[PROVIDER_TYPE_INDEX_IN_RESOURCEID + 1] // see vals.findIndexOf { it == "Microsoft.Network"} + 1
    } else {
      return null
    }
  }

  private static boolean validateIpv4PrefixMatch(Matcher matchResult) {
    if (!matchResult.matches()) {
      return false
    }
    if ((matchResult.group('length') as int) > 32) {
      return false
    }
    for (int i = 0; i < 4; i++) {
      if ((matchResult.group("addr$i") as int) > 255) {
        return false
      }
    }
    return true
  }

  static int convertIpv4PrefixToInt(String addrPrefix) {
    if (!addrPrefix) {
      return -1
    }

    def matchResult = IPV4_PREFIX_REGEX.matcher(addrPrefix)

    // do some validation on the input and return -1 if not a valid address prefix
    if (!validateIpv4PrefixMatch(matchResult)) {
      return -1
    }

    convertIpv4PrefixToInt(matchResult, matchResult.group('length') as int)
  }

  static int getSubnetRangeMax(String vnetAddressPrefix, int subnetAddressPrefixLength = SUBNET_DEFAULT_ADDRESS_PREFIX_LENGTH) {
    if (!vnetAddressPrefix) {
      return 0
    }

    def matchResult = IPV4_PREFIX_REGEX.matcher(vnetAddressPrefix)

    // do some validation on the input and return 0 if not a valid address prefix
    if (!validateIpv4PrefixMatch(matchResult)) {
      return 0
    }

    int vnetAddrPrefixLength = (matchResult.group('length') as int)

    if (vnetAddrPrefixLength >= subnetAddressPrefixLength) {
      return 0
    }

    1 << (subnetAddressPrefixLength - vnetAddrPrefixLength )
  }

  static int getAddressPrefixLength(String addrPrefix) {
    if (!addrPrefix) {
      return 0
    }

    def matchResult = IPV4_PREFIX_REGEX.matcher(addrPrefix)

    // do some validation on the input and return 0 if not a valid address prefix
    if (!validateIpv4PrefixMatch(matchResult)) {
      return 0
    }

    matchResult.group('length') as int
  }

  static int convertIpv4PrefixToInt(Matcher matchResult, int length) {
    int lengthMask = -1 << (32 - length)
    int val = 0
    for (int i = 0; i < 4; i++) {
      val |= (matchResult.group("addr$i") as int) << (i * 8)
    }
    val &= lengthMask
    return val
  }

  static String convertIntToIpv4Prefix(int prefix, int length) {
    int lowMask = 255
    int[] addr = new int[4]
    for (int i = 0; i < addr.length; i++) {
      addr[i] = (prefix >>> (i * 8)) & lowMask
    }
    return "${addr[3]}.${addr[2]}.${addr[1]}.${addr[0]}/$length"
  }

  static int compareIpv4AddrPrefixes(String leftAddrPrefix, String rightAddrPrefix) {
    def leftMatchResult = IPV4_PREFIX_REGEX.matcher(leftAddrPrefix)
    def rightMatchResult = IPV4_PREFIX_REGEX.matcher(rightAddrPrefix)

    // do some validation on the inputs
    if (!validateIpv4PrefixMatch(leftMatchResult)) {
      throw new IllegalArgumentException("Invalid IPv4 address prefix: $leftAddrPrefix")
    }
    if (!validateIpv4PrefixMatch(rightMatchResult)) {
      throw new IllegalArgumentException("Invalid IPv4 address prefix: $rightAddrPrefix")
    }

    // compare using the smallest length; that way, if the regions overlap, we return 0 for equal
    int length = Math.min(leftMatchResult.group('length') as int, rightMatchResult.group('length') as int)

    int leftNum = convertIpv4PrefixToInt(leftMatchResult, length)
    int rightNum = convertIpv4PrefixToInt(rightMatchResult, length)

    // all integers in Java are signed (2's complement), so comparing two prefixes with the high bit set
    // in one argument and not set in the other argument requires special handling
    if (leftNum < 0 && rightNum >= 0) {
      return 1
    } else if (rightNum < 0 && leftNum >= 0) {
      return -1
    } else {
      return leftNum - rightNum
    }
  }

  static String getNextSubnet(String vnetAddrPrefix, String subnetAddrPrefix = null) {
    if (!subnetAddrPrefix) {
      def vnetMatchResult = IPV4_PREFIX_REGEX.matcher(vnetAddrPrefix)
      if (vnetMatchResult.matches()) {
        // default subnet address prefix length to /24 unless vnet requires a larger length
        int adjustedLength = Math.max((vnetMatchResult.group('length') as int) + 8, SUBNET_DEFAULT_ADDRESS_PREFIX_LENGTH)
        subnetAddrPrefix = vnetAddrPrefix.replaceAll('/\\d+$', "/$adjustedLength")
      }
    }

    def subnetMatchResult = IPV4_PREFIX_REGEX.matcher(subnetAddrPrefix)

    if (!validateIpv4PrefixMatch(subnetMatchResult)) {
      throw new IllegalArgumentException("Invalid subnet address prefix: $subnetAddrPrefix")
    }
    if (compareIpv4AddrPrefixes(subnetAddrPrefix, vnetAddrPrefix) != 0) {
      throw new IllegalArgumentException("Subnet $subnetAddrPrefix is not in vnet $vnetAddrPrefix")
    }

    int length = subnetMatchResult.group('length') as int
    int prefix = convertIpv4PrefixToInt(subnetMatchResult, length)

    prefix >>>= (32 - length)
    prefix += 1
    prefix <<= (32 - length)

    String resultPrefix = convertIntToIpv4Prefix(prefix, length)
    if (compareIpv4AddrPrefixes(subnetAddrPrefix, resultPrefix) >= 0) {
      throw new ArithmeticException("Overflow occurred getting next valid subnet after $subnetAddrPrefix")
    }
    if (compareIpv4AddrPrefixes(resultPrefix, vnetAddrPrefix) != 0) {
      throw new ArithmeticException("Overflow occurred getting next valid subnet after $subnetAddrPrefix within vnet $vnetAddrPrefix")
    }
    return resultPrefix
  }

  static String convertParametersToTemplateJSON(ObjectMapper mapper, Map<String, Object> sourceParameters) {
    Map<String, Object> map = new HashMap<>()
    if (sourceParameters.size() == 0) return mapper.writeValueAsString(sourceParameters)
    for(Map.Entry<String, Object> entry: sourceParameters.entrySet()) {
      // Avoid null reference by skipping null values. It still works for those mapping destination fields whose source values are skipped here since they will be assigned as null by default.
      if(entry.value) {
        if (entry.value.class == String) {
          map.put(entry.key, new ValueParameter(entry.value))
        } else {
          map.put(entry.key, new ReferenceParameter(entry.value))
        }
      }
    }
    mapper.writeValueAsString(map)
  }

  static class ValueParameter extends Object {
    Object value
    ValueParameter(Object value) {
      this.value = value
    }
  }

  static class ReferenceParameter extends Object {
    Object reference
    ReferenceParameter(Object reference) {
      this.reference = reference
    }
  }

  static class ProvisioningState {
    public static final String SUCCEEDED = "Succeeded"
    public static final String FAILED = "Failed"
    public static final String CANCELED = "Canceled"
    public static final String READY = "Ready"
    public static final String DELETED = "Deleted"
    public static final String ACCEPTED = "Accepted"
    public static final String DEPLOYING = "Deploying"
  }
}
