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
import com.netflix.spinnaker.clouddriver.core.provider.agent.Namespace
import com.netflix.spinnaker.clouddriver.openstack.OpenstackCloudProvider
import groovy.transform.CompileStatic

//TODO - Refactor to use template pattern and add to core so common code can be shared across implementations.
@CompileStatic
class Keys {

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
        }

        if (!result.isEmpty()) {
          result << [provider: provider, type: type]
        }
      }
    }
    result.isEmpty() ? null : result
  }

  static String getInstanceKey(String instanceId, String account, String region) {
    "${OpenstackCloudProvider.ID}:${Namespace.INSTANCES}:${account}:${region}:${instanceId}"
  }

  static String getApplicationKey(String application) {
    "${OpenstackCloudProvider.ID}:${Namespace.APPLICATIONS}:${application.toLowerCase()}"
  }

  static String getServerGroupKey(String serverGroupName, String account, String region) {
    Names names = Names.parseName(serverGroupName)
    "${OpenstackCloudProvider.ID}:${Namespace.SERVER_GROUPS}:${names.cluster}:${account}:${region}:${names.group}"
  }

  static String getClusterKey(String account, String application, String clusterName) {
    "${OpenstackCloudProvider.ID}:${Namespace.CLUSTERS}:${account}:${application}:${clusterName}"
  }
}
