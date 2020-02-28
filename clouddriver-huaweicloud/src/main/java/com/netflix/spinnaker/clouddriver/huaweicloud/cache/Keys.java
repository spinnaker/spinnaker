/*
 * Copyright 2019 Huawei Technologies Co.,Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.clouddriver.huaweicloud.cache;

import com.google.common.base.CaseFormat;
import com.netflix.frigga.Names;
import com.netflix.spinnaker.clouddriver.cache.KeyParser;
import com.netflix.spinnaker.clouddriver.huaweicloud.HuaweiCloudProvider;
import com.netflix.spinnaker.clouddriver.huaweicloud.HuaweiCloudUtils;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component("HuaweiCloudKeys")
public class Keys implements KeyParser {

  public static enum Namespace {
    NETWORKS,
    SECURITY_GROUPS,
    SUBNETS,
    ON_DEMAND;

    public final String ns;

    private Namespace() {
      ns = CaseFormat.UPPER_UNDERSCORE.to(CaseFormat.LOWER_CAMEL, this.name());
    }

    @Override
    public String toString() {
      return ns;
    }
  }

  @Override
  public Map<String, String> parseKey(String key) {
    return parse(key);
  }

  @Override
  public String getCloudProvider() {
    return getCloudProviderId();
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

  private static final String SEPARATOR = ":";

  private static String getCloudProviderId() {
    return HuaweiCloudProvider.ID;
  }

  private static Map emptyMap() {
    return Collections.emptyMap();
  }

  public static Map<String, String> parse(String key, Namespace targetType) {
    Map<String, String> keys = parse(key);
    return (!keys.isEmpty() && !targetType.ns.equals(keys.get("type"))) ? emptyMap() : keys;
  }

  public static Map<String, String> parse(String key) {
    if (HuaweiCloudUtils.isEmptyStr(key)) {
      return emptyMap();
    }

    String[] parts = key.split(SEPARATOR);
    if ((parts.length < 2) || (!getCloudProviderId().equals(parts[0]))) {
      return emptyMap();
    }

    Namespace ns;
    try {
      ns = Namespace.valueOf(CaseFormat.LOWER_CAMEL.to(CaseFormat.UPPER_UNDERSCORE, parts[1]));
    } catch (Exception e) {
      return emptyMap();
    }

    Map<String, String> result;

    switch (ns) {
      case NETWORKS:
        result = parseNetworkKey(parts);
        break;
      case SECURITY_GROUPS:
        result = parseSecurityGroupKey(parts);
        break;
      case SUBNETS:
        result = parseSubnetKey(parts);
        break;
      default:
        return emptyMap();
    }

    if (result.isEmpty()) {
      return result;
    }

    result.put("provider", parts[0]);
    result.put("type", parts[1]);
    return result;
  }

  public static String getNetworkKey(String networkId, String account, String region) {
    return getCloudProviderId()
        + SEPARATOR
        + Namespace.NETWORKS
        + SEPARATOR
        + account
        + SEPARATOR
        + region
        + SEPARATOR
        + networkId;
  }

  private static Map parseNetworkKey(String[] parts) {
    if (parts.length != 5) {
      return emptyMap();
    }

    Map<String, String> result = new HashMap();
    result.put("account", parts[2]);
    result.put("region", parts[3]);
    result.put("id", parts[4]);
    return result;
  }

  public static String getSecurityGroupKey(
      String securityGroupName, String securityGroupId, String account, String region) {
    return getCloudProviderId()
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
  }

  private static Map parseSecurityGroupKey(String[] parts) {
    if (parts.length != 6) {
      return emptyMap();
    }

    Names names = Names.parseName(parts[4]);
    if (HuaweiCloudUtils.isEmptyStr(names.getApp())) {
      return emptyMap();
    }

    Map<String, String> result = new HashMap();
    result.put("application", names.getApp());
    result.put("account", parts[2]);
    result.put("region", parts[3]);
    result.put("name", parts[4]);
    result.put("id", parts[5]);

    return result;
  }

  public static String getSubnetKey(String subnetId, String account, String region) {
    return getCloudProviderId()
        + SEPARATOR
        + Namespace.SUBNETS
        + SEPARATOR
        + account
        + SEPARATOR
        + region
        + SEPARATOR
        + subnetId;
  }

  private static Map parseSubnetKey(String[] parts) {
    if (parts.length != 5) {
      return emptyMap();
    }

    Map<String, String> result = new HashMap();
    result.put("account", parts[2]);
    result.put("region", parts[3]);
    result.put("id", parts[4]);
    return result;
  }
}
