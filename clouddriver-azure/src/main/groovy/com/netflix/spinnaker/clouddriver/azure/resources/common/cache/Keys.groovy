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
    AZURE_APP_GATEWAYS,
    AZURE_APPLICATIONS,
    AZURE_CLUSTERS,
    AZURE_SERVER_GROUPS,
    AZURE_INSTANCES,
    AZURE_VMIMAGES,
    AZURE_CUSTOMVMIMAGES,
    AZURE_ON_DEMAND,
    AZURE_EVICTIONS

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
    parse(azureCloudProvider.id, key)
  }

  static Map<String, String> parse(String providerId, String key){
    def parts = key.split(':')

    if (parts.length < 2) {
      return null
    }

    def result = [provider: parts[0], type: parts[1]]

    if (result.provider != providerId) {
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
      case Namespace.AZURE_APP_GATEWAYS.ns:
        def names = Names.parseName(parts[2])
        result << [
          appname: names.app,
          name:    parts[2],
          region:  parts[3],
          account: parts[4]
        ]
        break
      case Namespace.AZURE_APPLICATIONS.ns:
        result << [application: parts[2].toLowerCase()]
        break
      case Namespace.AZURE_VMIMAGES.ns:
        result << [account: parts[2], region: parts[3], name: parts[4], vmversion: parts[5]]
        break
      case Namespace.AZURE_CUSTOMVMIMAGES.ns:
        result << [account: parts[2], region: parts[3], name: parts[4]]
        break
      case Namespace.AZURE_SERVER_GROUPS.ns:
        def names = Names.parseName(parts[2])
        result << [
          application : names.app.toLowerCase(),
          serverGroup : parts[2],
          region      : parts[3],
          account     : parts[4],
          stack       : names.stack,
          detail      : names.detail
        ]
        break
      case Namespace.AZURE_CLUSTERS.ns:
        def names = Names.parseName(parts[3])
        result << [
          application: parts[2],
          name       : parts[3],
          account    : parts[4],
          stack      : names.stack,
          detail     : names.detail
        ]
        break
      case Namespace.AZURE_INSTANCES.ns:
        def names = Names.parseName(parts[2])
        result << [
          application: names.app,
          serverGroup: parts[2],
          name       : parts[3],
          region     : parts[4],
          account    : parts[5]]
          break
      default:
        return null
        break
    }

    result
  }

  //TODO (scotm) remove all references to the methods that take azureCloudProvider as paramter
  //TODO (scotm) use the AzureCloudProvider.AZURE static string for the id instead
  //These changes are a stop-gap to enable testing without major refactoring (coming).
  // Long-term we will remove the method that takes the cloud provider and default the other version
  // to use the static member of the cloud provider
  static String getSecurityGroupKey(AzureCloudProvider azureCloudProvider,
                                    String securityGroupName,
                                    String securityGroupId,
                                    String region,
                                    String account) {
    //"$azureCloudProvider.id:${Namespace.SECURITY_GROUPS}:${securityGroupName}:${securityGroupId}:${region}:${account}"
    getSecurityGroupKey(azureCloudProvider.id, securityGroupName, securityGroupId, region, account)
  }

  static String getSecurityGroupKey(String azureCloudProviderId,
                                    String securityGroupName,
                                    String securityGroupId,
                                    String region,
                                    String account) {
    "${azureCloudProviderId}:${Namespace.SECURITY_GROUPS}:${securityGroupName}:${securityGroupId}:${region}:${account}"
  }

  static String getSubnetKey(AzureCloudProvider azureCloudProvider,
                             String subnetId,
                             String region,
                             String account) {
    //"$azureCloudProvider.id:${Namespace.AZURE_SUBNETS}:${subnetId}:${account}:${region}"
    getSubnetKey(azureCloudProvider.id, subnetId, region, account)
  }

  static String getSubnetKey(String azureCloudProviderId,
                             String subnetId,
                             String region,
                             String account) {
    "${azureCloudProviderId}:${Namespace.AZURE_SUBNETS}:${subnetId}:${account}:${region}"
  }

  static String getNetworkKey(AzureCloudProvider azureCloudProvider,
                              String networkId,
                              String region,
                              String account) {
    //"$azureCloudProvider.id:${Namespace.AZURE_NETWORKS}:${networkId}:${account}:${region}"
    getNetworkKey(azureCloudProvider.id, networkId, region, account)
  }

  static String getNetworkKey(String azureCloudProviderId,
                              String networkId,
                              String region,
                              String account) {
    "${azureCloudProviderId}:${Namespace.AZURE_NETWORKS}:${networkId}:${account}:${region}"
  }

  static String getVMImageKey(AzureCloudProvider azureCloudProvider,
                              String account,
                              String region,
                              String vmImageName,
                              String vmImageVersion)  {
    //"$azureCloudProvider.id:${Namespace.AZURE_VMIMAGES}:${account}:${region}:${vmImageName}:${vmImageVersion}"
    getVMImageKey(azureCloudProvider.id, account, region, vmImageName, vmImageVersion)
  }

  static String getVMImageKey(String azureCloudProviderId,
                              String account,
                              String region,
                              String vmImageName,
                              String vmImageVersion)  {
    "${azureCloudProviderId}:${Namespace.AZURE_VMIMAGES}:${account}:${region}:${vmImageName}:${vmImageVersion}"
  }

  static String getCustomVMImageKey(AzureCloudProvider azureCloudProvider,
                                    String account,
                                    String region,
                                    String vmImageName) {
    //"$azureCloudProvider.id:${Namespace.AZURE_CUSTOMVMIMAGES}:${account}:${region}:${vmImageName}"
    getCustomVMImageKey(azureCloudProvider.id, account, region, vmImageName)
  }

  static String getCustomVMImageKey(String azureCloudProviderId,
                                    String account,
                                    String region,
                                    String vmImageName) {
    "${azureCloudProviderId}:${Namespace.AZURE_CUSTOMVMIMAGES}:${account}:${region}:${vmImageName}"
  }

  static String getLoadBalancerKey(AzureCloudProvider azureCloudProvider,
                                   String loadBalancerName,
                                   String loadBalancerId,
                                   String application,
                                   String cluster,
                                   String region,
                                   String account) {
    //"$azureCloudProvider.id:${Namespace.AZURE_LOAD_BALANCERS}:${loadBalancerName}:${loadBalancerId}:${cluster}:${application}:${region}:${account}"
    getLoadBalancerKey(azureCloudProvider.id, loadBalancerName, loadBalancerId, application, cluster, region, account)
  }

  static String getLoadBalancerKey(String azureCloudProviderId,
                                   String loadBalancerName,
                                   String loadBalancerId,
                                   String application,
                                   String cluster,
                                   String region,
                                   String account) {
    "${azureCloudProviderId}:${Namespace.AZURE_LOAD_BALANCERS}:${loadBalancerName}:${loadBalancerId}:${cluster}:${application}:${region}:${account}"
  }

  static String getAppGatewayKey(AzureCloudProvider azureCloudProvider,
                                 String appName,
                                 String name,
                                 String region,
                                 String account) {
    getAppGatewayKey(azureCloudProvider.id, appName, name, region, account)
  }

  static String getAppGatewayKey(String azureCloudProviderId,
                                 String appName,
                                 String name,
                                 String region,
                                 String account) {
    "${azureCloudProviderId}:${Namespace.AZURE_APP_GATEWAYS}:${appName}:${name}:${region}:${account}"
  }

  static String getApplicationKey(AzureCloudProvider azureCloudProvider,
                                  String application) {
    //"$azureCloudProvider.id:${Namespace.AZURE_APPLICATIONS}:${application.toLowerCase()}"
    getApplicationKey(azureCloudProvider.id, application)
  }

  static String getApplicationKey(String azureCloudProviderId,
                                  String application) {
    "${azureCloudProviderId}:${Namespace.AZURE_APPLICATIONS}:${application.toLowerCase()}"
  }

  static String getServerGroupKey(AzureCloudProvider azureCloudProvider,
                                  String serverGroupName,
                                  String region,
                                  String account) {
    //"${azureCloudProvider.id}:${Namespace.AZURE_SERVER_GROUPS}:${serverGroupName}:${region}:${account}"
    getServerGroupKey(azureCloudProvider.id, serverGroupName, region, account)

  }

  static String getServerGroupKey(String azureCloudProviderId,
                                  String serverGroupName,
                                  String region,
                                  String account) {
    "${azureCloudProviderId}:${Namespace.AZURE_SERVER_GROUPS}:${serverGroupName}:${region}:${account}"
  }

  static String getClusterKey(AzureCloudProvider azureCloudProvider,
                              String application,
                              String clusterName,
                              String account) {
    //"${azureCloudProvider.id}:${Namespace.AZURE_CLUSTERS}:${application}:${clusterName}:${account}"
    getClusterKey(azureCloudProvider.id, application, clusterName, account)
  }

  static String getClusterKey(String azureCloudProviderId,
                              String application,
                              String clusterName,
                              String account) {
    "${azureCloudProviderId}:${Namespace.AZURE_CLUSTERS}:${application}:${clusterName}:${account}"
  }

  static String getInstanceKey(AzureCloudProvider azureCloudProvider,
                               String serverGroupName,
                               String name,
                               String region,
                               String account) {
    //"${azureCloudProvider.id}:${Namespace.AZURE_INSTANCES.ns}:${serverGroupName}:${name}:${region}:${account}"
    getInstanceKey(azureCloudProvider.id, serverGroupName, name, region, account)
  }

  static String getInstanceKey(String azureCloudProviderId,
                               String serverGroupName,
                               String name,
                               String region,
                               String account) {
    "${azureCloudProviderId}:${Namespace.AZURE_INSTANCES.ns}:${serverGroupName}:${name}:${region}:${account}"
  }

}
