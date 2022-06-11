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

import com.netflix.spectator.api.Registry;
import com.netflix.spinnaker.cats.agent.AgentLock;
import com.netflix.spinnaker.cats.agent.AgentScheduler;
import com.netflix.spinnaker.cats.agent.DefaultAgentScheduler;
import com.netflix.spinnaker.cats.agent.ExecutionInstrumentation;
import com.netflix.spinnaker.cats.cache.Cache;
import com.netflix.spinnaker.cats.cache.NamedCacheFactory;
import com.netflix.spinnaker.cats.mem.InMemoryNamedCacheFactory;
import com.netflix.spinnaker.cats.module.CatsModule;
import com.netflix.spinnaker.cats.provider.Provider;
import com.netflix.spinnaker.cats.provider.ProviderRegistry;
import com.netflix.spinnaker.clouddriver.search.SearchProvider;
import com.netflix.spinnaker.fiat.shared.FiatPermissionEvaluator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

@Configuration
@ComponentScan({
  "com.netflix.spinnaker.clouddriver.cache",
})
@EnableConfigurationProperties(CatsInMemorySearchProperties.class)
public class CacheConfig {
  @Bean
  @ConditionalOnMissingBean(NamedCacheFactory.class)
  NamedCacheFactory namedCacheFactory() {
    return new InMemoryNamedCacheFactory();
  }

  @Bean
  @ConditionalOnMissingBean(AgentScheduler.class)
  @ConditionalOnProperty(value = "caching.write-enabled", matchIfMissing = true)
  AgentScheduler agentScheduler() {
    return new DefaultAgentScheduler(60, TimeUnit.SECONDS);
  }

  @Bean
  @ConditionalOnProperty(value = "caching.write-enabled", havingValue = "false")
  @ConditionalOnMissingBean(AgentScheduler.class)
  AgentScheduler noopAgentScheduler() {
    return (agent, agentExecution, executionInstrumentation) -> {
      // do nothing
    };
  }

  @Bean
  @ConditionalOnMissingBean(CatsModule.class)
  CatsModule catsModule(
      List<Provider> providers,
      List<ExecutionInstrumentation> executionInstrumentation,
      NamedCacheFactory cacheFactory,
      AgentScheduler agentScheduler) {
    return new CatsModule.Builder()
        .cacheFactory(cacheFactory)
        .scheduler(agentScheduler)
        .instrumentation(executionInstrumentation)
        .build(providers);
  }

  @Bean
  Cache cacheView(CatsModule catsModule) {
    return catsModule.getView();
  }

  @Bean
  ProviderRegistry providerRegistry(CatsModule catsModule) {
    return catsModule.getProviderRegistry();
  }

  @Bean
  ExecutionInstrumentation loggingInstrumentation() {
    return new LoggingInstrumentation();
  }

  @Bean
  ExecutionInstrumentation metricInstrumentation(Registry registry) {
    return new MetricInstrumentation(registry);
  }

  @Bean
  OnDemandCacheUpdater catsOnDemandCacheUpdater(
      List<Provider> providers,
      CatsModule catsModule,
      AgentScheduler<? extends AgentLock> agentScheduler) {
    return new CatsOnDemandCacheUpdater(providers, catsModule, agentScheduler);
  }

  @Bean
  @ConditionalOnProperty(value = "caching.search.enabled", matchIfMissing = true)
  SearchProvider catsSearchProvider(
      CatsInMemorySearchProperties catsInMemorySearchProperties,
      Cache cacheView,
      List<SearchableProvider> providers,
      ProviderRegistry providerRegistry,
      Optional<FiatPermissionEvaluator> permissionEvaluator,
      Optional<List<KeyParser>> keyParsers) {
    return new CatsSearchProvider(
        catsInMemorySearchProperties,
        cacheView,
        providers,
        providerRegistry,
        permissionEvaluator,
        keyParsers);
  }

  @Bean
  @ConditionalOnMissingBean(SearchableProvider.class)
  SearchableProvider noopSearchableProvider() {
    return new NoopSearchableProvider();
  }
}
