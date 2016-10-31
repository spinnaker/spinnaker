/*
 * Copyright 2015 Google, Inc.
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

package com.netflix.spinnaker.clouddriver.google.cache

import com.netflix.frigga.Names
import com.netflix.spinnaker.clouddriver.google.GoogleCloudProvider
import groovy.util.logging.Slf4j

@Slf4j
class Keys {
  static enum Namespace {
    APPLICATIONS,
    BACKEND_SERVICES,
    CLUSTERS,
    HEALTH_CHECKS,
    HTTP_HEALTH_CHECKS,
    IMAGES,
    INSTANCES,
    LOAD_BALANCERS,
    NETWORKS,
    SECURITY_GROUPS,
    SERVER_GROUPS,
    SSL_CERTIFICATES,
    SUBNETS,

    ON_DEMAND,

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

    if (parts.length < 2 || parts[0] != GoogleCloudProvider.GCE) {
      return null
    }

    if (parts[0] != GoogleCloudProvider.GCE) {
      return null
    }

    def result = [provider: parts[0], type: parts[1]]

    switch (result.type) {
      case Namespace.APPLICATIONS.ns:
        result << [application: parts[2]]
        break
      case Namespace.BACKEND_SERVICES.ns:
        result << [
          account: parts[2],
          kind   : parts[3],
          name   : parts[4],
        ]
        break
      case Namespace.CLUSTERS.ns:
        def names = Names.parseName(parts[4])
        result << [
            application: parts[3],
            account    : parts[2],
            name       : parts[4],
            cluster    : parts[4],
            stack      : names.stack,
            detail     : names.detail
        ]
        break
      case Namespace.HEALTH_CHECKS.ns:
        result << [
            account: parts[2],
            kind   : parts[3],
            name   : parts[4],
        ]
        break
      case Namespace.HTTP_HEALTH_CHECKS.ns:
        result << [
          account: parts[2],
          name   : parts[3],
        ]
        break
      case Namespace.IMAGES.ns:
        result << [
            account: parts[2],
            imageId: parts[3]
        ]
        break
      case Namespace.INSTANCES.ns:
        result << [
            account   : parts[2],
            region    : parts[3],
            name      : parts[4],
            instanceId: parts[4],
        ]
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
            detail      : names.detail,
        ]
        break
      case Namespace.NETWORKS.ns:
        result << [
            id     : parts[2],
            account: parts[3],
            region : parts[4]
        ]
        break
      case Namespace.SECURITY_GROUPS.ns:
        def names = Names.parseName(parts[2])
        result << [
            application: names.app,
            name       : parts[2],
            id         : parts[3],
            region     : parts[4],
            account    : parts[5]
        ]
        break
      case Namespace.SERVER_GROUPS.ns:
        def names = Names.parseName(parts[5])
        result << [
            application: names.app.toLowerCase(),
            cluster    : parts[2],
            account    : parts[3],
            region     : parts[4],
            serverGroup: parts[5],
            stack      : names.stack,
            detail     : names.detail,
            sequence   : names.sequence?.toString()
        ]
        if (parts.length == 7) {
          result.zone = parts[6]
        }
        break
      case Namespace.SSL_CERTIFICATES.ns:
        result << [
          account: parts[2],
          name   : parts[3],
        ]
        break
      case Namespace.SUBNETS.ns:
        result << [
            id     : parts[2],
            account: parts[3],
            region : parts[4]
        ]
        break
      default:
        return null
        break
    }

    result
  }

  static String getApplicationKey(String application) {
    "$GoogleCloudProvider.GCE:${Namespace.APPLICATIONS}:${application}"
  }

  static String getBackendServiceKey(String account,
                                     String kind,
                                     String backendServiceName) {
    "$GoogleCloudProvider.GCE:${Namespace.BACKEND_SERVICES}:${account}:${kind}:${backendServiceName}"
  }

  static String getClusterKey(String account,
                              String application,
                              String clusterName) {
    "$GoogleCloudProvider.GCE:${Namespace.CLUSTERS}:${account}:${application}:${clusterName}"
  }

  static String getHealthCheckKey(String account,
                                  String kind,
                                  String healthCheckName) {
    "$GoogleCloudProvider.GCE:${Namespace.HEALTH_CHECKS}:${account}:${kind}:${healthCheckName}"
  }

  static String getHttpHealthCheckKey(String account,
                                      String httpHealthCheckName) {
    "$GoogleCloudProvider.GCE:${Namespace.HTTP_HEALTH_CHECKS}:${account}:${httpHealthCheckName}"
  }

  static String getImageKey(String account,
                            String imageId) {
    "$GoogleCloudProvider.GCE:${Namespace.IMAGES}:${account}:${imageId}"
  }

  static String getInstanceKey(String account,
                               String region,
                               String name) {
    "$GoogleCloudProvider.GCE:${Namespace.INSTANCES}:${account}:${region}:${name}"
  }

  static String getLoadBalancerKey(String region,
                                   String account,
                                   String loadBalancerName) {
    "$GoogleCloudProvider.GCE:${Namespace.LOAD_BALANCERS}:${account}:${region}:${loadBalancerName}"
  }

  static String getNetworkKey(String networkName,
                              String region,
                              String account) {
    "$GoogleCloudProvider.GCE:${Namespace.NETWORKS}:${networkName}:${account}:${region}"
  }

  static String getSecurityGroupKey(String securityGroupName,
                                    String securityGroupId,
                                    String region,
                                    String account) {
    "$GoogleCloudProvider.GCE:${Namespace.SECURITY_GROUPS}:${securityGroupName}:${securityGroupId}:${region}:${account}"
  }

  static String getServerGroupKey(String managedInstanceGroupName,
                                  String account,
                                  String region) {
    getServerGroupKey(managedInstanceGroupName, account, region, null)
  }

  static String getServerGroupKey(String managedInstanceGroupName,
                                  String account,
                                  String region,
                                  String zone) {
    Names names = Names.parseName(managedInstanceGroupName)
    "$GoogleCloudProvider.GCE:${Namespace.SERVER_GROUPS}:${names.cluster}:${account}:${region}:${names.group}${zone ? ":$zone" : ""}"
  }

  static String getSslCertificateKey(String account,
                                     String sslCertificateName) {
    "$GoogleCloudProvider.GCE:${Namespace.SSL_CERTIFICATES}:${account}:${sslCertificateName}"
  }

  static String getSubnetKey(String subnetName,
                             String region,
                             String account) {
    "$GoogleCloudProvider.GCE:${Namespace.SUBNETS}:${subnetName}:${account}:${region}"
  }
}
