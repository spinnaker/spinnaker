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
package com.netflix.spinnaker.clouddriver.haproxy.caching.agents;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spectator.api.Registry;
import com.netflix.spinnaker.cats.agent.AccountAware;
import com.netflix.spinnaker.cats.agent.AgentDataType;
import com.netflix.spinnaker.cats.agent.CacheResult;
import com.netflix.spinnaker.cats.agent.CachingAgent;
import com.netflix.spinnaker.cats.agent.DefaultCacheResult;
import com.netflix.spinnaker.cats.cache.CacheData;
import com.netflix.spinnaker.cats.cache.DefaultCacheData;
import com.netflix.spinnaker.cats.provider.ProviderCache;
import com.netflix.spinnaker.clouddriver.cache.OnDemandAgent;
import com.netflix.spinnaker.clouddriver.cache.OnDemandMetricsSupport;
import com.netflix.spinnaker.clouddriver.cache.OnDemandType;
import com.netflix.spinnaker.clouddriver.haproxy.HaProxyProvider;
import com.netflix.spinnaker.clouddriver.haproxy.caching.HaProxyCacheKeys;
import com.netflix.spinnaker.clouddriver.haproxy.caching.HaProxyResourceType;
import com.netflix.spinnaker.clouddriver.haproxy.names.HaProxyMetadataNamer;
import com.netflix.spinnaker.clouddriver.haproxy.names.HaProxyResource;
import com.netflix.spinnaker.clouddriver.haproxy.security.HaProxyNamedAccountCredentials;
import com.netflix.spinnaker.moniker.Moniker;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.Assert;

