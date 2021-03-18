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
package com.netflix.spinnaker.clouddriver.cache;

import com.netflix.spinnaker.cats.agent.Agent;
import com.netflix.spinnaker.cats.agent.AgentLock;
import com.netflix.spinnaker.cats.agent.AgentScheduler;
import com.netflix.spinnaker.cats.cache.CacheData;
import com.netflix.spinnaker.cats.module.CatsModule;
import com.netflix.spinnaker.cats.provider.Provider;
import com.netflix.spinnaker.cats.provider.ProviderCache;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class CatsOnDemandCacheUpdater implements OnDemandCacheUpdater {

  private static final Logger log = LoggerFactory.getLogger(CatsOnDemandCacheUpdater.class);

  private final List<Provider> providers;
  private final CatsModule catsModule;
  // TODO(rz): Deliberately not using <? extends AgentLock> since it results in
  //  compilation errors. This is a side-effect of migrating away from Groovy.
  //  I'm sure there's a way, but it's the early morning and I'm pretty tired!
  private final AgentScheduler agentScheduler;

  @Autowired
  public CatsOnDemandCacheUpdater(
      List<Provider> providers,
      CatsModule catsModule,
      AgentScheduler<? extends AgentLock> agentScheduler) {
    this.providers = providers;
    this.catsModule = catsModule;
    this.agentScheduler = agentScheduler;
  }

  private Collection<OnDemandAgent> getOnDemandAgents() {
    return providers.stream()
        .flatMap(
            provider -> provider.getAgents().stream().filter(it -> it instanceof OnDemandAgent))
        .map(it -> (OnDemandAgent) it)
        .collect(Collectors.toList());
  }

  @Override
  public boolean handles(final OnDemandType type, final String cloudProvider) {
    return getOnDemandAgents().stream().anyMatch(it -> it.handles(type, cloudProvider));
  }

  @Override
  public OnDemandCacheResult handle(
      final OnDemandType type, final String cloudProvider, Map<String, ?> data) {
    return handle(type, onDemandAgents(type, cloudProvider), data);
  }

  private OnDemandCacheResult handle(
      OnDemandType type, Collection<OnDemandAgent> onDemandAgents, Map<String, ?> data) {
    log.debug(
        "Calling handle on data: {}, onDemandAgents: {}, type: {}", data, onDemandAgents, type);

    boolean hasOnDemandResults = false;
    Map<String, List<String>> cachedIdentifiersByType = new HashMap<>();
    for (OnDemandAgent agent : onDemandAgents) {
      try {
        AgentLock lock = agentScheduler.tryLock((Agent) agent);
        if (agentScheduler.isAtomic() && lock == null) {
          // force Orca to retry
          hasOnDemandResults = true;
          continue;
        }

        final long startTime = System.nanoTime();
        final ProviderCache providerCache =
            catsModule.getProviderRegistry().getProviderCache(agent.getProviderName());
        if (agent.getMetricsSupport() != null) {
          agent.getMetricsSupport().countOnDemand();
        }

        final OnDemandAgent.OnDemandResult result = agent.handle(providerCache, data);
        if (result != null) {
          if (agentScheduler.isAtomic() && !agentScheduler.lockValid(lock)) {
            // force Orca to retry
            hasOnDemandResults = true;
            continue;
          }

          if (agent.getMetricsSupport() == null) {
            continue;
          }

          if (result.getCacheResult() != null) {
            final Map<String, Collection<CacheData>> results =
                result.getCacheResult().getCacheResults();
            if (agentHasOnDemandResults(results)) {
              hasOnDemandResults = true;
              results.forEach(
                  (k, v) -> {
                    if (v != null && !v.isEmpty()) {
                      if (!cachedIdentifiersByType.containsKey(k)) {
                        cachedIdentifiersByType.put(k, new ArrayList<>());
                      }
                      cachedIdentifiersByType
                          .get(k)
                          .addAll(v.stream().map(CacheData::getId).collect(Collectors.toList()));
                    }
                  });
            }

            agent
                .getMetricsSupport()
                .cacheWrite(
                    () -> {
                      if (result.cacheResult.isPartialResult()) {
                        providerCache.addCacheResult(
                            result.sourceAgentType, result.authoritativeTypes, result.cacheResult);
                      } else {
                        providerCache.putCacheResult(
                            result.sourceAgentType, result.authoritativeTypes, result.cacheResult);
                      }
                    });
          }

          if (result.getEvictions() != null && !result.getEvictions().isEmpty()) {
            agent
                .getMetricsSupport()
                .cacheEvict(
                    () -> {
                      result.evictions.forEach(providerCache::evictDeletedItems);
                    });
          }

          if (agentScheduler.isAtomic() && !(agentScheduler.tryRelease(lock))) {
            throw new IllegalStateException(
                "We likely just wrote stale data. If you're seeing this, file a github issue: https://github.com/spinnaker/spinnaker/issues");
          }

          final long elapsed = System.nanoTime() - startTime;
          agent.getMetricsSupport().recordTotalRunTimeNanos(elapsed);

          log.info(
              "{}/{} handled {} in {}ms. Payload: {}",
              agent.getProviderName(),
              agent.getOnDemandAgentType(),
              type,
              TimeUnit.NANOSECONDS.toMillis(elapsed),
              data);
        }

      } catch (Exception e) {
        if (agent.getMetricsSupport() != null) {
          agent.getMetricsSupport().countError();
        }
        log.warn(
            "{}/{} failed to handle on demand update for {}",
            agent.getProviderName(),
            agent.getOnDemandAgentType(),
            type,
            e);
      }
    }

    if (hasOnDemandResults) {
      return new OnDemandCacheResult(OnDemandCacheStatus.PENDING, cachedIdentifiersByType);
    }

    return new OnDemandCacheResult(OnDemandCacheStatus.SUCCESSFUL);
  }

  private boolean agentHasOnDemandResults(Map<String, Collection<CacheData>> results) {
    return !agentScheduler.isAtomic()
        && !(Optional.ofNullable(results).orElseGet(HashMap::new).values().stream()
                .mapToLong(Collection::size)
                .sum()
            == 0);
  }

  @Override
  public Collection<Map<String, Object>> pendingOnDemandRequests(
      final OnDemandType type, final String cloudProvider) {
    if (agentScheduler.isAtomic()) {
      return new ArrayList<>();
    }

    return onDemandAgentStream(type, cloudProvider)
        .flatMap(
            it -> {
              ProviderCache providerCache =
                  catsModule.getProviderRegistry().getProviderCache(it.getProviderName());
              return it.pendingOnDemandRequests(providerCache).stream();
            })
        .collect(Collectors.toList());
  }

  @Override
  public Map<String, Object> pendingOnDemandRequest(
      final OnDemandType type, final String cloudProvider, final String id) {
    if (agentScheduler.isAtomic()) {
      return null;
    }

    return onDemandAgentStream(type, cloudProvider)
        .map(
            it -> {
              ProviderCache providerCache =
                  catsModule.getProviderRegistry().getProviderCache(it.getProviderName());
              return it.pendingOnDemandRequest(providerCache, id);
            })
        .filter(Objects::nonNull)
        .findFirst()
        .orElse(null);
  }

  private Stream<OnDemandAgent> onDemandAgentStream(OnDemandType type, String cloudProvider) {
    return getOnDemandAgents().stream().filter(it -> it.handles(type, cloudProvider));
  }

  private Collection<OnDemandAgent> onDemandAgents(OnDemandType type, String cloudProvider) {
    return onDemandAgentStream(type, cloudProvider).collect(Collectors.toList());
  }
}
