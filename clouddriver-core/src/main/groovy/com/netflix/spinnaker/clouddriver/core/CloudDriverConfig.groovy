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

package com.netflix.spinnaker.clouddriver.core

import com.netflix.spinnaker.clouddriver.cache.CacheConfig
import com.netflix.spinnaker.clouddriver.cache.NoopOnDemandCacheUpdater
import com.netflix.spinnaker.clouddriver.cache.OnDemandCacheUpdater
import com.netflix.spinnaker.clouddriver.core.services.Front50Service
import com.netflix.spinnaker.clouddriver.search.ApplicationSearchProvider
import com.netflix.spinnaker.clouddriver.search.NoopSearchProvider
import com.netflix.spinnaker.clouddriver.search.ProjectSearchProvider
import com.netflix.spinnaker.clouddriver.search.SearchProvider
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsProvider
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsRepository
import com.netflix.spinnaker.clouddriver.security.DefaultAccountCredentialsProvider
import com.netflix.spinnaker.clouddriver.security.MapBackedAccountCredentialsRepository
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import
import org.springframework.core.convert.ConversionService
import org.springframework.core.convert.support.DefaultConversionService
import org.springframework.web.client.RestTemplate

@Configuration
@Import([
  RedisConfig,
  CacheConfig
])
class CloudDriverConfig {
  @Bean
  @ConditionalOnMissingBean(AccountCredentialsRepository)
  AccountCredentialsRepository accountCredentialsRepository() {
    new MapBackedAccountCredentialsRepository()
  }

  @Bean
  @ConditionalOnMissingBean(AccountCredentialsProvider)
  AccountCredentialsProvider accountCredentialsProvider(AccountCredentialsRepository accountCredentialsRepository) {
    new DefaultAccountCredentialsProvider(accountCredentialsRepository)
  }

  @Bean
  RestTemplate restTemplate() {
    new RestTemplate()
  }

  @Bean
  @ConditionalOnMissingBean(OnDemandCacheUpdater)
  NoopOnDemandCacheUpdater noopOnDemandCacheUpdater() {
    new NoopOnDemandCacheUpdater()
  }

  @Bean
  @ConditionalOnMissingBean(SearchProvider)
  NoopSearchProvider noopSearchProvider() {
    new NoopSearchProvider()
  }

  @Bean
  @ConditionalOnExpression('${services.front50.enabled:true}')
  ApplicationSearchProvider applicationSearchProvider(Front50Service front50Service) {
    new ApplicationSearchProvider(front50Service)
  }

  @Bean
  @ConditionalOnExpression('${services.front50.enabled:true}')
  ProjectSearchProvider projectSearchProvider(Front50Service front50Service) {
    new ProjectSearchProvider(front50Service)
  }

  @Bean
  @ConditionalOnMissingBean(CloudProvider)
  CloudProvider noopCloudProvider() {
    new NoopCloudProvider()
  }

  // Allows @Value annotation to tokenize a list of strings.
  @Bean
  ConversionService conversionService() {
    new DefaultConversionService()
  }
}
