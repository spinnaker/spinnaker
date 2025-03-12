/*
 * Copyright 2019 THL A29 Limited, a Tencent company.
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

package com.netflix.spinnaker.clouddriver.tencentcloud.cache;

import com.netflix.frigga.Names;
import com.netflix.spinnaker.clouddriver.cache.KeyParser;
import com.netflix.spinnaker.clouddriver.tencentcloud.TencentCloudProvider;
import java.util.HashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component("TencentCloudKeys")
public class Keys implements KeyParser {

  private static final String SEPARATOR = ":";

  public enum Namespace {
    APPLICATIONS,
    CLUSTERS,
    HEALTH_CHECKS,
    LAUNCH_CONFIGS,
    IMAGES,
    NAMED_IMAGES,
    INSTANCES,
    INSTANCE_TYPES,
    KEY_PAIRS,
    LOAD_BALANCERS,
    NETWORKS,
    SECURITY_GROUPS,
    SERVER_GROUPS,
    SUBNETS,
    ON_DEMAND;

    public final String ns;

    Namespace() {
      this.ns = name().toLowerCase();
    }

    public static Namespace fromString(String name) {
      try {
        return valueOf(name.toUpperCase());
      } catch (IllegalArgumentException e) {
        throw new IllegalArgumentException("No matching namespace with name " + name + " exists");
      }
    }

    public String toString() {
      return ns;
    }
  }

  @Override
  public String getCloudProvider() {
    return TencentCloudProvider.ID;
  }

  @Override
  public Boolean canParseType(final String type) {
    try {
      Namespace.fromString(type);
      return true;
    } catch (Exception e) {
      return false;
    }
  }

  @Override
  public Boolean canParseField(String field) {
    return false;
  }

  @Override
  public Map<String, String> parseKey(String key) {
    return parse(key);
  }

  public static Map<String, String> parse(String key) {
    String[] parts = key.split(SEPARATOR);

    if (parts.length < 2 || !parts[0].equals(TencentCloudProvider.ID)) {
      return null;
    }

    Map<String, String> result = new HashMap<>();
    result.put("provider", parts[0]);
    result.put("type", parts[1]);

    Namespace namespace = Namespace.fromString(result.get("type"));

    if (namespace == null) {
      return null;
    }

    switch (namespace) {
      case APPLICATIONS:
        break;
      case CLUSTERS:
        Names names = Names.parseName(parts[4]);
        result.put("application", parts[3]);
        result.put("account", parts[2]);
        result.put("name", parts[4]);
        result.put("cluster", parts[4]);
        result.put("stack", names.getStack());
        result.put("detail", names.getDetail());
        break;
      case IMAGES:
        result.put("account", parts[2]);
        result.put("region", parts[3]);
        result.put("imageId", parts[4]);
        break;
      case NAMED_IMAGES:
        result.put("account", parts[2]);
        result.put("imageName", parts[3]);
        break;
      case LOAD_BALANCERS:
      case NETWORKS:
      case SUBNETS:
        result.put("account", parts[2]);
        result.put("region", parts[3]);
        result.put("id", parts[4]);
        break;
      case SECURITY_GROUPS:
        result.put("application", Names.parseName(parts[2]).getApp());
        result.put("name", parts[2]);
        result.put("account", parts[3]);
        result.put("region", parts[4]);
        result.put("id", parts[5]);
        break;
      case SERVER_GROUPS:
        result.put("account", parts[2]);
        result.put("region", parts[3]);
        result.put("cluster", parts[4]);
        result.put("name", parts[5]);
        break;
      default:
        return null;
    }

    return result;
  }

  public static String getApplicationKey(String application) {
    return TencentCloudProvider.ID
        + SEPARATOR
        + Namespace.APPLICATIONS
        + SEPARATOR
        + application.toLowerCase();
  }

  public static String getClusterKey(String clusterName, String application, String account) {
    return TencentCloudProvider.ID
        + SEPARATOR
        + Namespace.CLUSTERS
        + SEPARATOR
        + account
        + SEPARATOR
        + application.toLowerCase()
        + SEPARATOR
        + clusterName;
  }

  public static String getServerGroupKey(String serverGroupName, String account, String region) {
    Names names = Names.parseName(serverGroupName);
    return getServerGroupKey(names.getCluster(), names.getGroup(), account, region);
  }

  public static String getServerGroupKey(
      String cluster, String serverGroupName, String account, String region) {
    return TencentCloudProvider.ID
        + SEPARATOR
        + Namespace.SERVER_GROUPS
        + SEPARATOR
        + account
        + SEPARATOR
        + region
        + SEPARATOR
        + cluster
        + SEPARATOR
        + serverGroupName;
  }

  public static String getInstanceKey(String instanceId, String account, String region) {
    return TencentCloudProvider.ID
        + SEPARATOR
        + Namespace.INSTANCES
        + SEPARATOR
        + account
        + SEPARATOR
        + region
        + SEPARATOR
        + instanceId;
  }

  public static String getImageKey(String imageId, String account, String region) {
    return TencentCloudProvider.ID
        + SEPARATOR
        + Namespace.IMAGES
        + SEPARATOR
        + account
        + SEPARATOR
        + region
        + SEPARATOR
        + imageId;
  }

  public static String getNamedImageKey(String imageName, String account) {
    return TencentCloudProvider.ID
        + SEPARATOR
        + Namespace.NAMED_IMAGES
        + SEPARATOR
        + account
        + SEPARATOR
        + imageName;
  }

  public static String getKeyPairKey(String keyId, String account, String region) {
    return TencentCloudProvider.ID
        + SEPARATOR
        + Namespace.KEY_PAIRS
        + SEPARATOR
        + account
        + SEPARATOR
        + region
        + SEPARATOR
        + keyId;
  }

  public static String getInstanceTypeKey(String account, String region, String instanceType) {
    return TencentCloudProvider.ID
        + SEPARATOR
        + Namespace.INSTANCE_TYPES
        + SEPARATOR
        + account
        + SEPARATOR
        + region
        + SEPARATOR
        + instanceType;
  }

  public static String getLoadBalancerKey(String loadBalancerId, String account, String region) {
    return TencentCloudProvider.ID
        + SEPARATOR
        + Namespace.LOAD_BALANCERS
        + SEPARATOR
        + account
        + SEPARATOR
        + region
        + SEPARATOR
        + loadBalancerId;
  }

  public static String getSecurityGroupKey(
      String securityGroupId, String securityGroupName, String account, String region) {
    return TencentCloudProvider.ID
        + SEPARATOR
        + Namespace.SECURITY_GROUPS
        + SEPARATOR
        + securityGroupName
        + SEPARATOR
        + account
        + SEPARATOR
        + region
        + SEPARATOR
        + securityGroupId;
  }

  public static String getNetworkKey(String networkId, String account, String region) {
    return TencentCloudProvider.ID
        + SEPARATOR
        + Namespace.NETWORKS
        + SEPARATOR
        + account
        + SEPARATOR
        + region
        + SEPARATOR
        + networkId;
  }

  public static String getSubnetKey(String subnetId, String account, String region) {
    return TencentCloudProvider.ID
        + SEPARATOR
        + Namespace.SUBNETS
        + SEPARATOR
        + account
        + SEPARATOR
        + region
        + SEPARATOR
        + subnetId;
  }

  public static String getTargetHealthKey(
      String loadBalancerId, String listenerId, String instanceId, String account, String region) {
    return TencentCloudProvider.ID
        + SEPARATOR
        + Namespace.HEALTH_CHECKS
        + SEPARATOR
        + account
        + SEPARATOR
        + region
        + SEPARATOR
        + loadBalancerId
        + SEPARATOR
        + listenerId
        + SEPARATOR
        + instanceId;
  }
}
