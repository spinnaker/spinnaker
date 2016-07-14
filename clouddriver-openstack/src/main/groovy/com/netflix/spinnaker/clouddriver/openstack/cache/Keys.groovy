/*
 * Copyright 2016 Target, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.clouddriver.openstack.cache

import com.netflix.frigga.Names
import com.netflix.spinnaker.clouddriver.openstack.OpenstackCloudProvider
import groovy.transform.CompileStatic

import static com.netflix.spinnaker.clouddriver.openstack.OpenstackCloudProvider.ID

/**
 * Defines custom namespaces, keys for Openstack caching ... Encapsulates parsing logic for keys across
 * providers.
 */
@CompileStatic
class Keys {

  static enum Namespace {
    NETWORKS,
    SUBNETS,
    INSTANCES,
    INSTANCE_TYPES,
    APPLICATIONS,
    CLUSTERS,
    SERVER_GROUPS,
    SECURITY_GROUPS,
    LOAD_BALANCERS,
    IMAGES

    final String ns

    private Namespace() {
      def parts = name().split('_')

      ns = parts.tail().inject(new StringBuilder(parts.head().toLowerCase())) { val, next -> val.append(next.charAt(0)).append(next.substring(1).toLowerCase()) }
    }

    String toString() {
      ns
    }
  }

  static Map<String, String> parse(String key) {
    def result = [:]

    def parts = key.split(':')

    if (parts.length > 2) {
      String provider = parts[0]
      if (provider == OpenstackCloudProvider.ID) {
        String type = parts[1]

        switch (type) {
          case Namespace.INSTANCES.ns:
            if (parts.length == 5) {
              result << [account: parts[2], region: parts[3], instanceId: parts[4]]
            }
            break
          case Namespace.INSTANCE_TYPES.ns:
            if (parts.length == 5) {
              result << [account: parts[3], region: parts[4], instanceTypeId: parts[2]]
            }
            break
          case Namespace.APPLICATIONS.ns:
            if (parts.length == 3) {
              result << [application: parts[2].toLowerCase()]
            }
            break
          case Namespace.CLUSTERS.ns:
            if (parts.length == 5) {
              def names = Names.parseName(parts[4])
              result << [application: parts[3].toLowerCase(), account: parts[2], cluster: parts[4], stack: names.stack, detail: names.detail]
            }
            break
          case Namespace.SUBNETS.ns:
            if (parts.length == 5) {
              result << [id: parts[2], account: parts[3], region: parts[4]]
            }
            break
          case Namespace.NETWORKS.ns:
            if (parts.length == 5) {
              result << [id: parts[2], account: parts[3], region: parts[4]]
            }
            break
          case Namespace.SECURITY_GROUPS.ns:
            if (parts.length == 6) {
              def names = Names.parseName(parts[2])
              result << [application: names.app, name: parts[2], id: parts[3], region: parts[4], account: parts[5]]
            }
            break
          case Namespace.IMAGES.ns:
            if (parts.length == 5)
              result << [account: parts[2], region: parts[3], imageId: parts[4]]
            break
          case Namespace.SERVER_GROUPS.ns:
            def names = Names.parseName(parts[5])
            if (parts.length == 6) {
              result << [application: names.app.toLowerCase(), cluster: parts[2], account: parts[3], region: parts[4], serverGroup: parts[5], stack: names.stack, detail: names.detail, sequence: names.sequence?.toString()]
            }
            break
        }

        if (!result.isEmpty()) {
          result << [provider: provider, type: type]
        }
      }
    }
    result.isEmpty() ? null : result
  }

  static String getInstanceKey(String instanceId, String account, String region) {
    "${ID}:${Namespace.INSTANCES}:${account}:${region}:${instanceId}"
  }

  static String getSubnetKey(String subnetId, String region, String account) {
    "${ID}:${Namespace.SUBNETS}:${subnetId}:${account}:${region}"
  }

  static String getApplicationKey(String application) {
    "${ID}:${Namespace.APPLICATIONS}:${application.toLowerCase()}"
  }

  static String getServerGroupKey(String serverGroupName, String account, String region) {
    Names names = Names.parseName(serverGroupName)
    "${ID}:${Namespace.SERVER_GROUPS}:${names.cluster}:${account}:${region}:${names.group}"
  }

  static String getClusterKey(String account, String application, String clusterName) {
    "${ID}:${Namespace.CLUSTERS}:${account}:${application}:${clusterName}"
  }

  static String getNetworkKey(String networkId, String account, String region) {
    "${ID}:${Namespace.NETWORKS}:${networkId}:${account}:${region}"
  }

  static String getSecurityGroupKey(String securityGroupName, String securityGroupId, String account, String region) {
    "${ID}:${Namespace.SECURITY_GROUPS}:${securityGroupName}:${securityGroupId}:${region}:${account}"
  }

  static String getLoadBalancerKey(String loadBalancerId, String account, String region) {
    "${ID}:${Namespace.LOAD_BALANCERS}:${account}:${region}:${loadBalancerId}"
  }

  static String getImageKey(String imageId, String account, String region) {
    "${ID}:${Namespace.IMAGES}:${account}:${region}:${imageId}"
  }

  static String getInstanceTypeKey(String instanceType, String account, String region) {
    "${ID}:${Namespace.INSTANCE_TYPES}:${instanceType}:${account}:${region}"
  }
}
