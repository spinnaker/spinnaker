/*
 * Copyright 2020 YANDEX LLC
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

package com.netflix.spinnaker.clouddriver.yandex.provider.view;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Strings;
import com.netflix.spinnaker.cats.cache.Cache;
import com.netflix.spinnaker.clouddriver.model.LoadBalancerInstance;
import com.netflix.spinnaker.clouddriver.model.LoadBalancerProvider;
import com.netflix.spinnaker.clouddriver.model.LoadBalancerServerGroup;
import com.netflix.spinnaker.clouddriver.yandex.YandexCloudProvider;
import com.netflix.spinnaker.clouddriver.yandex.model.YandexCloudInstance;
import com.netflix.spinnaker.clouddriver.yandex.model.YandexCloudLoadBalancer;
import com.netflix.spinnaker.clouddriver.yandex.model.YandexCloudServerGroup;
import com.netflix.spinnaker.clouddriver.yandex.model.health.YandexLoadBalancerHealth;
import com.netflix.spinnaker.clouddriver.yandex.provider.Keys;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.AllArgsConstructor;
import lombok.Data;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class YandexLoadBalancerProvider implements LoadBalancerProvider<YandexCloudLoadBalancer> {
  private YandexServerGroupProvider yandexServerGroupProvider;
  private final CacheClient<YandexCloudLoadBalancer> cacheClient;

  @Autowired
  public YandexLoadBalancerProvider(
      Cache cacheView,
      ObjectMapper objectMapper,
      YandexServerGroupProvider yandexServerGroupProvider) {
    this.yandexServerGroupProvider = yandexServerGroupProvider;
    this.cacheClient =
        new CacheClient<>(
            cacheView, objectMapper, Keys.Namespace.LOAD_BALANCERS, YandexCloudLoadBalancer.class);
  }

  public String getCloudProvider() {
    return YandexCloudProvider.ID;
  }

  @Override
  public Set<YandexCloudLoadBalancer> getApplicationLoadBalancers(String applicationName) {
    String pattern = Keys.getLoadBalancerKey("*", "*", "*", applicationName + "*");
    Collection<String> identifiers = cacheClient.filterIdentifiers(pattern);
    if (!Strings.isNullOrEmpty(applicationName)) {
      yandexServerGroupProvider.getByApplication(applicationName).stream()
          .map(g -> Keys.getServerGroupKey("*", g.getId(), "*", g.getName()))
          .map(k -> yandexServerGroupProvider.getLoadBalancersKeys(k))
          .forEach(identifiers::addAll);
    }
    return identifiers.stream().map(this::loadBalancersFromKey).collect(Collectors.toSet());
  }

  private YandexCloudLoadBalancer loadBalancersFromKey(String key) {
    YandexCloudLoadBalancer loadBalancer = cacheClient.findOne(key).get();
    Collection<String> serverGroupKeys =
        cacheClient.getRelationKeys(key, Keys.Namespace.SERVER_GROUPS);
    if (!serverGroupKeys.isEmpty()) {
      List<LoadBalancerServerGroup> groups =
          yandexServerGroupProvider.getAll(serverGroupKeys, false).stream()
              .map(serverGroup -> buildLoadBalancerGroup(loadBalancer, serverGroup))
              .collect(Collectors.toList());
      loadBalancer.getServerGroups().addAll(groups);
    }
    return loadBalancer;
  }

  public List<YandexCloudLoadBalancer> getAll(Collection<String> keys) {
    return cacheClient.getAll(keys);
  }

  @NotNull
  private LoadBalancerServerGroup buildLoadBalancerGroup(
      YandexCloudLoadBalancer loadBalancer, YandexCloudServerGroup serverGroup) {
    Set<LoadBalancerInstance> instances =
        serverGroup.getInstances().stream()
            .map(instance -> buildLoadBalancerInstance(loadBalancer, serverGroup, instance))
            .collect(Collectors.toSet());

    LoadBalancerServerGroup loadBalancerServerGroup = new LoadBalancerServerGroup();
    loadBalancerServerGroup.setCloudProvider(YandexCloudProvider.ID);
    loadBalancerServerGroup.setName(serverGroup.getName());
    loadBalancerServerGroup.setRegion(serverGroup.getRegion());
    loadBalancerServerGroup.setIsDisabled(serverGroup.isDisabled());
    loadBalancerServerGroup.setDetachedInstances(Collections.emptySet());
    loadBalancerServerGroup.setInstances(instances);
    return loadBalancerServerGroup;
  }

  @NotNull
  private LoadBalancerInstance buildLoadBalancerInstance(
      YandexCloudLoadBalancer loadBalancer,
      YandexCloudServerGroup serverGroup,
      YandexCloudInstance instance) {
    List<YandexLoadBalancerHealth> targetGroups =
        loadBalancer
            .getHealths()
            .getOrDefault(
                serverGroup.getLoadBalancerIntegration().getTargetGroupId(),
                Collections.emptyList());
    YandexLoadBalancerHealth.Status.ServiceStatus stat =
        targetGroups.stream()
            .filter(instance::containsAddress)
            .findFirst()
            .map(health -> health.getStatus().toServiceStatus())
            .orElse(YandexLoadBalancerHealth.Status.ServiceStatus.OutOfService);
    return LoadBalancerInstance.builder()
        .id(instance.getId())
        .name(instance.getName())
        .zone(instance.getZone())
        .health(Collections.singletonMap("state", stat))
        .build();
  }

  public List<YandexLoadBalancerAccountRegionSummary> list() {
    Map<String, List<YandexCloudLoadBalancer>> loadBalancerMap =
        cacheClient.getAll(Keys.LOAD_BALANCER_WILDCARD).stream()
            .collect(Collectors.groupingBy(YandexCloudLoadBalancer::getName));
    return loadBalancerMap.entrySet().stream()
        .map(e -> convertToSummary(e.getKey(), e.getValue()))
        .collect(Collectors.toList());
  }

  public YandexLoadBalancerAccountRegionSummary get(String name) {
    String pattern = Keys.getLoadBalancerKey("*", "*", "*", name);
    List<YandexCloudLoadBalancer> balancers = cacheClient.findAll(pattern);
    return balancers.isEmpty() ? null : convertToSummary(name, balancers);
  }

  @NotNull
  private YandexLoadBalancerAccountRegionSummary convertToSummary(
      String name, List<YandexCloudLoadBalancer> balancers) {
    YandexLoadBalancerAccountRegionSummary summary = new YandexLoadBalancerAccountRegionSummary();
    summary.setName(name);
    balancers.stream()
        .map(this::buildLoadBalancerSummary)
        .forEach(
            s ->
                summary
                    .getMappedAccounts()
                    .computeIfAbsent(s.getAccount(), a -> new YandexLoadBalancerAccount())
                    .getMappedRegions()
                    .computeIfAbsent(s.getRegion(), r -> new YandexLoadBalancerAccountRegion())
                    .getLoadBalancers()
                    .add(s));
    return summary;
  }

  @NotNull
  private YandexLoadBalancerSummary buildLoadBalancerSummary(YandexCloudLoadBalancer balancer) {
    YandexLoadBalancerSummary summary = new YandexLoadBalancerSummary();
    summary.setId(balancer.getId());
    summary.setAccount(balancer.getAccount());
    summary.setName(balancer.getName());
    summary.setRegion(balancer.getRegion());
    return summary;
  }

  public List<YandexLoadBalancerDetails> byAccountAndRegionAndName(
      String account, String region, String name) {
    String pattern = Keys.getLoadBalancerKey(account, "*", "*", name);
    return cacheClient.findAll(pattern).stream()
        .filter(balancer -> balancer.getRegion().equals(region))
        .map(this::buildLoadBalancerDetails)
        .collect(Collectors.toList());
  }

  @NotNull
  private YandexLoadBalancerProvider.YandexLoadBalancerDetails buildLoadBalancerDetails(
      YandexCloudLoadBalancer balancer) {
    return new YandexLoadBalancerDetails(
        balancer.getName(),
        balancer.getBalancerType(),
        balancer.getSessionAffinity().name(),
        balancer.getCreatedTime(),
        balancer.getListeners());
  }

  @Data
  public static class YandexLoadBalancerAccountRegionSummary implements LoadBalancerProvider.Item {
    private String name;

    @JsonIgnore private Map<String, YandexLoadBalancerAccount> mappedAccounts = new HashMap<>();

    @JsonProperty("accounts")
    public List<YandexLoadBalancerAccount> getByAccounts() {
      return new ArrayList<>(mappedAccounts.values());
    }
  }

  @Data
  public static class YandexLoadBalancerAccount implements LoadBalancerProvider.ByAccount {
    private String name;

    @JsonIgnore
    private Map<String, YandexLoadBalancerAccountRegion> mappedRegions = new HashMap<>();

    @JsonProperty("regions")
    public List<YandexLoadBalancerAccountRegion> getByRegions() {
      return new ArrayList<>(mappedRegions.values());
    }
  }

  @Data
  private static class YandexLoadBalancerAccountRegion implements LoadBalancerProvider.ByRegion {
    private String name;
    private List<YandexLoadBalancerSummary> loadBalancers = new ArrayList<>();
  }

  @Data
  private static class YandexLoadBalancerSummary implements LoadBalancerProvider.Details {
    private String id;
    private String account;
    private String region;
    private String name;
    private String type = YandexCloudProvider.ID;
  }

  @Data
  @AllArgsConstructor
  private static class YandexLoadBalancerDetails implements LoadBalancerProvider.Details {
    private String loadBalancerName;
    YandexCloudLoadBalancer.BalancerType type;
    private String sessionAffinity;
    private Long createdTime;
    private List<YandexCloudLoadBalancer.Listener> listeners;
  }
}
