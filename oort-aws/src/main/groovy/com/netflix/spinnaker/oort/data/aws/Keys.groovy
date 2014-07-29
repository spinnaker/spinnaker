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

package com.netflix.spinnaker.oort.data.aws

import com.netflix.frigga.Names

class Keys {

  static enum Namespace {
    IMAGES,
    SERVER_GROUPS,
    SERVER_GROUP_INSTANCE,
    INSTANCES,
    LAUNCH_CONFIGS,
    LOAD_BALANCERS,
    LOAD_BALANCER_SERVER_GROUPS,
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


  static String getImageKey(String imageId, String region) {
    "${Namespace.IMAGES}:${region}:${imageId}"
  }

  static String getServerGroupKey(String autoScalingGroupName, String account, String region) {
    Names names = Names.parseName(autoScalingGroupName)
    "${Namespace.SERVER_GROUPS}:${names.cluster}:${account}:${region}:${names.group}"
  }

  static String getServerGroupInstanceKey(String autoScalingGroupName, String instanceId, String account, String region) {
    Names names = Names.parseName(autoScalingGroupName)
    "${Namespace.SERVER_GROUP_INSTANCE}:${names.cluster}:${account}:${region}:${names.group}:${instanceId}"
  }

  static String getInstanceKey(String instanceId, String region) {
    "${Namespace.INSTANCES}:${region}:${instanceId}"
  }

  static String getLaunchConfigKey(String launchConfigName, String region) {
    "${Namespace.LAUNCH_CONFIGS}:${region}:${launchConfigName}"
  }

  static String getLoadBalancerKey(String loadBalancerName, String account, String region) {
    "${Namespace.LOAD_BALANCERS}:${account}:${region}:${loadBalancerName}"
  }

  static String getLoadBalancerServerGroupKey(String loadBalancerName, String account, String serverGroupName, String region) {
    "${Namespace.LOAD_BALANCER_SERVER_GROUPS}:${loadBalancerName}:${account}:${region}:${serverGroupName}"
  }

  static String getClusterKey(String clusterName, String application, String account) {
    "${Namespace.CLUSTERS}:${application}:${account}:${clusterName}"
  }

  static String getApplicationKey(String application) {
    "${Namespace.APPLICATIONS}:${application}"
  }

  static String getInstanceHealthKey(String instanceId, String account, String region, String provider) {
    "${Namespace.HEALTH}:${instanceId}:${account}:${region}:${provider}"
  }
}
