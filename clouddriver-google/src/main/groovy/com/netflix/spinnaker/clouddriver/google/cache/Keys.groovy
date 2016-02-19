/*
 * Copyright 2015 Google, Inc.
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

package com.netflix.spinnaker.clouddriver.google.cache

import com.netflix.frigga.Names
import com.netflix.spinnaker.clouddriver.google.GoogleCloudProvider
import com.netflix.spinnaker.clouddriver.google.model.health.GoogleHealth

class Keys {
  static enum Namespace {
    APPLICATIONS,
    CLUSTERS,
    INSTANCES,
    LOAD_BALANCERS,
    NETWORKS,
    SUBNETS,
    SECURITY_GROUPS,
    SERVER_GROUPS,

    final String ns

    private Namespace() {
      def parts = name().split('_')

      ns = parts.tail().inject(new StringBuilder(parts.head().toLowerCase())) { val, next -> val.append(next.charAt(0)).append(next.substring(1).toLowerCase()) }
    }

    String toString() {
      ns
    }
  }

  // TODO(ttomsu): Remove GoogleCloudProvider from most if not all of the method signatures in this class.
  static Map<String, String> parse(GoogleCloudProvider googleCloudProvider, String key) {
    def parts = key.split(':')

    if (parts.length < 2) {
      return null
    }

    def result = [provider: parts[0], type: parts[1]]

    if (result.provider != googleCloudProvider.id) {
      return null
    }

    switch (result.type) {
      case Namespace.APPLICATIONS.ns:
        result << [application: parts[2]]
        break
      case Namespace.CLUSTERS.ns:
        def names = Names.parseName(parts[4])
        result << [
            application: parts[3],
            account    : parts[2],
            name       : parts[4],
            stack      : names.stack,
            detail     : names.detail
        ]
        break
      case Namespace.INSTANCES.ns:
        def names = Names.parseName(parts[4])
        result << [
            application: names.app,
            account    : parts[2],
            serverGroup: parts[4],
            namespace  : parts[3],
            name       : parts[5]
        ]
        break
      case Namespace.LOAD_BALANCERS.ns:
        result << [
            account: parts[2],
            region : parts[3],
            name   : parts[4]
        ]
        break
      case Namespace.NETWORKS.ns:
        result << [
            id     : parts[2],
            account: parts[3],
            region : parts[4]
        ]
        break
      case Namespace.SUBNETS.ns:
        result << [
            id     : parts[2],
            account: parts[3],
            region : parts[4]
        ]
        break
      case Namespace.SECURITY_GROUPS.ns:
        def names = Names.parseName(parts[2])
        result << [
            application: names.app,
            name       : parts[2],
            id         : parts[3],
            region     : parts[4],
            account    : parts[5]
        ]
        break
      case Namespace.SERVER_GROUPS.ns:
        def names = Names.parseName(parts[5])
        result << [
            application: names.app.toLowerCase(),
            cluster    : parts[2],
            account    : parts[3],
            region     : parts[4],
            serverGroup: parts[5],
            stack      : names.stack,
            detail     : names.detail,
            sequence   : names.sequence?.toString()
        ]
        break
      default:
        return null
        break
    }

    result
  }

  static String getApplicationKey(GoogleCloudProvider googleCloudProvider,
                                  String application) {
    "$googleCloudProvider.id:${Namespace.APPLICATIONS}:${application}"
  }

  static String getClusterKey(GoogleCloudProvider googleCloudProvider,
                              String account,
                              String application,
                              String clusterName) {
    "$googleCloudProvider.id:${Namespace.CLUSTERS}:${account}:${application}:${clusterName}"
  }

  static String getInstanceKey(GoogleCloudProvider googleCloudProvider,
                               String account,
                               String name) {
    "$googleCloudProvider.id:${Namespace.INSTANCES}:${account}:${name}"
  }

  static String getLoadBalancerKey(GoogleCloudProvider googleCloudProvider,
                                   String region,
                                   String account,
                                   String loadBalancerName) {
    "$googleCloudProvider.id:${Namespace.LOAD_BALANCERS}:${account}:${region}:${loadBalancerName}"
  }

  static String getNetworkKey(GoogleCloudProvider googleCloudProvider,
                              String networkName,
                              String region,
                              String account) {
    "$googleCloudProvider.id:${Namespace.NETWORKS}:${networkName}:${account}:${region}"
  }

  static String getSubnetKey(GoogleCloudProvider googleCloudProvider,
                             String subnetName,
                             String region,
                             String account) {
    "$googleCloudProvider.id:${Namespace.SUBNETS}:${subnetName}:${account}:${region}"
  }

  static String getSecurityGroupKey(GoogleCloudProvider googleCloudProvider,
                                    String securityGroupName,
                                    String securityGroupId,
                                    String region,
                                    String account) {
    "$googleCloudProvider.id:${Namespace.SECURITY_GROUPS}:${securityGroupName}:${securityGroupId}:${region}:${account}"
  }

  static String getServerGroupKey(GoogleCloudProvider googleCloudProvider,
                                  String managedInstanceGroupName,
                                  String account,
                                  String region) {
    Names names = Names.parseName(managedInstanceGroupName)
    "$googleCloudProvider.id:${Namespace.SERVER_GROUPS}:${names.cluster}:${account}:${region}:${names.group}"
  }
}
