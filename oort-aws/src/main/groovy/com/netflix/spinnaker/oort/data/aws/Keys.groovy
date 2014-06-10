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

  static String getImageKey(String imageId, String region) {
    "images:${region}:${imageId}"
  }

  static String getServerGroupKey(String autoScalingGroupName, String account, String region) {
    Names names = Names.parseName(autoScalingGroupName)
    "serverGroups:${names.cluster}:${account}:${region}:${names.group}"
  }

  static String getServerGroupInstanceKey(String autoScalingGroupName, String instanceId, String account, String region) {
    Names names = Names.parseName(autoScalingGroupName)
    "serverGroupsInstance:${names.cluster}:${account}:${region}:${names.group}:${instanceId}"
  }

  static String getInstanceKey(String instanceId, String region) {
    "instances:${region}:${instanceId}"
  }

  static String getLaunchConfigKey(String launchConfigName, String region) {
    "launchConfigs:${region}:${launchConfigName}"
  }

  static String getLoadBalancerKey(String loadBalancerName, String region) {
    "loadBalancers:${region}:${loadBalancerName}"
  }

  static String getClusterKey(String clusterName, String application, String account) {
    "clusters:${application}:${account}:${clusterName}"
  }

  static String getApplicationKey(String application) {
    "applications:${application}"
  }
}
