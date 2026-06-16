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
import com.netflix.spinnaker.clouddriver.proxmox.model.ProxmoxNode;
import com.netflix.spinnaker.clouddriver.proxmox.security.ProxmoxNamedAccountCredentials;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class AbstractProxmoxCachingAgent
    implements CachingAgent, OnDemandAgent, AccountAware {
  private static final String ON_DEMAND_TYPE =
      String.join(":", ProxmoxProvider.ID, OnDemandType.ServerGroup.getValue());
  protected final Logger log = LoggerFactory.getLogger(getClass());
  protected final ProxmoxNamedAccountCredentials credentials;
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
    Collection<CacheData> cacheData =
        items.entrySet().stream()
            .map(
                item -> {
                  Map<String, Object> attributes =
                      objectMapper.convertValue(item.getValue(), new TypeReference<>() {});
                  return new DefaultCacheData(item.getKey(), attributes, Collections.emptyMap());
                })
            .collect(Collectors.toUnmodifiableList());
    return new DefaultCacheResult(Map.of(dataType, cacheData));
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
