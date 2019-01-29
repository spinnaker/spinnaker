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

import com.google.common.collect.ImmutableMap
import com.google.common.collect.ImmutableSet
import com.netflix.frigga.Names
import com.netflix.spinnaker.clouddriver.cache.KeyParser
import com.netflix.spinnaker.clouddriver.core.provider.agent.Namespace
import groovy.transform.CompileStatic
import groovy.transform.TypeChecked
import groovy.transform.TypeCheckingMode
import org.springframework.stereotype.Component

import static com.netflix.spinnaker.clouddriver.aws.AmazonCloudProvider.ID

@CompileStatic
@Component("AmazonKeys")
class Keys implements KeyParser {

  private static final Map<String, String> NAMESPACE_MAPPING =
    ImmutableMap.builder()
      .put(Namespace.SERVER_GROUPS.ns, "serverGroup")
      .put(Namespace.INSTANCES.ns, "instanceId")
      .put(Namespace.LOAD_BALANCERS.ns, "loadBalancer")
      .put(Namespace.TARGET_GROUPS.ns, "targetGroup")
      .put(Namespace.CLUSTERS.ns, "cluster")
      .put(Namespace.APPLICATIONS.ns, "application")
      .put(Namespace.STACKS.ns, "stacks")
      .build()

  private static final Set<String> PARSEABLE_FIELDS =
    ImmutableSet.builder()
      .addAll(Namespace.SERVER_GROUPS.fields)
      .addAll(Namespace.LOAD_BALANCERS.fields)
      .build()

  @Override
  String getNameMapping(String cache) {
    return NAMESPACE_MAPPING.get(cache)
  }

  @Override
  String getCloudProvider() {
    return ID
  }

  @Override
  Map<String, String> parseKey(String key) {
    return parse(key)
  }

  @Override
  @TypeChecked(value = TypeCheckingMode.SKIP)
  Boolean canParseType(String type) {
    return Namespace.values().any { it.ns == type }
  }

  @Override
  Boolean canParseField(String field) {
    return PARSEABLE_FIELDS.contains(field)
  }

  static Map<String, String> parse(String key) {
    def parts = key.split(':')

    if (parts.length < 2) {
      return null
    }

    def result = [provider: parts[0], type: parts[1]]

    if (result.provider != ID) {
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
        String vpcId = parts.length > 5 ? (parts[5] ?: null) : null
        String loadBalancerType = vpcId && parts.length > 6 ? parts[6] : 'classic'
        result << [account: parts[2], region: parts[3], loadBalancer: parts[4], vpcId: vpcId, application: names.app?.toLowerCase(), stack: names.stack, detail: names.detail, loadBalancerType: loadBalancerType]
        break
      case Namespace.TARGET_GROUPS.ns:
        def names = Names.parseName(parts[4])
        String vpcId = parts.length > 6 ? (parts[6] ?: null) : null
        result << [account: parts[2], region: parts[3], targetGroup: parts[4], vpcId: vpcId, application: names.app?.toLowerCase(), stack: names.stack, detail: names.detail, targetType: parts[5]]
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
      case Namespace.STACKS.ns:
        result << [stackId: parts[2], account: parts[3], region: parts[4]]
        break
      default:
        return null
        break
    }

    result
  }

  static String getImageKey(String imageId, String account, String region) {
    "${ID}:${Namespace.IMAGES}:${account}:${region}:${imageId}"
  }

  static String getNamedImageKey(String account, String imageName) {
    "${ID}:${Namespace.NAMED_IMAGES}:${account}:${imageName}"
  }

  static String getServerGroupKey(String autoScalingGroupName, String account, String region) {
    Names names = Names.parseName(autoScalingGroupName)
    return getServerGroupKey(names.cluster, names.group, account, region)
  }

  static String getServerGroupKey(String cluster, String autoScalingGroupName, String account, String region) {
    "${ID}:${Namespace.SERVER_GROUPS}:${cluster}:${account}:${region}:${autoScalingGroupName}"
  }

  static String getInstanceKey(String instanceId, String account, String region) {
    "${ID}:${Namespace.INSTANCES}:${account}:${region}:${instanceId}"
  }

  static String getLaunchConfigKey(String launchConfigName, String account, String region) {
    "${ID}:${Namespace.LAUNCH_CONFIGS}:${account}:${region}:${launchConfigName}"
  }

  static String getLoadBalancerKey(String loadBalancerName, String account, String region, String vpcId, String loadBalancerType) {
    String key = "${ID}:${Namespace.LOAD_BALANCERS}:${account}:${region}:${loadBalancerName}"
    if (vpcId) {
      key += ":${vpcId}"
      if (loadBalancerType && loadBalancerType != 'classic') {
        key += ":${loadBalancerType}"
      }
    }
    return key
  }

  static String getTargetGroupKey(String targetGroupName, String account, String region, String targetGroupType, String vpcId) {
    "${ID}:${Namespace.TARGET_GROUPS}:${account}:${region}:${targetGroupName}:${targetGroupType}:${vpcId}"
  }

  static String getClusterKey(String clusterName, String application, String account) {
    "${ID}:${Namespace.CLUSTERS}:${application.toLowerCase()}:${account}:${clusterName}"
  }

  static String getApplicationKey(String application) {
    "${ID}:${Namespace.APPLICATIONS}:${application.toLowerCase()}"
  }

  static String getInstanceHealthKey(String instanceId, String account, String region, String provider) {
    "${ID}:${Namespace.HEALTH}:${instanceId}:${account}:${region}:${provider}"
  }

  static String getReservedInstancesKey(String reservedInstancesId, String account, String region) {
    "${ID}:${Namespace.RESERVED_INSTANCES}:${account}:${region}:${reservedInstancesId}"
  }

  static String getCloudFormationKey(String stackId, String accountName, String region) {
    "${ID}:${Namespace.STACKS}:${accountName}:${region}:${stackId}"
  }
}
