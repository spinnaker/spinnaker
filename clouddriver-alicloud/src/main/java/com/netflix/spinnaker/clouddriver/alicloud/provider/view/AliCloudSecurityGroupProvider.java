/*
 * Copyright 2020 Netflix, Inc.
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
package com.netflix.spinnaker.clouddriver.alicloud.provider.view;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.cats.cache.Cache;
import com.netflix.spinnaker.cats.cache.CacheData;
import com.netflix.spinnaker.cats.cache.RelationshipCacheFilter;
import com.netflix.spinnaker.clouddriver.alicloud.AliCloudProvider;
import com.netflix.spinnaker.clouddriver.alicloud.cache.Keys;
import com.netflix.spinnaker.clouddriver.alicloud.cache.Keys.Namespace;
import com.netflix.spinnaker.clouddriver.alicloud.model.AliCloudSecurityGroup;
import com.netflix.spinnaker.clouddriver.alicloud.model.AliCloudSecurityGroupRule;
import com.netflix.spinnaker.clouddriver.model.SecurityGroupProvider;
import com.netflix.spinnaker.clouddriver.model.securitygroups.Rule;
import com.netflix.spinnaker.clouddriver.model.securitygroups.Rule.PortRange;
import java.util.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class AliCloudSecurityGroupProvider implements SecurityGroupProvider<AliCloudSecurityGroup> {

  private final ObjectMapper objectMapper;

  private final Cache cacheView;

  @Autowired
  public AliCloudSecurityGroupProvider(ObjectMapper objectMapper, Cache cacheView) {
    this.objectMapper = objectMapper;
    this.cacheView = cacheView;
  }

  @Override
  public Collection<AliCloudSecurityGroup> getAllByAccount(boolean includeRules, String account) {
    return buildSecurityGroup(Keys.getSecurityGroupKey("*", "*", "*", account, "*"));
  }

  @Override
  public AliCloudSecurityGroup get(String account, String region, String name, String vpcId) {
    String key = Keys.getSecurityGroupKey(name, "*", region, account, vpcId);
    return getByKey(key);
  }

  @Override
  public AliCloudSecurityGroup getById(String account, String region, String id, String vpcId) {
    String key = Keys.getSecurityGroupKey("*", id, region, account, vpcId);
    return getByKey(key);
  }

  private AliCloudSecurityGroup getByKey(String key) {
    Collection<String> allSecurityGroupKeys =
        cacheView.filterIdentifiers(Namespace.SECURITY_GROUPS.ns, key);
    Collection<CacheData> allData =
        cacheView.getAll(
            Namespace.SECURITY_GROUPS.ns, allSecurityGroupKeys, RelationshipCacheFilter.none());
    if (allData.size() == 0) {
      return null;
    }
    return buildSecurityGroup(allData.iterator().next());
  }

  @Override
  public Collection<AliCloudSecurityGroup> getAll(boolean includeRules) {
    Set<AliCloudSecurityGroup> results = new HashSet<>();
    String globalKey = Keys.getSecurityGroupKey("*", "*", "*", "*", "*");
    Collection<String> keys = cacheView.filterIdentifiers(Namespace.SECURITY_GROUPS.ns, globalKey);
    for (String key : keys) {
      if (!includeRules) {
        Map<String, String> parse = Keys.parse(key);
        results.add(
            new AliCloudSecurityGroup(
                parse.get("id"),
                parse.get("name"),
                null,
                parse.get("account"),
                parse.get("region"),
                parse.get("vpcId"),
                null));
      } else {
        CacheData cacheData = cacheView.get(Namespace.SECURITY_GROUPS.ns, key);
        results.add(buildSecurityGroup(cacheData));
      }
    }
    return results;
  }

  @Override
  public Collection<AliCloudSecurityGroup> getAllByRegion(boolean includeRules, String region) {
    return buildSecurityGroup(Keys.getSecurityGroupKey("*", "*", region, "*", "*"));
  }

  @Override
  public Collection<AliCloudSecurityGroup> getAllByAccountAndName(
      boolean includeRules, String account, String name) {
    return buildSecurityGroup(Keys.getSecurityGroupKey(name, "*", "*", account, "*"));
  }

  @Override
  public Collection<AliCloudSecurityGroup> getAllByAccountAndRegion(
      boolean includeRule, String account, String region) {
    return buildSecurityGroup(Keys.getSecurityGroupKey("*", "*", region, account, "*"));
  }

  private Collection<AliCloudSecurityGroup> buildSecurityGroup(String globalKey) {
    Set<AliCloudSecurityGroup> results = new HashSet<>();
    Collection<String> allSecurityGroupKeys =
        cacheView.filterIdentifiers(Namespace.SECURITY_GROUPS.ns, globalKey);
    Collection<CacheData> allData =
        cacheView.getAll(
            Namespace.SECURITY_GROUPS.ns, allSecurityGroupKeys, RelationshipCacheFilter.none());
    for (CacheData data : allData) {
      results.add(buildSecurityGroup(data));
    }
    return results;
  }

  private AliCloudSecurityGroup buildSecurityGroup(CacheData data) {
    Map<String, Object> attributes = data.getAttributes();
    Set<Rule> rules = new HashSet<>();
    List<Map<String, Object>> permissions =
        objectMapper.convertValue(attributes.get("permissions"), List.class);
    for (Map<String, Object> permission : permissions) {
      String protocol = (String) permission.get("ipProtocol");
      String range = (String) permission.get("portRange");
      String[] split = range.split("/");
      SortedSet<PortRange> portRanges = new TreeSet<>();
      Rule.PortRange portRange = new PortRange();
      portRange.setStartPort(Integer.valueOf(split[0]));
      portRange.setEndPort(Integer.valueOf(split[1]));
      portRanges.add(portRange);
      AliCloudSecurityGroupRule rule =
          new AliCloudSecurityGroupRule(protocol, portRanges, permission);
      rules.add(rule);
    }
    AliCloudSecurityGroup securityGroup =
        new AliCloudSecurityGroup(
            String.valueOf(attributes.get("securityGroupId")),
            String.valueOf(attributes.get("securityGroupName")),
            String.valueOf(attributes.get("description")),
            String.valueOf(attributes.get("account")),
            String.valueOf(attributes.get("regionId")),
            String.valueOf(attributes.get("vpcId")),
            rules);
    return securityGroup;
  }

  @Override
  public String getCloudProvider() {
    return AliCloudProvider.ID;
  }
}
