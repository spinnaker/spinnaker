/*
 * Copyright 2016 Pivotal, Inc.
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

package com.netflix.spinnaker.clouddriver.cf.cache

import com.netflix.frigga.Names
import com.netflix.spinnaker.clouddriver.cf.CloudFoundryCloudProvider
import groovy.transform.CompileStatic

@CompileStatic
class Keys {

  static enum Namespace {
    INSTANCES,
    SERVER_GROUPS,
    CLUSTERS,
    APPLICATIONS,
    LOAD_BALANCERS,
    ON_DEMAND

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
    def parts = key.split(':')

    if (parts.length < 2) {
      return null
    }

    def result = [provider: parts[0], type: parts[1]]

    if (result.provider != CloudFoundryCloudProvider.ID) {
      return null
    }

    switch (result.type) {
      case Namespace.INSTANCES.ns:
        result << [account: parts[2], region: parts[3], instanceId: parts[4]]
        break
      case Namespace.SERVER_GROUPS.ns:
        def names = Names.parseName(parts[5])
        result << [application: names.app.toLowerCase(), cluster: parts[2], account: parts[3], region: parts[4], serverGroup: parts[5], stack: names.stack, detail: names.detail, sequence: names.sequence?.toString()]
        break
      case Namespace.CLUSTERS.ns:
        def names = Names.parseName(parts[4])
        result << [application: parts[2].toLowerCase(), account: parts[3], cluster: parts[4], stack: names.stack, detail: names.detail]
        break
      case Namespace.APPLICATIONS.ns:
        result << [application: parts[2].toLowerCase()]
        break
      case Namespace.LOAD_BALANCERS.ns:
        def names = Names.parseName(parts[4])
        result << [account: parts[2], region: parts[3], loadBalancer: parts[4], application: names.app?.toLowerCase(), stack: names.stack, detail: names.detail]
        break
      default:
        return null
    }

    result
  }

  static String getInstanceKey(String instanceId, String account, String region) {
    "${CloudFoundryCloudProvider.ID}:${Namespace.INSTANCES}:${account}:${region}:${instanceId}"
  }

  static String getServerGroupKey(String serverGroupName, String account, String region) {
    Names names = Names.parseName(serverGroupName)
    "${CloudFoundryCloudProvider.ID}:${Namespace.SERVER_GROUPS}:${names.cluster}:${account}:${region}:${names.group}"
  }

  static String getClusterKey(String clusterName, String application, String account) {
    "${CloudFoundryCloudProvider.ID}:${Namespace.CLUSTERS}:${application.toLowerCase()}:${account}:${clusterName}"
  }

  static String getApplicationKey(String application) {
    "${CloudFoundryCloudProvider.ID}:${Namespace.APPLICATIONS}:${application.toLowerCase()}"
  }

  static String getLoadBalancerKey(String loadBalancerName, String account, String region) {
    "${CloudFoundryCloudProvider.ID}:${Namespace.LOAD_BALANCERS}:${account}:${region}:${loadBalancerName}"
  }

}
