/*
 * Copyright 2016 Google, Inc.
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

package com.netflix.spinnaker.clouddriver.kubernetes.cache

import com.netflix.frigga.Names

class Keys {
  static enum Namespace {
    APPLICATIONS,
    CLUSTERS,
    SERVER_GROUPS,
    INSTANCES,
    LOAD_BALANCERS,
    SECURITY_GROUPS,
    ON_DEMAND,

    static String provider = "kubernetes"

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

    if (result.provider != Namespace.provider) {
      return null
    }

    switch (result.type) {
      case Namespace.APPLICATIONS.ns:
        result << [
            application: parts[2]
        ]
        break
      case Namespace.CLUSTERS.ns:
        def names = Names.parseName(parts[4])
        result << [
            account: parts[2],
            application: parts[3],
            category: parts[4], // <- {`serverGroup`, `job`, `daemonSet`, etc...}
            name: parts[5],
            cluster: parts[5],
            stack: names.stack,
            detail: names.detail,
        ]
        break
      case Namespace.SERVER_GROUPS.ns:
        def names = Names.parseName(parts[4])
        result << [
            account: parts[2],
            name: parts[4],
            namespace: parts[3],
            region: parts[3],
            serverGroup: parts[4],
            application: names.app,
            stack: names.stack,
            cluster: names.cluster,
            detail: names.detail,
            sequence: names.sequence?.toString(),
        ]
        break
      case Namespace.LOAD_BALANCERS.ns:
        def names = Names.parseName(parts[4])
        result << [
            account: parts[2],
            namespace: parts[3],
            name: parts[4],
            loadBalancer: parts[4],
            application: names.app,
            stack: names.stack,
            detail: names.detail
        ]
        break
      case Namespace.INSTANCES.ns:
        def names = Names.parseName(parts[4])
        result << [
            account: parts[2],
            namespace: parts[3],
            region: parts[3],
            name: parts[4],
            instanceId: parts[4],
            application: names.app,
        ]
        break
      case Namespace.SECURITY_GROUPS.ns:
        def names = Names.parseName(parts[4])
        result << [
            account: parts[2],
            namespace: parts[3],
            region: parts[3],
            name: parts[4],
            id: parts[4],
            application: names.app,
        ]
        break
      default:
        return null
        break
    }

    result
  }

  static String getApplicationKey(String application) {
    "${Namespace.provider}:${Namespace.APPLICATIONS}:${application}"
  }

  static String getClusterKey(String account, String application, String category, String clusterName) {
    "${Namespace.provider}:${Namespace.CLUSTERS}:${account}:${application}:${category}:${clusterName}"
  }

  static String getServerGroupKey(String account, String namespace, String replicationControllerName) {
    "${Namespace.provider}:${Namespace.SERVER_GROUPS}:${account}:${namespace}:${replicationControllerName}"
  }

  static String getLoadBalancerKey(String account, String namespace, String serviceName) {
    "${Namespace.provider}:${Namespace.LOAD_BALANCERS}:${account}:${namespace}:${serviceName}"
  }

  static String getInstanceKey(String account, String namespace, String name) {
    "${Namespace.provider}:${Namespace.INSTANCES}:${account}:${namespace}:${name}"
  }

  static String getSecurityGroupKey(String account, String namespace, String ingressName) {
    "${Namespace.provider}:${Namespace.SECURITY_GROUPS}:${account}:${namespace}:${ingressName}"
  }
}
