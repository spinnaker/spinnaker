/*
 * Copyright 2015 Netflix, Inc.
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

package com.netflix.spinnaker.clouddriver.titus.caching
import com.netflix.frigga.Names

class Keys {

  //TODO(cfieber) - titan->titus. This is still externally referenced in a bunch of spots, rename in conjunction with updates to orca/deck
  public static final PROVIDER = "titan"

  static enum Namespace {
    IMAGES,
    SERVER_GROUPS,
    INSTANCES,
    CLUSTERS,
    APPLICATIONS,
    HEALTH

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

    if (parts[0] != PROVIDER) {
      return null
    }

    def result = [provider: parts[0], type: parts[1]]

    switch (result.type) {
      case Namespace.IMAGES.ns:
        result << [account: parts[2], region: parts[3], imageId: parts[4]]
        break
      case Namespace.SERVER_GROUPS.ns:
        def names = Names.parseName(parts[5])
        result << [application: names.app.toLowerCase(), cluster: parts[2], account: parts[3], region: parts[4], serverGroup: parts[5], stack: names.stack, detail: names.detail, sequence: names.sequence?.toString()]
        break
      case Namespace.INSTANCES.ns:
        result << [account: parts[2], region: parts[3], instanceId: parts[4]]
        break
      case Namespace.CLUSTERS.ns:
        def names = Names.parseName(parts[4])
        result << [application: parts[2].toLowerCase(), account: parts[3], cluster: parts[4], stack: names.stack, detail: names.detail]
        break
      case Namespace.APPLICATIONS.ns:
        result << [application: parts[2].toLowerCase()]
        break
      case Namespace.HEALTH.ns:
        result << [instanceId: parts[2], account: parts[3], region: parts[4], provider: parts[5]]
        break
      default:
        return null
        break
    }

    result
  }

  static String getImageKey(String imageId, String account, String region) {
    "${PROVIDER}:${Namespace.IMAGES}:${account}:${region}:${imageId}"
  }

  static String getServerGroupKey(String serverGroupName, String account, String region) {
    Names names = Names.parseName(serverGroupName)
    "${PROVIDER}:${Namespace.SERVER_GROUPS}:${names.cluster}:${account}:${region}:${names.group}"
  }

  static String getInstanceKey(String instanceId, String account, String region) {
    "${PROVIDER}:${Namespace.INSTANCES}:${account}:${region}:${instanceId}"
  }

  static String getClusterKey(String clusterName, String application, String account) {
    "${PROVIDER}:${Namespace.CLUSTERS}:${application.toLowerCase()}:${account}:${clusterName}"
  }

  static String getApplicationKey(String application) {
    "${PROVIDER}:${Namespace.APPLICATIONS}:${application.toLowerCase()}"
  }

  static String getInstanceHealthKey(String instanceId, String account, String region, String healthProvider) {
    "${PROVIDER}:${Namespace.HEALTH}:${instanceId}:${account}:${region}:${healthProvider}"
  }
}
