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
import com.netflix.spinnaker.clouddriver.cache.KeyParser
import com.netflix.spinnaker.clouddriver.titus.TitusCloudProvider
import org.springframework.stereotype.Component

@Component("titusKeyParser")
class Keys implements KeyParser {

  static enum Namespace {
    IMAGES,
    SERVER_GROUPS,
    INSTANCES,
    CLUSTERS,
    APPLICATIONS,
    HEALTH,
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

  @Override
  String getCloudProvider() {
    return TitusCloudProvider.ID
  }

  @Override
  Map<String, String> parseKey(String key) {
    return parse(key)
  }

  @Override
  Boolean canParseType(String type) {
    return Namespace.values().any { it.ns == type }
  }

  @Override
  Boolean canParseField(String field) {
    return false
  }


  static Map<String, String> parse(String key) {
    def parts = key.split(':')

    if (parts.length < 2) {
      return null
    }

    if (parts[0] != TitusCloudProvider.ID) {
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
        result << [id: parts[2]]
        break
      case Namespace.CLUSTERS.ns:
        def names = Names.parseName(parts[4])
        result << [application: parts[2].toLowerCase(), account: parts[3], cluster: parts[4], stack: names.stack, detail: names.detail]
        break
      case Namespace.APPLICATIONS.ns:
        result << [application: parts[2].toLowerCase()]
        break
      case Namespace.HEALTH.ns:
        result << [id: parts[2], account: parts[3], region: parts[4], provider: parts[5]]
        break
      default:
        return null
        break
    }

    result
  }

  static String getImageKey(String imageId, String account, String region) {
    "${TitusCloudProvider.ID}:${Namespace.IMAGES}:${account}:${region}:${imageId}"
  }

  static String getServerGroupKey(String serverGroupName, String account, String region) {
    Names names = Names.parseName(serverGroupName)
    return getServerGroupKey(names.cluster, names.group, account, region)
  }

  static String getServerGroupKey(String cluster, String autoScalingGroupName, String account, String region) {
    "${TitusCloudProvider.ID}:${Namespace.SERVER_GROUPS}:${cluster}:${account}:${region}:${autoScalingGroupName}"
  }

  static String getInstanceKey(String id, String accountId, String stack, String region) {
    "${TitusCloudProvider.ID}:${Namespace.INSTANCES}:${accountId}:${region}:${stack}:${id}"
  }

  static String getClusterKey(String clusterName, String application, String account) {
    "${TitusCloudProvider.ID}:${Namespace.CLUSTERS}:${application?.toLowerCase()}:${account}:${clusterName}"
  }

  static String getApplicationKey(String application) {
    "${TitusCloudProvider.ID}:${Namespace.APPLICATIONS}:${application?.toLowerCase()}"
  }

  static String getInstanceHealthKey(String id, String healthProvider) {
    "${TitusCloudProvider.ID}:${Namespace.HEALTH}:${id}:${healthProvider}"
  }
}
