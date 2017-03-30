/*
 * Copyright 2016 Google, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.clouddriver.appengine.cache

import com.netflix.frigga.Names
import com.netflix.spinnaker.clouddriver.appengine.AppengineCloudProvider
import groovy.util.logging.Slf4j

@Slf4j
class Keys {
  static enum Namespace {
    APPLICATIONS,
    PLATFORM_APPLICATIONS,
    CLUSTERS,
    SERVER_GROUPS,
    INSTANCES,
    LOAD_BALANCERS,
    ON_DEMAND

    static String provider = AppengineCloudProvider.ID

    final String ns

    private Namespace() {
      def parts = name().split('_')

      ns = parts.tail().inject(new StringBuilder(parts.head().toLowerCase())) { val, next ->
        val.append(next.charAt(0)).append(next.substring(1).toLowerCase())
      }
    }

    String toString() {
      ns
    }
  }

  static Map<String, String> parse(String key) {
    def parts = key.split(':')

    if (parts.length < 2 || parts[0] != AppengineCloudProvider.ID) {
      return null
    }

    def result = [provider: parts[0], type: parts[1]]

    switch (result.type) {
      case Namespace.APPLICATIONS.ns:
        result << [application: parts[2]]
        break
      case Namespace.PLATFORM_APPLICATIONS.ns:
        result << [project: parts[2]]
        break
      case Namespace.CLUSTERS.ns:
        def names = Names.parseName(parts[4])
        result << [
          account: parts[2],
          application: parts[3],
          name: parts[4],
          cluster: parts[4],
          stack: names.stack,
          detail: names.detail
        ]
        break
      case Namespace.INSTANCES.ns:
        result << [
          account: parts[2],
          name: parts[3],
          instance: parts[3]
        ]
        break
      case Namespace.LOAD_BALANCERS.ns:
        result << [
          account: parts[2],
          name: parts[3],
          loadBalancer: parts[3]
        ]
        break
      case Namespace.SERVER_GROUPS.ns:
        def names = Names.parseName(parts[5])
        result << [
          application: names.app,
          cluster: parts[2],
          account: parts[3],
          region: parts[4],
          stack: names.stack,
          detail: names.detail,
          serverGroup: parts[5],
          name: parts[5]
        ]
        break
      default:
        return null
        break
    }

    result
  }

  static String getApplicationKey(String application) {
    "$AppengineCloudProvider.ID:${Namespace.APPLICATIONS}:${application}"
  }

  static String getPlatformApplicationKey(String project) {
    "$AppengineCloudProvider.ID:${Namespace.PLATFORM_APPLICATIONS}:${project}"
  }

  static String getClusterKey(String account, String application, String clusterName) {
    "$AppengineCloudProvider.ID:${Namespace.CLUSTERS}:${account}:${application}:${clusterName}"
  }

  static String getInstanceKey(String account, String instanceName) {
    "$AppengineCloudProvider.ID:${Namespace.INSTANCES}:${account}:${instanceName}"
  }
  static String getLoadBalancerKey(String account, String loadBalancerName) {
    "$AppengineCloudProvider.ID:${Namespace.LOAD_BALANCERS}:${account}:${loadBalancerName}"
  }

  static String getServerGroupKey(String account, String serverGroupName, String region) {
    Names names = Names.parseName(serverGroupName)
    "$AppengineCloudProvider.ID:${Namespace.SERVER_GROUPS}:${names.cluster}:${account}:${region}:${names.group}"
  }
}
