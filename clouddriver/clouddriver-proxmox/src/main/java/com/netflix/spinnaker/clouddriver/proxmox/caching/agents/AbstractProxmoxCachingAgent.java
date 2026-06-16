package com.netflix.spinnaker.clouddriver.proxmox.caching.agents;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spectator.api.Registry;
import com.netflix.spinnaker.cats.agent.AccountAware;
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
import com.netflix.spinnaker.clouddriver.proxmox.security.ProxmoxNamedAccountCredentials;
import java.util.*;
import java.util.stream.Collectors;
import org.jetbrains.annotations.Nullable;

public abstract class AbstractProxmoxCachingAgent
    implements CachingAgent, OnDemandAgent, AccountAware {
  private static final String ON_DEMAND_TYPE =
      String.join(":", ProxmoxProvider.ID, OnDemandType.ServerGroup.getValue());
  private final ProxmoxNamedAccountCredentials credentials;
  private final OnDemandMetricsSupport metricsSupport;

  public AbstractProxmoxCachingAgent(
      ProxmoxNamedAccountCredentials credentials, Registry registry) {
    this.metricsSupport = new OnDemandMetricsSupport(registry, this, ON_DEMAND_TYPE);
    this.credentials = credentials;
  }

  @Override
  public OnDemandMetricsSupport getMetricsSupport() {
    return metricsSupport;
  }

  abstract Map<String, ?> getItems();

  private static final ObjectMapper objectMapper = new ObjectMapper();

  @Override
  public CacheResult loadData(ProviderCache providerCache) {
    Map<String, ?> items = getItems();
    Collection<CacheData> cacheData =
        items.entrySet().stream()
            .map(
                item -> {
                  Map<String, Object> attributes =
                      objectMapper.convertValue(item, new TypeReference<>() {});
                  return new DefaultCacheData(item.getKey(), attributes, Collections.emptyMap());
                })
            .collect(Collectors.toUnmodifiableList());
    return new DefaultCacheResult(Map.of(getAccountName(), cacheData));
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
