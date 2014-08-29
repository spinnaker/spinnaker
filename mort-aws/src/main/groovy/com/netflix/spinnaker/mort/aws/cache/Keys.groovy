/*
 * Copyright 2014 Netflix, Inc.
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

package com.netflix.spinnaker.mort.aws.cache

import com.netflix.frigga.Names

class Keys {
  static enum Namespace {
    SECURITY_GROUPS

    final String ns

    private Namespace() {
      def parts = name().split('_')

      ns = parts.tail().inject(new StringBuilder(parts.head().toLowerCase())) { val, next -> val.append(next.charAt(0)).append(next.substring(1).toLowerCase()) }
    }

    String toString() {
      ns
    }
  }

  static Map parse(String key) {
    def parts = key.split(':')
    def result = [:]
    switch (parts[0]) {
      case Namespace.SECURITY_GROUPS.ns:
        def names = Names.parseName(parts[1])
        result = [application: names.app, id: parts[2], region: parts[3], account: parts[4]]
        break
    }
    result.type = parts[0]
    result
  }

  static String getSecurityGroupKey(String securityGroupName, String securityGroupId, String region, String account) {
    "${Namespace.SECURITY_GROUPS}:${securityGroupName}:${securityGroupId}:${region}:${account}"
  }
}
