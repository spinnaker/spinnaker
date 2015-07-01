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

import com.netflix.spectator.api.ExtendedRegistry
import com.netflix.spinnaker.cats.agent.AgentExecution
import com.netflix.spinnaker.cats.agent.AgentScheduler
import com.netflix.spinnaker.cats.agent.CachingAgent
import com.netflix.spinnaker.cats.agent.DefaultAgentScheduler
import com.netflix.spinnaker.cats.agent.ExecutionInstrumentation
import com.netflix.spinnaker.cats.cache.Cache
import com.netflix.spinnaker.cats.cache.NamedCacheFactory
import com.netflix.spinnaker.cats.mem.InMemoryNamedCacheFactory
import com.netflix.spinnaker.cats.module.CatsModule
import com.netflix.spinnaker.cats.provider.Provider
import com.netflix.spinnaker.clouddriver.search.SearchProvider
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import

import java.util.concurrent.TimeUnit

@Configuration
@Import([
  RedisCacheConfig
])
class CacheConfig {
  @Bean
  @ConditionalOnMissingBean(NamedCacheFactory)
  NamedCacheFactory namedCacheFactory() {
    new InMemoryNamedCacheFactory()
  }

  @Bean
  @ConditionalOnMissingBean(AgentScheduler)
  @ConditionalOnProperty(value = 'caching.writeEnabled', matchIfMissing = true)
  AgentScheduler agentScheduler() {
    new DefaultAgentScheduler(60, TimeUnit.SECONDS)
  }

  @Bean
  @ConditionalOnProperty(value = 'caching.writeEnabled', havingValue = 'false')
  @ConditionalOnMissingBean(AgentScheduler)
  AgentScheduler noopAgentScheduler() {
    new AgentScheduler() {
      @Override
      void schedule(CachingAgent agent, AgentExecution agentExecution, ExecutionInstrumentation executionInstrumentation) {
        //do nothing
      }
    }
  }

  @Bean
  @ConditionalOnMissingBean(SearchableProvider)
  SearchableProvider noopProvider() {
    new SearchableProvider() {
      @Override
      String getProviderName() {
        "noop"
      }

      @Override
      Collection<CachingAgent> getCachingAgents() {
        Collections.emptySet()
      }

      @Override
      Set<String> getDefaultCaches() {
        Collections.emptySet()
      }

      @Override
      Map<String, String> getUrlMappingTemplates() {
        Collections.emptyMap()
      }

      @Override
      Map<String, SearchableProvider.SearchResultHydrator> getSearchResultHydrators() {
        Collections.emptyMap()
      }

      @Override
      Map<String, SearchableProvider.IdentifierExtractor> getIdentifierExtractors() {
        Collections.emptyMap()
      }

      @Override
      Map<String, String> parseKey(String key) {
        null
      }
    }
  }

  @Bean
  @ConditionalOnMissingBean(CatsModule)
  CatsModule catsModule(List<Provider> providers, List<ExecutionInstrumentation> executionInstrumentation, NamedCacheFactory cacheFactory, AgentScheduler agentScheduler) {
    new CatsModule.Builder().cacheFactory(cacheFactory).scheduler(agentScheduler).instrumentation(executionInstrumentation).build(providers)
  }

  @Bean
  Cache cacheView(CatsModule catsModule) {
    catsModule.view
  }

  @Bean
  ExecutionInstrumentation loggingInstrumentation() {
    new LoggingInstrumentation()
  }

  @Bean
  ExecutionInstrumentation metricInstrumentation(ExtendedRegistry extendedRegistry) {
    new MetricInstrumentation(extendedRegistry)
  }

  @Bean
  OnDemandCacheUpdater catsOnDemandCacheUpdater(List<Provider> providers, CatsModule catsModule) {
    new CatsOnDemandCacheUpdater(providers, catsModule)
  }

  @Bean
  SearchProvider catsSearchProvider(Cache cacheView, List<SearchableProvider> providers) {
    new CatsSearchProvider(cacheView, providers)
  }

}
