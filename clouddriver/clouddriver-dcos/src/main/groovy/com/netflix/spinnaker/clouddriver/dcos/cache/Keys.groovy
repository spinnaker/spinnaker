/*
 * Copyright 2017 Cerner Corporation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
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
 *
 */

package com.netflix.spinnaker.clouddriver.dcos.cache

import com.netflix.frigga.Names
import com.netflix.spinnaker.clouddriver.dcos.deploy.util.id.DcosSpinnakerAppId
import com.netflix.spinnaker.clouddriver.dcos.deploy.util.id.DcosSpinnakerLbId

class Keys {
  public static final PROVIDER = "dcos"

  static enum Namespace {
    IMAGES,
    SERVER_GROUPS,
    INSTANCES,
    LOAD_BALANCERS,
    CLUSTERS,
    APPLICATIONS,
    HEALTH,
    SECRETS,
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

    if (parts[0] != PROVIDER) {
      return null
    }

    def result = [provider: parts[0], type: parts[1]]

    switch (result.type) {

      case Namespace.SECRETS.ns:
        result << [
          dcosCluster: parts[2],
          secretPath : parts[3]
        ]
        break
      case Namespace.IMAGES.ns:
        //TODO result << [account: parts[2], region: parts[3], imageId: parts[4]]
        break
      case Namespace.SERVER_GROUPS.ns:
        def names = Names.parseName(parts[4])
        result << [
          account    : parts[2],
          name       : parts[5],
          region     : parts[3],
          group      : parts[4],
          serverGroup: parts[5],
          application: names.app,
          stack      : names.stack,
          cluster    : names.cluster,
          detail     : names.detail,
          sequence   : names.sequence?.toString()
        ]
        break
      case Namespace.INSTANCES.ns:
        //TODO result << [id: parts[2]]
        break
      case Namespace.CLUSTERS.ns:
        def names = Names.parseName(parts[4])
        result << [
          account    : parts[2],
          application: parts[3],
          name       : parts[4],
          cluster    : parts[4],
          stack      : names.stack,
          detail     : names.detail]
        //TODO result << [application: parts[2].toLowerCase(), account: parts[3], cluster: parts[4], stack: names.stack, detail: names.detail]
        break
      case Namespace.APPLICATIONS.ns:
        result << [
          application: parts[2]
        ]
        break
      case Namespace.HEALTH.ns:
        //TODO result << [id: parts[2], account: parts[3], region: parts[4], provider: parts[5]]
        break
      case Namespace.LOAD_BALANCERS.ns:
        def names = Names.parseName(parts[4])
        result << [
          account     : parts[2],
          region      : parts[3],
          name        : parts[4],
          loadBalancer: parts[4],
          application : names.app,
          stack       : names.stack,
          detail      : names.detail
        ]
        break
      default:
        return null
        break
    }

    return result
  }

  static String getSecretKey(String region, String secretPath) {
    "${PROVIDER}:${Namespace.SECRETS}:${region}:${secretPath.replaceAll('/', '_')}"
  }

  static String getApplicationKey(String application) {
    "${PROVIDER}:${Namespace.APPLICATIONS}:${application}"
  }

  static String getServerGroupKey(DcosSpinnakerAppId id, String region) {
    "${PROVIDER}:${Namespace.SERVER_GROUPS}:${id.account}:${region}:${id.safeGroup}:${id.serverGroupName.group}"
  }

  static String getClusterKey(String account, String application, String cluster) {
    "${PROVIDER}:${Namespace.CLUSTERS}:${account}:${application}:${cluster}"
  }

  static String getInstanceKey(DcosSpinnakerAppId appId, String taskName) {
    "${PROVIDER}:${Namespace.INSTANCES}:${appId.account}:${appId.safeGroup}:${taskName}"
  }

  static String getInstanceKey(String account, String safeCombinedGroup, String taskName) {
    "${PROVIDER}:${Namespace.INSTANCES}:${account}:${safeCombinedGroup}:${taskName}"
  }

  static String getLoadBalancerKey(String account, String region, String loadBalancerName) {
    "${PROVIDER}:${Namespace.LOAD_BALANCERS}:${account}:${region}:${loadBalancerName}"
  }

  static String getLoadBalancerKey(DcosSpinnakerLbId id, String region) {
    "${PROVIDER}:${Namespace.LOAD_BALANCERS}:${id.account}:${region}:${id.loadBalancerName}"
  }
}
