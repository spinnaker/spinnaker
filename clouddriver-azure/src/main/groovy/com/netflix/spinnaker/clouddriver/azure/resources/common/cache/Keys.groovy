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

package com.netflix.spinnaker.clouddriver.azure.resources.common.cache

import com.netflix.frigga.Names
import com.netflix.spinnaker.clouddriver.azure.AzureCloudProvider

class Keys {
  static enum Namespace {
    SECURITY_GROUPS,
    AZURE_SUBNETS,
    AZURE_NETWORKS,
    AZURE_LOAD_BALANCERS,
    AZURE_APPLICATIONS,
    AZURE_CLUSTERS,
    AZURE_SERVER_GROUPS,
    AZURE_INSTANCES,
    AZURE_VMIMAGES

    final String ns

    private Namespace() {
      def parts = name().split('_')

      ns = parts.tail().inject(new StringBuilder(parts.head().toLowerCase())) { val, next -> val.append(next.charAt(0)).append(next.substring(1).toLowerCase()) }
    }

    String toString() {
      ns
    }
  }

  static Map<String, String> parse(AzureCloudProvider azureCloudProvider, String key) {
    def parts = key.split(':')

    if (parts.length < 2) {
      return null
    }

    def result = [provider: parts[0], type: parts[1]]

    if (result.provider != azureCloudProvider.id) {
      return null
    }

    switch (result.type) {
      case Namespace.SECURITY_GROUPS.ns:
        def names = Names.parseName(parts[2])
        result << [application: names.app, name: parts[2], id: parts[3], region: parts[4], account: parts[5]]
        break
      case Namespace.AZURE_NETWORKS.ns:
        result << [id: parts[2], account: parts[3], region: parts[4]]
        break
      case Namespace.AZURE_SUBNETS.ns:
        result << [id: parts[2], account: parts[3], region: parts[4]]
        break
      case Namespace.AZURE_LOAD_BALANCERS.ns:
        def names = Names.parseName(parts[2])
        result << [application: names.app, name: parts[2], id: parts[3], cluster: parts[4], appname: parts[5], region: parts[6], account: parts[7]]
        break
      case Namespace.AZURE_APPLICATIONS.ns:
        result << [application: parts[2].toLowerCase()]
        break
      case Namespace.AZURE_VMIMAGES.ns:
        result << [account: parts[2], region: parts[3], name: parts[4], vmversion: parts[5]]
        break
      default:
        return null
        break
    }

    result
  }

  static String getSecurityGroupKey(AzureCloudProvider azureCloudProvider,
                                    String securityGroupName,
                                    String securityGroupId,
                                    String region,
                                    String account) {
    "$azureCloudProvider.id:${Namespace.SECURITY_GROUPS}:${securityGroupName}:${securityGroupId}:${region}:${account}"
  }

  static String getSubnetKey(AzureCloudProvider azureCloudProvider,
                             String subnetId,
                             String region,
                             String account) {
    "$azureCloudProvider.id:${Namespace.AZURE_SUBNETS}:${subnetId}:${account}:${region}"
  }

  static String getNetworkKey(AzureCloudProvider azureCloudProvider,
                              String networkId,
                              String region,
                              String account) {
    "$azureCloudProvider.id:${Namespace.AZURE_NETWORKS}:${networkId}:${account}:${region}"
  }

  static String getVMImageKey(AzureCloudProvider azureCloudProvider,
                              String account,
                              String region,
                              String vmImageName,
                              String vmImageVersion
                              ) {
    "$azureCloudProvider.id:${Namespace.AZURE_VMIMAGES}:${account}:${region}:${vmImageName}:${vmImageVersion}"
  }

  static String getLoadBalancerKey(AzureCloudProvider azureCloudProvider,
                                   String loadBalancerName,
                                   String loadBalancerId,
                                   String application,
                                   String cluster,
                                   String region,
                                   String account) {
    "$azureCloudProvider.id:${Namespace.AZURE_LOAD_BALANCERS}:${loadBalancerName}:${loadBalancerId}:${cluster}:${application}:${region}:${account}"
  }

  static String getApplicationKey(AzureCloudProvider azureCloudProvider,
                                  String application ) {
    //TODO revisit this method when we are ready to store into the cache the Azure server groups
    "$azureCloudProvider.id:${Namespace.AZURE_APPLICATIONS}:${application.toLowerCase()}"
  }
}
