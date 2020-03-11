/*
 * Copyright 2019 Alibaba Group.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.clouddriver.alicloud.cache;

import static com.netflix.spinnaker.clouddriver.alicloud.AliCloudProvider.ID;

import com.google.common.base.CaseFormat;
import com.netflix.frigga.Names;
import com.netflix.spinnaker.clouddriver.alicloud.model.AliCloudServerGroup;
import com.netflix.spinnaker.clouddriver.cache.KeyParser;
import java.util.HashMap;
import java.util.Map;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

@Component("AliCloudKeys")
public class Keys implements KeyParser {

  public enum Namespace {
    LOAD_BALANCERS,
    SUBNETS,
    INSTANCE_TYPES,
    SECURITY_GROUPS,
    ALI_CLOUD_KEY_PAIRS;

    public final String ns;

    Namespace() {
      ns = CaseFormat.UPPER_UNDERSCORE.to(CaseFormat.LOWER_CAMEL, this.name());
    }

    @Override
    public String toString() {
      return ns;
    }
  }

  public static final String SEPARATOR = ":";

  public static String getLoadBalancerKey(
      String loadBalancerName, String account, String region, String vpcId) {
    String key =
        ID
            + SEPARATOR
            + Namespace.LOAD_BALANCERS
            + SEPARATOR
            + account
            + SEPARATOR
            + region
            + SEPARATOR
            + loadBalancerName;
    if (!StringUtils.isEmpty(vpcId)) {
      key = key + SEPARATOR + vpcId;
    }
    return key;
  }

  public static String getSubnetKey(String vSwitchId, String region, String account) {
    String key =
        ID
            + SEPARATOR
            + Namespace.SUBNETS
            + SEPARATOR
            + account
            + SEPARATOR
            + region
            + SEPARATOR
            + vSwitchId;
    return key;
  }

  public static String getImageKey(String imageId, String account, String region) {
    String key =
        ID
            + SEPARATOR
            + com.netflix.spinnaker.clouddriver.core.provider.agent.Namespace.IMAGES
            + SEPARATOR
            + account
            + SEPARATOR
            + region
            + SEPARATOR
            + imageId;
    return key;
  }

  public static String getNamedImageKey(String account, String imageName) {
    String key =
        ID
            + SEPARATOR
            + com.netflix.spinnaker.clouddriver.core.provider.agent.Namespace.NAMED_IMAGES
            + SEPARATOR
            + account
            + SEPARATOR
            + imageName;
    return key;
  }

  public static String getInstanceTypeKey(String account, String region, String zoneId) {
    String key =
        ID
            + SEPARATOR
            + Namespace.INSTANCE_TYPES
            + SEPARATOR
            + account
            + SEPARATOR
            + region
            + SEPARATOR
            + zoneId;
    return key;
  }

  public static String getSecurityGroupKey(
      String securityGroupName,
      String securityGroupId,
      String region,
      String account,
      String vpcId) {
    String key =
        ID
            + SEPARATOR
            + Namespace.SECURITY_GROUPS
            + SEPARATOR
            + account
            + SEPARATOR
            + region
            + SEPARATOR
            + securityGroupName
            + SEPARATOR
            + securityGroupId;
    if (!StringUtils.isEmpty(vpcId)) {
      key = key + SEPARATOR + vpcId;
    }
    return key;
  }

  public static String getKeyPairKey(String keyPairName, String region, String account) {
    String key =
        ID
            + SEPARATOR
            + Namespace.ALI_CLOUD_KEY_PAIRS
            + SEPARATOR
            + keyPairName
            + SEPARATOR
            + account
            + SEPARATOR
            + region;
    return key;
  }

  public static String getServerGroupKey(
      String autoScalingGroupName, String account, String region) {
    AliCloudServerGroup serverGroup = new AliCloudServerGroup();
    serverGroup.setName(autoScalingGroupName);
    // Names names = Names.parseName(autoScalingGroupName);
    return getServerGroupKey(
        serverGroup.getMoniker().getCluster(), autoScalingGroupName, account, region);
  }

  public static String getServerGroupKey(
      String cluster, String autoScalingGroupName, String account, String region) {
    return ID
        + SEPARATOR
        + com.netflix.spinnaker.clouddriver.core.provider.agent.Namespace.SERVER_GROUPS
        + SEPARATOR
        + cluster
        + SEPARATOR
        + account
        + SEPARATOR
        + region
        + SEPARATOR
        + autoScalingGroupName;
  }

  public static String getApplicationKey(String application) {
    String key =
        ID
            + SEPARATOR
            + com.netflix.spinnaker.clouddriver.core.provider.agent.Namespace.APPLICATIONS
            + SEPARATOR
            + application.toLowerCase();
    return key;
  }

  public static String getClusterKey(String clusterName, String application, String account) {
    String key =
        ID
            + SEPARATOR
            + com.netflix.spinnaker.clouddriver.core.provider.agent.Namespace.CLUSTERS
            + SEPARATOR
            + application.toLowerCase()
            + SEPARATOR
            + account
            + SEPARATOR
            + clusterName;
    return key;
  }

  public static String getLaunchConfigKey(String launchConfigName, String account, String region) {
    String key =
        ID
            + SEPARATOR
            + com.netflix.spinnaker.clouddriver.core.provider.agent.Namespace.LAUNCH_CONFIGS
            + SEPARATOR
            + account
            + SEPARATOR
            + region
            + SEPARATOR
            + launchConfigName;
    return key;
  }

  public static String getInstanceKey(String instanceId, String account, String region) {
    String key =
        ID
            + SEPARATOR
            + com.netflix.spinnaker.clouddriver.core.provider.agent.Namespace.INSTANCES
            + SEPARATOR
            + account
            + SEPARATOR
            + region
            + SEPARATOR
            + instanceId;
    return key;
  }

  public static String getInstanceHealthKey(
      String loadBalancerId,
      String instanceId,
      String port,
      String account,
      String region,
      String provider) {
    String key =
        ID
            + SEPARATOR
            + com.netflix.spinnaker.clouddriver.core.provider.agent.Namespace.HEALTH
            + SEPARATOR
            + loadBalancerId
            + SEPARATOR
            + instanceId
            + SEPARATOR
            + port
            + SEPARATOR
            + account
            + SEPARATOR
            + region
            + SEPARATOR
            + provider;
    return key;
  }

  @Override
  public Map<String, String> parseKey(String key) {
    return parse(key);
  }

  public static Map<String, String> parse(String key) {
    Map<String, String> result = new HashMap<>();
    String[] parts = key.split(SEPARATOR);
    if (parts.length < 2) {
      return null;
    }

    result.put("provider", parts[0]);
    result.put("type", parts[1]);

    if (!ID.equals(result.get("provider"))) {
      return null;
    }

    switch (result.get("type")) {
      case "securityGroups":
        if (parts.length >= 7 && !"null".equals(parts[6])) {
          Names names = Names.parseName(parts[4]);
          result.put("application", names.getApp());
          result.put("name", parts[4]);
          result.put("id", parts[5]);
          result.put("region", parts[3]);
          result.put("account", parts[2]);
          result.put("vpcId", parts[6]);
        } else {
          return null;
        }
        break;
      default:
        return null;
    }

    return result;
  }

  @Override
  public String getCloudProvider() {
    return ID;
  }

  @Override
  public Boolean canParseType(String type) {
    for (Namespace key : Namespace.values()) {
      if (key.toString().equals(type)) {
        return true;
      }
    }
    return false;
  }

  @Override
  public Boolean canParseField(String field) {
    return false;
  }
}