public abstract class AbstractHaProxyCachingAgent
    implements CachingAgent, OnDemandAgent, AccountAware {
  private static final String ON_DEMAND_TYPE =
      String.join(":", HaProxyProvider.ID, OnDemandType.LoadBalancer.getValue());
  protected static final ObjectMapper objectMapper = new ObjectMapper();
  protected static final TypeReference<Map<String, Object>> ATTRIBUTES = new TypeReference<>() {};

  protected final Logger log = LoggerFactory.getLogger(getClass());
  protected final HaProxyNamedAccountCredentials credentials;
  protected final HaProxyMetadataNamer namer;
  private final OnDemandMetricsSupport metricsSupport;

  protected AbstractHaProxyCachingAgent(
      HaProxyNamedAccountCredentials credentials, Registry registry, HaProxyMetadataNamer namer) {
    Assert.notNull(credentials, "credentials cannot be null");
    this.credentials = credentials;
    this.namer = namer;
    this.metricsSupport = new OnDemandMetricsSupport(registry, this, ON_DEMAND_TYPE);
  }

  @Override
  public OnDemandMetricsSupport getMetricsSupport() {
    return metricsSupport;
  }

  /** Cache key mapped to the cacheable HAProxy configuration section. */
  abstract Map<String, CachedResource> getItems();

  /** A named configuration section plus the attribute map to cache for it. */
  protected record CachedResource(
      String name, Map<String, Object> metadata, Map<String, Object> attributes)
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

  @Override
  public CacheResult loadData(ProviderCache providerCache) {
    Map<String, CachedResource> items = getItems();
    String dataType =
        getProvidedDataTypes().stream()
            .filter(dt -> dt.getAuthority() == AgentDataType.Authority.AUTHORITATIVE)
            .findFirst()
            .map(AgentDataType::getTypeName)
            .orElseThrow(
                () ->
                    new IllegalStateException(
                        "No authoritative data type declared for " + getAgentType()));

    Map<String, CacheData> appEntries = new LinkedHashMap<>();
    Map<String, List<String>> appToResourceKeys = new LinkedHashMap<>();
    Map<String, List<String>> clusterToResourceKeys = new LinkedHashMap<>();
    Map<String, Map<String, Object>> clusterAttrMap = new LinkedHashMap<>();
    Map<String, String> clusterToAppKey = new LinkedHashMap<>();
    Map<String, String> resourceToClusterKey = new LinkedHashMap<>();

    Collection<CacheData> cacheData =
        items.entrySet().stream()
            .map(
                item -> {
                  CachedResource resource = item.getValue();
                  Map<String, Object> attributes = new HashMap<>(resource.attributes());
                  Map<String, Collection<String>> relationships = new HashMap<>();
                  Moniker moniker = namer.deriveMoniker(resource);
                  String app = moniker.getApp();
                  if (app != null) {
                    attributes.put("spinnakerApp", app);
                    String appKey = HaProxyCacheKeys.application(app);
                    appEntries.putIfAbsent(
                        appKey, new DefaultCacheData(appKey, Map.of("name", app), new HashMap<>()));
                    appToResourceKeys
                        .computeIfAbsent(appKey, k -> new ArrayList<>())
                        .add(item.getKey());
                    relationships.put(HaProxyResourceType.APPLICATION.name(), List.of(appKey));

                    String account = HaProxyCacheKeys.getAccount(item.getKey());
                    if (account != null) {
                      String clusterName =
                          moniker.getCluster() != null ? moniker.getCluster() : app;
                      String clusterKey = HaProxyCacheKeys.cluster(account, clusterName);
                      clusterToResourceKeys
                          .computeIfAbsent(clusterKey, k -> new ArrayList<>())
                          .add(item.getKey());
                      clusterAttrMap.putIfAbsent(
                          clusterKey,
                          new HashMap<>(
                              Map.of("name", clusterName, "accountName", account, "app", app)));
                      clusterToAppKey.putIfAbsent(clusterKey, appKey);
                      resourceToClusterKey.put(item.getKey(), clusterKey);
                      relationships.put(HaProxyResourceType.CLUSTER.name(), List.of(clusterKey));
                    }
                  }
                  return new DefaultCacheData(item.getKey(), attributes, relationships);
                })
            .collect(Collectors.toUnmodifiableList());

    appToResourceKeys.forEach(
        (appKey, resourceKeys) -> {
          CacheData appEntry = appEntries.get(appKey);
          if (appEntry != null) {
            appEntry.getRelationships().put(dataType, resourceKeys);
            List<String> clusterKeys =
                resourceKeys.stream()
                    .map(resourceToClusterKey::get)
                    .filter(Objects::nonNull)
                    .distinct()
                    .collect(Collectors.toList());
            if (!clusterKeys.isEmpty()) {
              appEntry.getRelationships().put(HaProxyResourceType.CLUSTER.name(), clusterKeys);
            }
          }
        });

    Map<String, CacheData> clusterEntries = new LinkedHashMap<>();
    clusterAttrMap.forEach(
        (clusterKey, attrs) -> {
          List<String> resourceKeys = clusterToResourceKeys.getOrDefault(clusterKey, List.of());
          Map<String, Collection<String>> clusterRels = new HashMap<>();
          String appKey = clusterToAppKey.get(clusterKey);
          if (appKey != null) {
            clusterRels.put(HaProxyResourceType.APPLICATION.name(), List.of(appKey));
          }
          clusterRels.put(dataType, resourceKeys);
          clusterEntries.put(
              clusterKey, new DefaultCacheData(clusterKey, new HashMap<>(attrs), clusterRels));
        });

    Map<String, Collection<CacheData>> result = new HashMap<>();
    result.put(dataType, cacheData);
    if (!appEntries.isEmpty()) {
      result.put(HaProxyResourceType.APPLICATION.name(), appEntries.values());
    }
    if (!clusterEntries.isEmpty()) {
      result.put(HaProxyResourceType.CLUSTER.name(), clusterEntries.values());
    }
    return new DefaultCacheResult(result);
  }

  @Override
  public String getProviderName() {
    return HaProxyProvider.ID;
  }

  @Override
  public String getAccountName() {
    return credentials.getName();
  }

  protected String getRegion() {
    return credentials.getRegion();
  }

  @Override
  public boolean handles(OnDemandType type, String cloudProvider) {
    return type == OnDemandType.LoadBalancer && Objects.equals(cloudProvider, HaProxyProvider.ID);
  }

  @Override
  public String getAgentType() {
    return getAccountName() + "/" + getClass().getName();
  }

  @Override
  public String getOnDemandAgentType() {
    return getAgentType() + "-onDemand";
  }

  @Override
  public @Nullable OnDemandResult handle(ProviderCache providerCache, Map<String, ?> data) {
    return null;
  }

  @Override
  public Collection<Map<String, Object>> pendingOnDemandRequests(ProviderCache providerCache) {
    return List.of();
  }
}
