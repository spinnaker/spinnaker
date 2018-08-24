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

import com.netflix.spectator.api.Registry
import com.netflix.spinnaker.cats.agent.Agent
import com.netflix.spinnaker.cats.agent.AgentExecution
import com.netflix.spinnaker.cats.agent.AgentScheduler
import com.netflix.spinnaker.cats.agent.DefaultAgentScheduler
import com.netflix.spinnaker.cats.agent.ExecutionInstrumentation
import com.netflix.spinnaker.cats.cache.Cache
import com.netflix.spinnaker.cats.cache.NamedCacheFactory
import com.netflix.spinnaker.cats.mem.InMemoryNamedCacheFactory
import com.netflix.spinnaker.cats.module.CatsModule
import com.netflix.spinnaker.cats.provider.Provider
import com.netflix.spinnaker.cats.provider.ProviderRegistry
import com.netflix.spinnaker.clouddriver.search.SearchProvider
import com.netflix.spinnaker.fiat.shared.FiatPermissionEvaluator
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.Configuration

import java.util.concurrent.TimeUnit

@Configuration
@ComponentScan([
  'com.netflix.spinnaker.clouddriver.cache',
])
@EnableConfigurationProperties(CatsInMemorySearchProperties)
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
      void schedule(Agent agent, AgentExecution agentExecution, ExecutionInstrumentation executionInstrumentation) {
        //do nothing
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
  ProviderRegistry providerRegistry(CatsModule catsModule) {
    catsModule.providerRegistry
  }

  @Bean
  ExecutionInstrumentation loggingInstrumentation() {
    new LoggingInstrumentation()
  }

  @Bean
  ExecutionInstrumentation metricInstrumentation(Registry registry) {
    new MetricInstrumentation(registry)
  }

  @Bean
  OnDemandCacheUpdater catsOnDemandCacheUpdater(List<Provider> providers, CatsModule catsModule) {
    new CatsOnDemandCacheUpdater(providers, catsModule)
  }

  @Bean
  SearchProvider catsSearchProvider(CatsInMemorySearchProperties catsInMemorySearchProperties,
                                    Cache cacheView,
                                    List<SearchableProvider> providers,
                                    ProviderRegistry providerRegistry,
                                    Optional<FiatPermissionEvaluator> permissionEvaluator,
                                    Optional<List<KeyParser>> keyParsers) {
    new CatsSearchProvider(catsInMemorySearchProperties, cacheView, providers, providerRegistry, permissionEvaluator, keyParsers)
  }

  @Bean
  @ConditionalOnMissingBean(SearchableProvider)
  SearchableProvider noopSearchableProvider() {
    new NoopSearchableProvider()
  }
}
