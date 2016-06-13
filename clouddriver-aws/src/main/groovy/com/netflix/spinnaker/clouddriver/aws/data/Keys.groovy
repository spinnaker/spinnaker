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

package com.netflix.spinnaker.clouddriver.aws.data

import com.netflix.frigga.Names
import com.netflix.spinnaker.clouddriver.core.provider.agent.Namespace
import groovy.transform.CompileStatic
import static com.netflix.spinnaker.clouddriver.aws.AmazonCloudProvider.AWS

@CompileStatic
class Keys {

  static Map<String, String> parse(String key) {
    def parts = key.split(':')

    if (parts.length < 2) {
      return null
    }

    def result = [provider: parts[0], type: parts[1]]

    if (result.provider != AWS) {
      return null
    }

    switch (result.type) {
      case Namespace.IMAGES.ns:
        result << [account: parts[2], region: parts[3], imageId: parts[4]]
        break
      case Namespace.NAMED_IMAGES.ns:
        result << [account: parts[2], imageName: parts[3]]
        break
      case Namespace.SERVER_GROUPS.ns:
        def names = Names.parseName(parts[5])
        result << [application: names.app.toLowerCase(), cluster: parts[2], account: parts[3], region: parts[4], serverGroup: parts[5], stack: names.stack, detail: names.detail, sequence: names.sequence?.toString()]
        break
      case Namespace.INSTANCES.ns:
        result << [account: parts[2], region: parts[3], instanceId: parts[4]]
        break
      case Namespace.LAUNCH_CONFIGS.ns:
        def names = Names.parseName(parts[4])
        result << [account: parts[2], region: parts[3], launchConfig: parts[4], application: names.app?.toLowerCase(), stack: names.stack]
        break
      case Namespace.LOAD_BALANCERS.ns:
        def names = Names.parseName(parts[4])
        result << [account: parts[2], region: parts[3], loadBalancer: parts[4], vpcId: parts.length > 5 ? parts[5] : null, application: names.app?.toLowerCase(), stack: names.stack, detail: names.detail]
        break
      case Namespace.CLUSTERS.ns:
        def names = Names.parseName(parts[4])
        result << [application: parts[2].toLowerCase(), account: parts[3], cluster: parts[4], stack: names.stack, detail: names.detail]
        break
      case Namespace.APPLICATIONS.ns:
        result << [application: parts[2].toLowerCase()]
        break
      case Namespace.HEALTH.ns:
        result << [instanceId: parts[2], account: parts[3], region: parts[4], provider: parts[5]]
        break
      default:
        return null
        break
    }

    result
  }

  static String getImageKey(String imageId, String account, String region) {
    "${AWS}:${Namespace.IMAGES}:${account}:${region}:${imageId}"
  }

  static String getNamedImageKey(String account, String imageName) {
    "${AWS}:${Namespace.NAMED_IMAGES}:${account}:${imageName}"
  }

  static String getServerGroupKey(String autoScalingGroupName, String account, String region) {
    Names names = Names.parseName(autoScalingGroupName)
    "${AWS}:${Namespace.SERVER_GROUPS}:${names.cluster}:${account}:${region}:${names.group}"
  }

  static String getInstanceKey(String instanceId, String account, String region) {
    "${AWS}:${Namespace.INSTANCES}:${account}:${region}:${instanceId}"
  }

  static String getLaunchConfigKey(String launchConfigName, String account, String region) {
    "${AWS}:${Namespace.LAUNCH_CONFIGS}:${account}:${region}:${launchConfigName}"
  }

  static String getLoadBalancerKey(String loadBalancerName, String account, String region, String vpcId) {
    "${AWS}:${Namespace.LOAD_BALANCERS}:${account}:${region}:${loadBalancerName}${vpcId ? ':' + vpcId : ''}"
  }

  static String getClusterKey(String clusterName, String application, String account) {
    "${AWS}:${Namespace.CLUSTERS}:${application.toLowerCase()}:${account}:${clusterName}"
  }

  static String getApplicationKey(String application) {
    "${AWS}:${Namespace.APPLICATIONS}:${application.toLowerCase()}"
  }

  static String getInstanceHealthKey(String instanceId, String account, String region, String provider) {
    "${AWS}:${Namespace.HEALTH}:${instanceId}:${account}:${region}:${provider}"
  }

  static String getReservedInstancesKey(String reservedInstancesId, String account, String region) {
    "${AWS}:${Namespace.RESERVED_INSTANCES}:${account}:${region}:${reservedInstancesId}"
  }
}
