package com.netflix.spinnaker.clouddriver.proxmox.caching.agents;

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
import com.netflix.spinnaker.clouddriver.proxmox.ProxmoxProvider;
import com.netflix.spinnaker.clouddriver.proxmox.caching.ProxmoxCacheKeys;
import com.netflix.spinnaker.clouddriver.proxmox.caching.ProxmoxResourceType;
import com.netflix.spinnaker.clouddriver.proxmox.model.ProxmoxNode;
import com.netflix.spinnaker.clouddriver.proxmox.names.ProxmoxResource;
import com.netflix.spinnaker.clouddriver.proxmox.names.ProxmoxTagNamer;
import com.netflix.spinnaker.clouddriver.proxmox.security.ProxmoxNamedAccountCredentials;
import com.netflix.spinnaker.moniker.Moniker;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.Assert;

public abstract class AbstractProxmoxCachingAgent
    implements CachingAgent, OnDemandAgent, AccountAware {
  private static final String ON_DEMAND_TYPE =
      String.join(":", ProxmoxProvider.ID, OnDemandType.ServerGroup.getValue());
  protected final Logger log = LoggerFactory.getLogger(getClass());
  protected final ProxmoxNamedAccountCredentials credentials;
  protected final ProxmoxTagNamer tagNamer;
  private final OnDemandMetricsSupport metricsSupport;

  public AbstractProxmoxCachingAgent(
      ProxmoxNamedAccountCredentials credentials, Registry registry, ProxmoxTagNamer tagNamer) {
    Assert.notNull(credentials, "credentials cannot be null");
    this.credentials = credentials;
    this.tagNamer = tagNamer;
    this.metricsSupport = new OnDemandMetricsSupport(registry, this, ON_DEMAND_TYPE);
  }

  @Override
  public OnDemandMetricsSupport getMetricsSupport() {
    return metricsSupport;
  }

  abstract Map<String, ?> getItems();

  protected List<ProxmoxNode> fetchNodes() throws IOException {
    var response = credentials.getCredentials().getNodes().execute();
    if (!response.isSuccessful() || response.body() == null || response.body().getData() == null) {
      log.warn(
          "Failed to fetch nodes for account {}: HTTP {}", credentials.getName(), response.code());
      return List.of();
    }
    return response.body().getData();
  }

  private static final ObjectMapper objectMapper = new ObjectMapper();

  @Override
  public CacheResult loadData(ProviderCache providerCache) {
    Map<String, ?> items = getItems();
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
                  Map<String, Object> attributes =
                      objectMapper.convertValue(item.getValue(), new TypeReference<>() {});
                  Map<String, Collection<String>> relationships = new HashMap<>();
                  if (tagNamer != null && item.getValue() instanceof ProxmoxResource resource) {
                    Moniker moniker = tagNamer.deriveMoniker(resource);
                    String app = moniker.getApp();
                    if (app != null) {
                      attributes.put("spinnakerApp", app);
                      String appKey = ProxmoxCacheKeys.application(app);
                      appEntries.putIfAbsent(
                          appKey,
                          new DefaultCacheData(appKey, Map.of("name", app), new HashMap<>()));
                      appToResourceKeys
                          .computeIfAbsent(appKey, k -> new ArrayList<>())
                          .add(item.getKey());
                      relationships.put(ProxmoxResourceType.APPLICATION.name(), List.of(appKey));

                      String account = ProxmoxCacheKeys.getAccount(item.getKey());
                      if (account != null) {
                        String clusterName =
                            moniker.getCluster() != null ? moniker.getCluster() : app;
                        String clusterKey = ProxmoxCacheKeys.cluster(account, clusterName);
                        clusterToResourceKeys
                            .computeIfAbsent(clusterKey, k -> new ArrayList<>())
                            .add(item.getKey());
                        clusterAttrMap.putIfAbsent(
                            clusterKey,
                            new HashMap<>(
                                Map.of("name", clusterName, "accountName", account, "app", app)));
                        clusterToAppKey.putIfAbsent(clusterKey, appKey);
                        resourceToClusterKey.put(item.getKey(), clusterKey);
                        relationships.put(ProxmoxResourceType.CLUSTER.name(), List.of(clusterKey));
                      }
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
              appEntry.getRelationships().put(ProxmoxResourceType.CLUSTER.name(), clusterKeys);
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
            clusterRels.put(ProxmoxResourceType.APPLICATION.name(), List.of(appKey));
          }
          clusterRels.put(dataType, resourceKeys);
          clusterEntries.put(
              clusterKey, new DefaultCacheData(clusterKey, new HashMap<>(attrs), clusterRels));
        });

    Map<String, Collection<CacheData>> result = new HashMap<>();
    result.put(dataType, cacheData);
    if (!appEntries.isEmpty()) {
      result.put(ProxmoxResourceType.APPLICATION.name(), appEntries.values());
    }
    if (!clusterEntries.isEmpty()) {
      result.put(ProxmoxResourceType.CLUSTER.name(), clusterEntries.values());
    }
    return new DefaultCacheResult(result);
  }

  @Override
  public String getProviderName() {
    return ProxmoxProvider.ID;
  }

  @Override
  public String getAccountName() {
    return credentials.getName();
  }

  @Override
  public boolean handles(OnDemandType type, String cloudProvider) {
    return type == OnDemandType.ServerGroup && Objects.equals(cloudProvider, ProxmoxProvider.ID);
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
