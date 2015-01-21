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

package com.netflix.spinnaker.oort.aws.data

import com.netflix.frigga.Names
import groovy.transform.CompileStatic

@CompileStatic
class Keys {

  static enum Namespace {
    IMAGES,
    NAMED_IMAGES,
    SERVER_GROUPS,
    INSTANCES,
    LAUNCH_CONFIGS,
    LOAD_BALANCERS,
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

  static Map<String, String> parse(String key) {
    def parts = key.split(':')
    def result = [:]
    switch (parts[0]) {
      case Namespace.IMAGES.ns:
        result = [account: parts[1], region: parts[2], imageId: parts[3]]
        break
      case Namespace.NAMED_IMAGES.ns:
        result = [account: parts[1], imageName: parts[2]]
        break
      case Namespace.SERVER_GROUPS.ns:
        def names = Names.parseName(parts[4])
        result = [application: names.app.toLowerCase(), cluster: parts[1], account: parts[2], region: parts[3], serverGroup: parts[4], stack: names.stack, detail: names.detail, sequence: names.sequence?.toString()]
        break
      case Namespace.INSTANCES.ns:
        result = [account: parts[1], region: parts[2], instanceId: parts[3]]
        break
      case Namespace.LAUNCH_CONFIGS.ns:
        def names = Names.parseName(parts[3])
        result = [account: parts[1], region: parts[2], launchConfig: parts[3], application: names.app?.toLowerCase(), stack: names.stack]
        break
      case Namespace.LOAD_BALANCERS.ns:
        def names = Names.parseName(parts[3])
        result = [account: parts[1], region: parts[2], loadBalancer: parts[3], vpcId: parts.length > 4 ? parts[4] : null, application: names.app?.toLowerCase(), stack: names.stack, detail: names.detail]
        break
      case Namespace.CLUSTERS.ns:
        def names = Names.parseName(parts[3])
        result = [application: parts[1].toLowerCase(), account: parts[2], cluster: parts[3], stack: names.stack, detail: names.detail]
        break
      case Namespace.APPLICATIONS.ns:
        result = [application: parts[1].toLowerCase()]
        break
      case Namespace.HEALTH.ns:
        result = [instanceId: parts[1], account: parts[2], region: parts[3], provider: parts[4]]
        break
    }
    result.type = parts[0]
    result
  }

  static String getImageKey(String imageId, String account, String region) {
    "${Namespace.IMAGES}:${account}:${region}:${imageId}"
  }

  static String getNamedImageKey(String account, String imageName) {
    "${Namespace.NAMED_IMAGES}:${account}:${imageName}"
  }

  static String getServerGroupKey(String autoScalingGroupName, String account, String region) {
    Names names = Names.parseName(autoScalingGroupName)
    "${Namespace.SERVER_GROUPS}:${names.cluster}:${account}:${region}:${names.group}"
  }

  static String getInstanceKey(String instanceId, String account, String region) {
    "${Namespace.INSTANCES}:${account}:${region}:${instanceId}"
  }

  static String getLaunchConfigKey(String launchConfigName, String account, String region) {
    "${Namespace.LAUNCH_CONFIGS}:${account}:${region}:${launchConfigName}"
  }

  static String getLoadBalancerKey(String loadBalancerName, String account, String region, String vpcId) {
    "${Namespace.LOAD_BALANCERS}:${account}:${region}:${loadBalancerName}${vpcId ? ':' + vpcId : ''}"
  }

  static String getClusterKey(String clusterName, String application, String account) {
    "${Namespace.CLUSTERS}:${application.toLowerCase()}:${account}:${clusterName}"
  }

  static String getApplicationKey(String application) {
    "${Namespace.APPLICATIONS}:${application.toLowerCase()}"
  }

  static String getInstanceHealthKey(String instanceId, String account, String region, String provider) {
    "${Namespace.HEALTH}:${instanceId}:${account}:${region}:${provider}"
  }
}
