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

package com.netflix.spinnaker.clouddriver.cache

import com.netflix.spinnaker.cats.module.CatsModule
import com.netflix.spinnaker.cats.provider.Provider
import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

import java.util.concurrent.TimeUnit

@Component
@Slf4j
class CatsOnDemandCacheUpdater implements OnDemandCacheUpdater {

  private final List<Provider> providers
  private final CatsModule catsModule

  @Autowired
  public CatsOnDemandCacheUpdater(List<Provider> providers, CatsModule catsModule) {
    this.providers = providers
    this.catsModule = catsModule
  }

  private Collection<OnDemandAgent> getOnDemandAgents() {
    providers.collect {
      it.agents.findAll { it instanceof OnDemandAgent } as Collection<OnDemandAgent>
    }.flatten()
  }

  @Override
  boolean handles(String type) {
    onDemandAgents.any { it.handles(type) }
  }

  @Override
  boolean handles(String type, String cloudProvider) {
    onDemandAgents.any { it.handles(type, cloudProvider) }
  }

  @Override
  void handle(String type, Map<String, ? extends Object> data) {
    Collection<OnDemandAgent> onDemandAgents = onDemandAgents.findAll { it.handles(type) }
    handle(type, onDemandAgents, data)
  }

  @Override
  void handle(String type, String cloudProvider, Map<String, ? extends Object> data) {
    Collection<OnDemandAgent> onDemandAgents = onDemandAgents.findAll { it.handles(type, cloudProvider) }
    handle(type, onDemandAgents, data)
  }

  void handle(String type, Collection<OnDemandAgent> onDemandAgents, Map<String, ? extends Object> data) {
    for (OnDemandAgent agent : onDemandAgents) {
      try {
        final long startTime = System.nanoTime()
        def providerCache = catsModule.getProviderRegistry().getProviderCache(agent.providerName)
        OnDemandAgent.OnDemandResult result = agent.handle(providerCache, data)
        if (result) {
          if (result.cacheResult) {
            agent.metricsSupport.cacheWrite {
              providerCache.putCacheResult(result.sourceAgentType, result.authoritativeTypes, result.cacheResult)
            }
          }
          if (result.evictions) {
            agent.metricsSupport.cacheEvict {
              result.evictions.each { String evictType, Collection<String> ids ->
                providerCache.evictDeletedItems(evictType, ids)
              }
            }
          }
          final long elapsed = System.nanoTime() - startTime
          agent.metricsSupport.recordTotalRunTimeNanos(elapsed)
          log.info("$agent.providerName/$agent.onDemandAgentType handled $type in ${TimeUnit.NANOSECONDS.toMillis(elapsed)} millis. Payload: $data")
        }
      } catch (e) {
        agent.metricsSupport.countError()
        log.warn("$agent.providerName/$agent.onDemandAgentType failed to handle on demand update for $type", e)
      }
    }
  }

  @Override
  Collection<Map> pendingOnDemandRequests(String type, String cloudProvider) {
    Collection<OnDemandAgent> onDemandAgents = onDemandAgents.findAll { it.handles(type, cloudProvider) }
    return onDemandAgents.collect {
      def providerCache = catsModule.getProviderRegistry().getProviderCache(it.providerName)
      it.pendingOnDemandRequests(providerCache)
    }.flatten()
  }
}
