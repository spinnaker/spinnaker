/*
 * Copyright 2026 McIntosh.farm
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
package com.netflix.spinnaker.clouddriver.haproxy.provider.view;

import com.netflix.spinnaker.cats.cache.Cache;
import com.netflix.spinnaker.cats.cache.CacheData;
import com.netflix.spinnaker.clouddriver.haproxy.HaProxyProvider;
import com.netflix.spinnaker.clouddriver.haproxy.caching.HaProxyCacheKeys;
import com.netflix.spinnaker.clouddriver.haproxy.caching.HaProxyResourceType;
import com.netflix.spinnaker.clouddriver.haproxy.model.HaProxyLoadBalancer;
import com.netflix.spinnaker.clouddriver.haproxy.names.HaProxyMetadataNamer;
import com.netflix.spinnaker.clouddriver.haproxy.names.HaProxyResource;
import com.netflix.spinnaker.clouddriver.model.LoadBalancerInstance;
import com.netflix.spinnaker.clouddriver.model.LoadBalancerProvider;
import com.netflix.spinnaker.clouddriver.model.LoadBalancerServerGroup;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.Data;
import org.springframework.stereotype.Component;

/** Presents cached HAProxy frontends as Spinnaker load balancers. */
@Component
public class HaProxyLoadBalancerProvider implements LoadBalancerProvider<HaProxyLoadBalancer> {

  private final Cache cacheView;
  private final HaProxyMetadataNamer namer;

  public HaProxyLoadBalancerProvider(Cache cacheView, HaProxyMetadataNamer namer) {
    this.cacheView = cacheView;
    this.namer = namer;
  }

  @Override
  public String getCloudProvider() {
    return HaProxyProvider.ID;
  }

  @Override
  public Set<HaProxyLoadBalancer> getApplicationLoadBalancers(String application) {
    CacheData app =
        cacheView.get(
            HaProxyResourceType.APPLICATION.name(), HaProxyCacheKeys.application(application));
    if (app == null) {
      return Set.of();
    }
    Collection<String> frontendKeys =
        app.getRelationships().getOrDefault(HaProxyResourceType.FRONTEND.name(), List.of());
    return cacheView.getAll(HaProxyResourceType.FRONTEND.name(), frontendKeys).stream()
        .map(this::toLoadBalancer)
        .filter(Objects::nonNull)
        .collect(Collectors.toCollection(LinkedHashSet::new));
  }

  @Override
  public List<HaProxyLoadBalancerSummary> list() {
    Map<String, HaProxyLoadBalancerSummary> summaries = new LinkedHashMap<>();
    for (CacheData frontend : cacheView.getAll(HaProxyResourceType.FRONTEND.name())) {
      HaProxyLoadBalancerDetail detail = toDetail(frontend);
      if (detail == null) {
        continue;
      }
      summaries.computeIfAbsent(detail.getName(), HaProxyLoadBalancerSummary::new).add(detail);
    }
    return new ArrayList<>(summaries.values());
  }

  @Override
  public HaProxyLoadBalancerSummary get(String name) {
    return list().stream().filter(s -> s.getName().equals(name)).findFirst().orElse(null);
  }

  @Override
  public List<HaProxyLoadBalancerDetail> byAccountAndRegionAndName(
      String account, String region, String name) {
    CacheData frontend =
        cacheView.get(
            HaProxyResourceType.FRONTEND.name(), HaProxyCacheKeys.frontend(account, region, name));
    if (frontend == null) {
      return List.of();
    }
    return List.of(toDetail(frontend));
  }

  private HaProxyLoadBalancerDetail toDetail(CacheData frontend) {
    String name = HaProxyCacheKeys.getName(frontend.getId());
    if (name == null) {
      return null;
    }
    return new HaProxyLoadBalancerDetail(
        HaProxyCacheKeys.getAccount(frontend.getId()),
        HaProxyCacheKeys.getRegion(frontend.getId()),
        name);
  }

