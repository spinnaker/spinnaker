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

import com.microsoft.windowsazure.core.utils.CollectionStringBuilder
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
  static final String DNS_NAME_PREFIX = "dns-"
  static final String IPCONFIG_NAME_PREFIX = "ipc-"
  static final String NETWORK_INTERFACE_PREFIX = "nic-"
  static final Pattern IPV4_PREFIX_REGEX = ~/^(?<addr3>\d+)\.(?<addr2>\d+)\.(?<addr1>\d+)\.(?<addr0>\d+)\/(?<length>\d+)$/

  static String getResourceNameFromID(String resourceId) {
    int idx = resourceId.lastIndexOf(PATH_SEPARATOR)
    if (idx > 0) {
      return resourceId.substring(idx + 1)
    }
    resourceId
  }

  static String getResourceGroupName(AzureResourceOpsDescription description) {
    description.appName + NAME_SEPARATOR + description.region
  }

  static String getVirtualNetworkName(AzureResourceOpsDescription description) {
    VNET_NAME_PREFIX + getResourceGroupName(description)
  }

  static String getVirtualNetworkName(String resourceGroupName) {
    VNET_NAME_PREFIX + resourceGroupName
  }

  static String getSubnetName(String virtualNetworkName, String addressPrefix) {
    String addressPrefixSanitized = addressPrefix.replaceAll('[\\./]', '_')
    virtualNetworkName + NAME_SEPARATOR + SUBNET_NAME_PREFIX + addressPrefixSanitized
  }

  static String getResourceGroupName(String appName, String region) {
    appName + NAME_SEPARATOR + region.replace(' ', '').toLowerCase()
  }

  static String getResourceGroupLocation(AzureResourceOpsDescription description) {
    def resourceGroupName = getResourceGroupName(description)

    description.credentials.getResourceManagerClient().getResourceGroupLocation(resourceGroupName, description.getCredentials())
  }

  static String getResourceGroupNameFromResourceId(String resourceId) {
    def parts = resourceId.split(PATH_SEPARATOR)
    def idx = parts.findIndexOf {it == "resourceGroups"}
    def resourceGroupName = "unknown"

    if (idx > 0) {
      resourceGroupName = parts[idx + 1]
    }

    resourceGroupName
  }

  static String getAppNameFromAzureResourceName(String azureResourceName) {
    azureResourceName.split(NAME_SEPARATOR).first()
  }

  static String getAppNameFromResourceId(String resourceId) {
    getResourceGroupNameFromResourceId(resourceId).split(NAME_SEPARATOR).first()
  }

  static String getLocationFromResourceGroupName(String resourceId) {
    getResourceGroupNameFromResourceId(resourceId).split(NAME_SEPARATOR).last()
  }

  static String getNameFromResourceId(String resourceId) {
    resourceId.split(PATH_SEPARATOR).last()
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

  private static int convertIpv4PrefixToInt(Matcher matchResult, int length) {
    int lengthMask = -1 << (32 - length)
    int val = 0
    for (int i = 0; i < 4; i++) {
      val |= (matchResult.group("addr$i") as int) << (i * 8)
    }
    val &= lengthMask
    return val
  }

  private static String convertIntToIpv4Prefix(int prefix, int length) {
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
        int adjustedLength = (vnetMatchResult.group('length') as int) + 8
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

  public static String getAzureRESTUrl(String subscriptionId, String baseUrl, String targetUrl, List<String> queryParameters) {
    String url = baseUrl
    // Trim '/' character from the end of baseUrl.
    if (url && url.charAt(url.length() - 1) == (char) '/') {
      url = url.substring(0, (url.length() - 1) + 0)
    }
    url += "/subscriptions/"
    if (subscriptionId != null) {
      url = url + URLEncoder.encode(subscriptionId, "UTF-8")
    }
    if (targetUrl && targetUrl.charAt(0) != (char) '/')
      url += "/"
    url += targetUrl
    if (queryParameters && queryParameters.size() > 0) {
      url = url + "?" + CollectionStringBuilder.join(queryParameters, "&")
    }
    url = url.replace(" ", "%20")

    url
  }
}
