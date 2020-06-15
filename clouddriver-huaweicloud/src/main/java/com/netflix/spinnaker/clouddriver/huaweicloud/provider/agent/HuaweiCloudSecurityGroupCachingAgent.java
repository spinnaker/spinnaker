/*
 * Copyright 2019 Huawei Technologies Co.,Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package com.netflix.spinnaker.clouddriver.huaweicloud.provider.agent;

import static com.netflix.spinnaker.cats.agent.AgentDataType.Authority.AUTHORITATIVE;
import static com.netflix.spinnaker.clouddriver.huaweicloud.HuaweiCloudProvider.ID;
import static com.netflix.spinnaker.clouddriver.huaweicloud.cache.Keys.Namespace.SECURITY_GROUPS;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.huawei.openstack4j.openstack.vpc.v1.domain.SecurityGroup;
import com.huawei.openstack4j.openstack.vpc.v1.domain.SecurityGroupRule;
import com.netflix.spectator.api.Registry;
import com.netflix.spinnaker.cats.agent.AgentDataType;
import com.netflix.spinnaker.cats.provider.ProviderCache;
import com.netflix.spinnaker.clouddriver.cache.OnDemandAgent;
import com.netflix.spinnaker.clouddriver.cache.OnDemandMetricsSupport;
import com.netflix.spinnaker.clouddriver.cache.OnDemandType;
import com.netflix.spinnaker.clouddriver.huaweicloud.HuaweiCloudUtils;
import com.netflix.spinnaker.clouddriver.huaweicloud.cache.CacheResultBuilder;
import com.netflix.spinnaker.clouddriver.huaweicloud.cache.CacheResultBuilder.NamespaceCache;
import com.netflix.spinnaker.clouddriver.huaweicloud.cache.Keys;
import com.netflix.spinnaker.clouddriver.huaweicloud.model.HuaweiCloudSecurityGroupCacheData;
import com.netflix.spinnaker.clouddriver.huaweicloud.security.HuaweiCloudNamedAccountCredentials;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.slf4j.Logger;

public class HuaweiCloudSecurityGroupCachingAgent extends AbstractOnDemandCachingAgent {

  private static final Logger log =
      HuaweiCloudUtils.getLogger(HuaweiCloudSecurityGroupCachingAgent.class);

  private final OnDemandMetricsSupport onDemandMetricsSupport;

  public HuaweiCloudSecurityGroupCachingAgent(
      HuaweiCloudNamedAccountCredentials credentials,
      ObjectMapper objectMapper,
      Registry registry,
      String region) {

    super(credentials, objectMapper, SECURITY_GROUPS.ns, region);

    this.onDemandMetricsSupport =
        new OnDemandMetricsSupport(registry, this, ID + ":" + OnDemandType.SecurityGroup);
  }

  @Override
  String getAgentName() {
    return this.getClass().getSimpleName();
  }

  @Override
  public OnDemandMetricsSupport getMetricsSupport() {
    return this.onDemandMetricsSupport;
  }

  @Override
  public Collection<AgentDataType> getProvidedDataTypes() {
    return Collections.unmodifiableCollection(
        new ArrayList<AgentDataType>() {
          {
            add(AUTHORITATIVE.forType(SECURITY_GROUPS.ns));
          }
        });
  }

  @Override
  public boolean handles(OnDemandType type, String cloudProvider) {
    return OnDemandType.SecurityGroup.equals(type) && ID.equals(cloudProvider);
  }

  @Override
  public OnDemandAgent.OnDemandResult handle(
      ProviderCache providerCache, Map<String, ? extends Object> data) {

    if (!(!HuaweiCloudUtils.isEmptyStr(data.get("securityGroupName"))
        && this.getAccountName().equals(data.get("account"))
        && region.equals(data.get("region")))) {
      return null;
    }

    return handle(providerCache, (String) data.get("securityGroupName"));
  }

  @Override
  Optional<Object> getResourceByName(String name) {
    if (HuaweiCloudUtils.isEmptyStr(name)) {
      return Optional.empty();
    }

    List<? extends SecurityGroup> groups = getCloudClient().getSecurityGroups(region);
    if (groups.isEmpty()) {
      return Optional.empty();
    }

    List<? extends SecurityGroup> groups1 =
        groups.stream().filter(it -> name.equals(it.getName())).collect(Collectors.toList());

    if (groups1.size() == 1) {
      return Optional.of(groups1.get(0));
    }

    log.warn(
        "There is {} with name={} in region={}",
        groups1.isEmpty() ? "no security group" : "more than one security groups",
        name,
        region);

    return Optional.empty();
  }

  @Override
  String getResourceCacheDataId(Object resource) {
    SecurityGroup seg = (SecurityGroup) resource;
    return Keys.getSecurityGroupKey(seg.getName(), seg.getId(), getAccountName(), region);
  }

  @Override
  Collection<String> getOnDemandKeysToEvict(ProviderCache providerCache, String name) {
    return providerCache.filterIdentifiers(
        SECURITY_GROUPS.ns, Keys.getSecurityGroupKey(name, "*", getAccountName(), region));
  }

  @Override
  void buildCurrentNamespaceCacheData(CacheResultBuilder cacheResultBuilder) {
    List<? extends SecurityGroup> securityGroups = getCloudClient().getSecurityGroups(region);
    buildNamespaceCacheData(cacheResultBuilder, securityGroups, securityGroups);
  }

  @Override
  void buildSingleResourceCacheData(CacheResultBuilder cacheResultBuilder, Object resource) {
    List<SecurityGroup> securityGroups = new ArrayList(1);
    securityGroups.add((SecurityGroup) resource);

    List<? extends SecurityGroup> allSecurityGroups = getCloudClient().getSecurityGroups(region);

    buildNamespaceCacheData(cacheResultBuilder, securityGroups, allSecurityGroups);
  }

  private void buildNamespaceCacheData(
      CacheResultBuilder cacheResultBuilder,
      List<? extends SecurityGroup> securityGroups,
      List<? extends SecurityGroup> allSecurityGroups) {

    NamespaceCache nsCache = cacheResultBuilder.getNamespaceCache(SECURITY_GROUPS.ns);

    TypeReference<Map<String, Object>> typeRef = new TypeReference<Map<String, Object>>() {};

    Map<String, String> groupId2CacheIds =
        allSecurityGroups.stream()
            .collect(
                Collectors.toMap(
                    it -> it.getId(),
                    it ->
                        Keys.getSecurityGroupKey(
                            it.getName(), it.getId(), getAccountName(), region)));

    securityGroups.forEach(
        item -> {
          if (!groupId2CacheIds.containsKey(item.getId())) {
            log.warn(
                String.format(
                    "Can't find the security group(id={}) in current security groups",
                    item.getId()));
            return;
          }

          Map<String, String> relevantSecurityGroups = new HashMap();

          List<SecurityGroupRule> rules = item.getSecurityGroupRules();
          if (!HuaweiCloudUtils.isEmptyCollection(rules)) {
            rules.forEach(
                rule -> {
                  String remoteGroupId = rule.getRemoteGroupId();

                  if (!HuaweiCloudUtils.isEmptyStr(remoteGroupId)) {

                    if (groupId2CacheIds.containsKey(remoteGroupId)) {
                      relevantSecurityGroups.put(
                          remoteGroupId, groupId2CacheIds.get(remoteGroupId));
                    } else {
                      log.warn(
                          String.format(
                              "Can't find the remote security group(id={}) for rule({}) of security group(id={})",
                              remoteGroupId,
                              rule.getId(),
                              item.getId()));
                    }
                  }
                });
          }

          nsCache
              .getCacheDataBuilder(groupId2CacheIds.get(item.getId()))
              .setAttributes(
                  objectMapper.convertValue(
                      new HuaweiCloudSecurityGroupCacheData(item, relevantSecurityGroups),
                      typeRef));
        });
  }
}