  @SuppressWarnings("unchecked")
  private HaProxyLoadBalancer toLoadBalancer(CacheData frontend) {
    String name = HaProxyCacheKeys.getName(frontend.getId());
    String account = HaProxyCacheKeys.getAccount(frontend.getId());
    String region = HaProxyCacheKeys.getRegion(frontend.getId());
    if (name == null || account == null || region == null) {
      return null;
    }

    Map<String, Object> attributes = frontend.getAttributes();
    Map<String, Object> metadata = (Map<String, Object>) attributes.get("metadata");
    String defaultBackend = (String) attributes.get("default_backend");

    // Attached server groups: the default backend plus any use_backend switching rule targets.
    Set<String> backendNames = new LinkedHashSet<>();
    if (defaultBackend != null) {
      backendNames.add(defaultBackend);
    }
    Object switchingRules = attributes.get("backend_switching_rule_list");
    if (switchingRules instanceof Collection<?> rules) {
      for (Object rule : rules) {
        if (rule instanceof Map<?, ?> ruleMap && ruleMap.get("name") instanceof String target) {
          backendNames.add(target);
        }
      }
    }

    return HaProxyLoadBalancer.builder()
        .name(name)
        .account(account)
        .region(region)
        .moniker(namer.deriveMoniker(new NamedSection(name, metadata)))
        .mode((String) attributes.get("mode"))
        .binds((Map<String, Object>) attributes.get("binds"))
        .defaultBackend(defaultBackend)
        .metadata(metadata)
        .serverGroups(serverGroupsFor(account, region, backendNames))
        .build();
  }

  @SuppressWarnings("unchecked")
  private Set<LoadBalancerServerGroup> serverGroupsFor(
      String account, String region, Set<String> backendNames) {
    if (backendNames.isEmpty()) {
      return Set.of();
    }
    List<String> backendKeys =
        backendNames.stream()
            .map(backend -> HaProxyCacheKeys.backend(account, region, backend))
            .collect(Collectors.toList());
    Map<String, CacheData> backends =
        cacheView.getAll(HaProxyResourceType.BACKEND.name(), backendKeys).stream()
            .collect(Collectors.toMap(CacheData::getId, data -> data));

    Set<LoadBalancerServerGroup> serverGroups = new LinkedHashSet<>();
    for (String backendName : backendNames) {
      CacheData backend = backends.get(HaProxyCacheKeys.backend(account, region, backendName));

      Set<LoadBalancerInstance> instances = new LinkedHashSet<>();
      Boolean disabled = null;
      if (backend != null) {
        disabled = (Boolean) backend.getAttributes().get("disabled");
        Object servers = backend.getAttributes().get("servers");
        if (servers instanceof Map<?, ?> serverMap) {
          for (Object server : serverMap.values()) {
            if (server instanceof Map<?, ?> attrs && attrs.get("name") instanceof String id) {
              instances.add(LoadBalancerInstance.builder().id(id).name(id).zone(region).build());
            }
          }
        }
      }

      serverGroups.add(
          LoadBalancerServerGroup.builder()
              .name(backendName)
              .account(account)
              .region(region)
              .cloudProvider(HaProxyProvider.ID)
              .isDisabled(disabled)
              .instances(instances)
              .build());
    }
    return serverGroups;
  }

  /** Wraps a cached section name and metadata for moniker derivation. */
  private record NamedSection(String name, Map<String, Object> metadata)
      implements HaProxyResource {
    @Override
    public String getName() {
      return name;
    }

    @Override
    public Map<String, Object> getMetadata() {
      return metadata;
    }
  }

  @Data
  public static class HaProxyLoadBalancerSummary implements Item {
    private final String name;
    private final Map<String, HaProxyLoadBalancerAccount> byAccounts = new LinkedHashMap<>();

    void add(HaProxyLoadBalancerDetail detail) {
      byAccounts.computeIfAbsent(detail.getAccount(), HaProxyLoadBalancerAccount::new).add(detail);
    }

    @Override
    public List<HaProxyLoadBalancerAccount> getByAccounts() {
      return new ArrayList<>(byAccounts.values());
    }
  }

  @Data
  public static class HaProxyLoadBalancerAccount implements ByAccount {
    private final String name;
    private final Map<String, HaProxyLoadBalancerRegion> byRegions = new LinkedHashMap<>();

    void add(HaProxyLoadBalancerDetail detail) {
      byRegions
          .computeIfAbsent(detail.getRegion(), HaProxyLoadBalancerRegion::new)
          .getLoadBalancers()
          .add(detail);
    }

    @Override
    public List<HaProxyLoadBalancerRegion> getByRegions() {
      return new ArrayList<>(byRegions.values());
    }
  }

  @Data
  public static class HaProxyLoadBalancerRegion implements ByRegion {
    private final String name;
    private final List<HaProxyLoadBalancerDetail> loadBalancers = new ArrayList<>();
  }

  @Data
  public static class HaProxyLoadBalancerDetail implements Details {
    private final String account;
    private final String region;
    private final String name;
    private final String type = HaProxyProvider.ID;
  }
}
