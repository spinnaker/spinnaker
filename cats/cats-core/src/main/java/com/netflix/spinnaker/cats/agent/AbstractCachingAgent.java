/*
 * Copyright 2015 Netflix, Inc.
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

package com.netflix.spinnaker.cats.agent;

import com.netflix.spinnaker.cats.provider.ProviderCache;
import com.netflix.spinnaker.cats.provider.ProviderRegistry;

import java.util.Collection;
import java.util.HashSet;

public abstract class AbstractCachingAgent implements CachingAgent {
  @Override
  public AgentExecution getAgentExecution(ProviderRegistry providerRegistry) {
    return new CacheExecution(providerRegistry);
  }

  private static class CacheExecution implements AgentExecution {
    private final ProviderRegistry providerRegistry;

    public CacheExecution(ProviderRegistry providerRegistry) {
      this.providerRegistry = providerRegistry;
    }

    @Override
    public void executeAgent(Agent agent) {
      CachingAgent cachingAgent = (CachingAgent) agent;
      ProviderCache cache = providerRegistry.getProviderCache(cachingAgent.getProviderName());

      CacheResult result = cachingAgent.loadData(cache);
      Collection<AgentDataType> providedTypes = cachingAgent.getProvidedDataTypes();
      Collection<String> authoritative = new HashSet<>(providedTypes.size());
      for (AgentDataType type : providedTypes) {
        if (type.getAuthority() == AgentDataType.Authority.AUTHORITATIVE) {
          authoritative.add(type.getTypeName());
        }
      }
      cache.putCacheResult(agent.getAgentType(), authoritative, result);
    }
  }
}
