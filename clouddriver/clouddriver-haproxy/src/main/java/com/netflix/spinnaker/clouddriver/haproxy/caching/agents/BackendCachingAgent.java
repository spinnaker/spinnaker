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
import com.netflix.spinnaker.clouddriver.haproxy.caching.HaProxyCacheKeys;
import com.netflix.spinnaker.clouddriver.haproxy.caching.HaProxyResourceType;
import com.netflix.spinnaker.clouddriver.haproxy.dataplane.api.BackendApi;
import com.netflix.spinnaker.clouddriver.haproxy.dataplane.model.Backend;
import com.netflix.spinnaker.clouddriver.haproxy.names.HaProxyMetadataNamer;
import com.netflix.spinnaker.clouddriver.haproxy.security.HaProxyNamedAccountCredentials;
import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import retrofit2.Response;

/**
 * Caches HAProxy backends ({@code full_section=true} so member servers and health check
 * configuration are included) as the provider's server group sections.
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
        AgentDataType.Authority.AUTHORITATIVE.forType(HaProxyResourceType.CLUSTER.name()));
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
        result.put(
            HaProxyCacheKeys.backend(getAccountName(), getRegion(), backend.getName()),
            new CachedResource(
                backend.getName(),
                backend.getMetadata(),
                objectMapper.convertValue(backend, ATTRIBUTES)));
      }
      return result;
    } catch (IOException e) {
      log.error("Failed to fetch backends for account {}", credentials.getName(), e);
      return Map.of();
    }
  }
}
