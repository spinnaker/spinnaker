/*
 * Copyright 2017 Lookout, Inc.
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

package com.netflix.spinnaker.clouddriver.ecs.cache.client;

import static com.netflix.spinnaker.clouddriver.core.provider.agent.Namespace.LOAD_BALANCERS;
import static com.netflix.spinnaker.clouddriver.core.provider.agent.Namespace.TARGET_GROUPS;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.cats.cache.Cache;
import com.netflix.spinnaker.cats.cache.CacheData;
import com.netflix.spinnaker.cats.cache.RelationshipCacheFilter;
import com.netflix.spinnaker.clouddriver.aws.data.Keys;
import com.netflix.spinnaker.clouddriver.ecs.cache.model.EcsLoadBalancerCache;
import com.netflix.spinnaker.clouddriver.ecs.provider.view.EcsAccountMapper;
import java.util.*;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@Component
public class EcsLoadbalancerCacheClient {

  private final Cache cacheView;
  private final ObjectMapper objectMapper;
  private final EcsAccountMapper ecsAccountMapper;

  public EcsLoadbalancerCacheClient(
      Cache cacheView, ObjectMapper objectMapper, EcsAccountMapper ecsAccountMapper) {
    this.cacheView = cacheView;
    this.objectMapper = objectMapper;
    this.ecsAccountMapper = ecsAccountMapper;
  }

  public List<EcsLoadBalancerCache> find(String account, String region) {
    Set<Map<String, Object>> loadbalancerAttributes = fetchFromCache(account, region);
    return convertToLoadbalancer(loadbalancerAttributes);
  }

  public List<EcsLoadBalancerCache> findAll() {
    return find("*", "*");
  }

  private Set<Map<String, Object>> fetchFromCache(String account, String region) {
    String accountFilter = account != null ? account : "*";
    if (!"*".equals(accountFilter)) {
      String awsAccountName = ecsAccountMapper.fromEcsAccountNameToAwsAccountName(accountFilter);
      if (awsAccountName != null) {
        accountFilter = awsAccountName;
      }
    }
    String regionFilter = region != null ? region : "*";

    String searchKey = Keys.getLoadBalancerKey("*", accountFilter, regionFilter, "*", "*") + "*";

    Collection<String> loadbalancerKeys =
        cacheView.filterIdentifiers(LOAD_BALANCERS.getNs(), searchKey);

    return fetchLoadBalancerAttributes(loadbalancerKeys);
  }

  public List<EcsLoadBalancerCache> findWithTargetGroups(Set<String> targetGroupKeys) {
    Set<CacheData> targetGroupCacheData =
        new HashSet<>(
            cacheView.getAll(
                TARGET_GROUPS.getNs(),
                targetGroupKeys,
                RelationshipCacheFilter.include(LOAD_BALANCERS.getNs())));
    Set<String> lbKeys = inferAssociatedLoadBalancers(targetGroupCacheData);
    Set<Map<String, Object>> loadbalancerAttributes = fetchLoadBalancerAttributes(lbKeys);
    return convertToLoadbalancer(loadbalancerAttributes);
  }

  private EcsLoadBalancerCache convertToLoadBalancer(Map<String, Object> targetGroupAttributes) {
    return objectMapper.convertValue(targetGroupAttributes, EcsLoadBalancerCache.class);
  }

  private List<EcsLoadBalancerCache> convertToLoadbalancer(
      Collection<Map<String, Object>> targetGroupAttributes) {
    List<EcsLoadBalancerCache> ecsTargetGroups = new ArrayList<>();

    for (Map<String, Object> attributes : targetGroupAttributes) {
      ecsTargetGroups.add(convertToLoadBalancer(attributes));
    }

    return ecsTargetGroups;
  }

  private Set<Map<String, Object>> fetchLoadBalancerAttributes(
      Collection<String> loadBalancerKeys) {
    Set<CacheData> loadBalancerCache = fetchLoadBalancers(loadBalancerKeys);

    return loadBalancerCache.stream()
        .filter(this::hashTargetGroups)
        .map(this::convertCacheData)
        .collect(Collectors.toSet());
  }

  private boolean hashTargetGroups(CacheData loadbalancerCache) {
    return loadbalancerCache.getRelationships().get("targetGroups") != null
        && loadbalancerCache.getRelationships().get("targetGroups").size() > 0;
  }

  private Map<String, Object> convertCacheData(CacheData loadbalancerCache) {
    Map<String, Object> attributes = loadbalancerCache.getAttributes();
    Map<String, String> parts = Keys.parse(loadbalancerCache.getId());

    attributes.put("region", parts.get("region"));
    String ecsAccount = ecsAccountMapper.fromAwsAccountNameToEcsAccountName(parts.get("account"));
    attributes.put("account", ecsAccount);
    attributes.put("loadBalancerType", parts.get("loadBalancerType"));
    attributes.put(
        "targetGroups",
        loadbalancerCache.getRelationships().get("targetGroups").stream()
            .map(id -> Keys.parse(id).get("targetGroup"))
            .collect(Collectors.toSet()));

    return attributes;
  }

  private Set<String> inferAssociatedLoadBalancers(Set<CacheData> targetGroups) {
    Set<String> loadbalancersAssociatedWithTargetGroups = new HashSet<>();

    for (CacheData targetGroup : targetGroups) {
      Collection<String> relatedLoadbalancer =
          targetGroup.getRelationships().get(LOAD_BALANCERS.ns);
      if (relatedLoadbalancer != null && relatedLoadbalancer.size() > 0) {
        loadbalancersAssociatedWithTargetGroups.addAll(relatedLoadbalancer);
      }
    }
    return loadbalancersAssociatedWithTargetGroups;
  }

  private Set<CacheData> fetchLoadBalancers(Collection<String> loadBalancerKeys) {
    return new HashSet<>(
        cacheView.getAll(
            LOAD_BALANCERS.getNs(),
            loadBalancerKeys,
            RelationshipCacheFilter.include(TARGET_GROUPS.getNs())));
  }
}
