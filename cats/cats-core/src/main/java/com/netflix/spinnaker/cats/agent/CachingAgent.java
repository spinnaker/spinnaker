/*
 * Copyright 2014 Netflix, Inc.
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

import com.netflix.spinnaker.cats.cache.AgentIntrospection;
import com.netflix.spinnaker.cats.cache.CacheData;
import com.netflix.spinnaker.cats.cache.CacheIntrospectionStore;
import com.netflix.spinnaker.cats.cache.DefaultAgentIntrospection;
import com.netflix.spinnaker.cats.provider.ProviderCache;
import com.netflix.spinnaker.cats.provider.ProviderRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * A CachingAgent loads one or more types of data.
 * <p>
 * The data set for a caching agent is scoped to the provider and agent type. For example
 * an agent might load clusters for the AWS provider, and be scoped to a particular account
 * and region.
 */
public interface CachingAgent extends Agent {
  /**
   * @return the data types this Agent returns
   * @see com.netflix.spinnaker.cats.agent.AgentDataType.Authority
   */
  Collection<AgentDataType> getProvidedDataTypes();

  /**
   * Triggered by an AgentScheduler to tell this Agent to load its data.
   *
   * @param providerCache Cache associated with this Agent's provider
   * @return the complete set of data for this Agent.
   */
  CacheResult loadData(ProviderCache providerCache);

  default Optional<Map<String, String>> getCacheKeyPatterns() {
    return Optional.empty();
  }

  default AgentExecution getAgentExecution(ProviderRegistry providerRegistry) {
    return new CacheExecution(providerRegistry);
  }

  class CacheExecution implements AgentExecution {
    private final Logger log = LoggerFactory.getLogger(CacheExecution.class);
    private final ProviderRegistry providerRegistry;

    public CacheExecution(ProviderRegistry providerRegistry) {
      this.providerRegistry = providerRegistry;
    }

    @Override
    public void executeAgent(Agent agent) {
      AgentIntrospection introspection = new DefaultAgentIntrospection(agent);
      CacheResult result = executeAgentWithoutStore(agent);
      introspection.finish(result);
      CacheIntrospectionStore.getStore().recordAgent(introspection);
      storeAgentResult(agent, result);
    }

    public CacheResult executeAgentWithoutStore(Agent agent) {
      CachingAgent cachingAgent = (CachingAgent) agent;
      ProviderCache cache = providerRegistry.getProviderCache(cachingAgent.getProviderName());

      return cachingAgent.loadData(cache);
    }

    public void storeAgentResult(Agent agent, CacheResult result) {
      CachingAgent cachingAgent = (CachingAgent) agent;
      ProviderCache cache = providerRegistry.getProviderCache(cachingAgent.getProviderName());
      Collection<AgentDataType> providedTypes = cachingAgent.getProvidedDataTypes();
      Collection<String> authoritative = new HashSet<>(providedTypes.size());
      for (AgentDataType type : providedTypes) {
        if (type.getAuthority() == AgentDataType.Authority.AUTHORITATIVE) {
          authoritative.add(type.getTypeName());
        }
      }


      Optional<Map<String, String>> cacheKeyPatterns = cachingAgent.getCacheKeyPatterns();
      if (cacheKeyPatterns.isPresent()) {
        for (String type : authoritative) {
          String cacheKeyPatternForType = cacheKeyPatterns.get().get(type);
          if (cacheKeyPatternForType != null) {
            try {
              Set<String> cachedIdentifiersForType = result.getCacheResults().get(type)
                .stream()
                .map(CacheData::getId)
                .collect(Collectors.toSet());

              Collection<String> evictableIdentifiers = cache.filterIdentifiers(type, cacheKeyPatternForType)
                .stream()
                .filter(i -> !cachedIdentifiersForType.contains(i))
                .collect(Collectors.toSet());

              // any key that existed previously but was not re-cached by this agent is considered evictable
              if (!evictableIdentifiers.isEmpty()) {
                Collection<String> evictionsForType = result.getEvictions().computeIfAbsent(type, evictableKeys -> new ArrayList<>());
                evictionsForType.addAll(evictableIdentifiers);

                log.debug("Evicting stale identifiers: {}", evictableIdentifiers);
              }
            } catch (Exception e) {
              log.error("Failed to check for stale identifiers (type: {}, pattern: {}, agent: {})", type, cacheKeyPatternForType, agent, e);
            }
          }
        }
      }

      cache.putCacheResult(agent.getAgentType(), authoritative, result);
    }
  }
}
