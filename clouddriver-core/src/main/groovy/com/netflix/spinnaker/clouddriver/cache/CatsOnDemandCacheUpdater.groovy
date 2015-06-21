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
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

@Component
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
      it.cachingAgents.findAll { it instanceof OnDemandAgent } as Collection<OnDemandAgent>
    }.flatten()
  }

  @Override
  boolean handles(String type) {
    onDemandAgents.any { it.handles(type) }
  }

  @Override
  void handle(String type, Map<String, ? extends Object> data) {
    Collection<OnDemandAgent> handleAgents = onDemandAgents.findAll { it.handles(type) }
    for (OnDemandAgent agent : handleAgents) {
      def providerCache = catsModule.getProviderRegistry().getProviderCache(awsProvider.providerName)
      OnDemandAgent.OnDemandResult result = agent.handle(providerCache, data)
      if (result) {
        if (result.evictions) {
          result.evictions.each { String evictType, Collection<String> ids ->
            providerCache.evictDeletedItems(evictType, ids)
          }
        }
        if (result.cacheResult) {
          providerCache.putCacheResult(result.sourceAgentType, result.authoritativeTypes, result.cacheResult)
        }
      }
    }
  }
}
