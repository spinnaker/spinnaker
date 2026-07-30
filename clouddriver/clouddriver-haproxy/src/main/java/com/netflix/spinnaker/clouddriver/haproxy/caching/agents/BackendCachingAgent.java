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

import com.netflix.spectator.api.Registry;
import com.netflix.spinnaker.cats.agent.AgentDataType;
import com.netflix.spinnaker.cats.agent.CacheResult;
import com.netflix.spinnaker.cats.cache.CacheData;
import com.netflix.spinnaker.cats.cache.DefaultCacheData;
import com.netflix.spinnaker.cats.provider.ProviderCache;
import com.netflix.spinnaker.clouddriver.haproxy.caching.HaProxyCacheKeys;
import com.netflix.spinnaker.clouddriver.haproxy.caching.HaProxyResourceType;
import com.netflix.spinnaker.clouddriver.haproxy.dataplane.api.BackendApi;
import com.netflix.spinnaker.clouddriver.haproxy.dataplane.api.ServerApi;
import com.netflix.spinnaker.clouddriver.haproxy.dataplane.model.Backend;
import com.netflix.spinnaker.clouddriver.haproxy.dataplane.model.RuntimeServer;
import com.netflix.spinnaker.clouddriver.haproxy.model.HaProxyServerHealth;
import com.netflix.spinnaker.clouddriver.haproxy.names.HaProxyMetadataNamer;
import com.netflix.spinnaker.clouddriver.haproxy.security.HaProxyNamedAccountCredentials;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import retrofit2.Response;

/**
 * Caches HAProxy backends ({@code full_section=true} so member servers and health check
 * configuration are included) as the provider's server group sections, along with each member
 * server's runtime health.
 */
public class BackendCachingAgent extends AbstractHaProxyCachingAgent {

  public BackendCachingAgent(
      HaProxyNamedAccountCredentials credentials, Registry registry, HaProxyMetadataNamer namer) {
    super(credentials, registry, namer);
  }

  @Override
  public Collection<AgentDataType> getProvidedDataTypes() {
    return List.of(
        AgentDataType.Authority.AUTHORITATIVE.forType(HaProxyResourceType.BACKEND.name()),
        AgentDataType.Authority.AUTHORITATIVE.forType(HaProxyResourceType.APPLICATION.name()),
        AgentDataType.Authority.AUTHORITATIVE.forType(HaProxyResourceType.CLUSTER.name()),
        AgentDataType.Authority.AUTHORITATIVE.forType(HaProxyResourceType.HEALTH.name()));
  }

  @Override
  public CacheResult loadData(ProviderCache providerCache) {
    CacheResult result = super.loadData(providerCache);
    Collection<CacheData> backends =
        result.getCacheResults().getOrDefault(HaProxyResourceType.BACKEND.name(), List.of());
    result.getCacheResults().put(HaProxyResourceType.HEALTH.name(), healthEntries(backends));
    return result;
  }

  /**
   * One HEALTH entry per backend server, carrying its address and the account's optional Proxmox
   * account link so instance providers can correlate health by IP.
   */
  @SuppressWarnings("unchecked")
  private Collection<CacheData> healthEntries(Collection<CacheData> backends) {
    List<CacheData> entries = new ArrayList<>();
    String proxmoxAccount = credentials.getManagedAccount().getProxmoxAccount();
    for (CacheData backend : backends) {
      String backendName = HaProxyCacheKeys.getName(backend.getId());
      Object runtimeServers = backend.getAttributes().get("runtime_servers");
      if (backendName == null || !(runtimeServers instanceof Map<?, ?> serverMap)) {
        continue;
      }
      for (Object value : serverMap.values()) {
        if (!(value instanceof Map<?, ?> runtime)
            || !(runtime.get("name") instanceof String name)) {
          continue;
        }
        Map<String, Object> attributes =
            new LinkedHashMap<>(
                HaProxyServerHealth.healthMap(
                    (String) runtime.get("admin_state"),
                    (String) runtime.get("operational_state"),
                    runtime.get("check_status")));
        attributes.put("backend", backendName);
        attributes.put("server", name);
        attributes.put("address", runtime.get("address"));
        attributes.put("port", runtime.get("port"));
        if (proxmoxAccount != null) {
          attributes.put("proxmoxAccount", proxmoxAccount);
        }
        entries.add(
            new DefaultCacheData(
                HaProxyCacheKeys.health(getAccountName(), getRegion(), backendName, name),
                attributes,
                Map.of(HaProxyResourceType.BACKEND.name(), List.of(backend.getId()))));
      }
    }
    return entries;
  }

  @Override
  Map<String, CachedResource> getItems() {
    try {
      Response<List<Backend>> response =
          credentials.getApi(BackendApi.class).getBackends(null, true).execute();
      if (!response.isSuccessful() || response.body() == null) {
        log.warn(
            "Failed to fetch backends for account {}: HTTP {}",
            credentials.getName(),
            response.code());
        return Map.of();
      }
      Map<String, CachedResource> result = new HashMap<>();
      for (Backend backend : response.body()) {
        if (backend.getName() == null) {
          continue;
        }
        Map<String, Object> attributes = objectMapper.convertValue(backend, ATTRIBUTES);
        attributes.put("runtime_servers", fetchRuntimeServers(backend.getName()));
        result.put(
            HaProxyCacheKeys.backend(getAccountName(), getRegion(), backend.getName()),
            new CachedResource(backend.getName(), backend.getMetadata(), attributes));
      }
      return result;
    } catch (IOException e) {
      log.error("Failed to fetch backends for account {}", credentials.getName(), e);
      return Map.of();
    }
  }

  /** Runtime state (admin/operational) per server name; empty when the runtime API fails. */
  private Map<String, Map<String, Object>> fetchRuntimeServers(String backendName) {
    try {
      Response<List<RuntimeServer>> response =
          credentials.getApi(ServerApi.class).getAllRuntimeServer(backendName).execute();
      if (!response.isSuccessful() || response.body() == null) {
        log.warn(
            "Failed to fetch runtime servers for backend {} in account {}: HTTP {}",
            backendName,
            credentials.getName(),
            response.code());
        return Map.of();
      }
      Map<String, Map<String, Object>> runtimeServers = new LinkedHashMap<>();
      for (RuntimeServer server : response.body()) {
        if (server.getName() != null) {
          runtimeServers.put(server.getName(), objectMapper.convertValue(server, ATTRIBUTES));
        }
      }
      return runtimeServers;
    } catch (IOException e) {
      log.error(
          "Failed to fetch runtime servers for backend {} in account {}",
          backendName,
          credentials.getName(),
          e);
      return Map.of();
    }
  }
}
