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

import com.netflix.spinnaker.clouddriver.openstack.OpenstackCloudProvider
import groovy.transform.CompileStatic

//TODO - Refactor to use template pattern and add to core so common code can be shared across implementations.
@CompileStatic
class Keys {

  static enum Namespace {
    INSTANCES

    final String ns

    private Namespace() {
      def parts = name().split('_')

      // Converts CONSTANT naming convention to camel case
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
}
