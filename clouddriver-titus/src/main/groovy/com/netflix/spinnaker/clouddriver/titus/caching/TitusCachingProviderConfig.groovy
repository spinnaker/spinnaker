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

package com.netflix.spinnaker.clouddriver.titus.caching
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.netflix.spectator.api.Registry
import com.netflix.spinnaker.cats.agent.CachingAgent
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsRepository
import com.netflix.spinnaker.clouddriver.titus.TitusClientProvider
import com.netflix.spinnaker.clouddriver.titus.TitusCloudProvider
import com.netflix.spinnaker.clouddriver.titus.credentials.NetflixTitusCredentials
import com.netflix.spinnaker.clouddriver.titus.caching.agents.TitusClusterCachingAgent
import com.netflix.spinnaker.clouddriver.titus.caching.agents.TitusImageCachingAgent
import com.netflix.spinnaker.clouddriver.titus.caching.agents.TitusInstanceCachingAgent
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.DependsOn

@Configuration
class TitusCachingProviderConfig {

  @Bean
  @DependsOn('netflixTitusCredentials')
  TitusCachingProvider titusCachingProvider(TitusCloudProvider titusCloudProvider,
                                            Registry registry,
                                            AccountCredentialsRepository accountCredentialsRepository,
                                            TitusClientProvider titusClientProvider,
                                            ObjectMapper objectMapper) {
    List<CachingAgent> agents = []
    def allAccounts = accountCredentialsRepository.all.findAll { it instanceof NetflixTitusCredentials } as Collection<NetflixTitusCredentials>
    allAccounts.each { NetflixTitusCredentials account ->
      account.regions.each { region ->
        agents << new TitusClusterCachingAgent(titusCloudProvider, titusClientProvider, account, region.name, objectMapper, registry)
        agents << new TitusImageCachingAgent(titusClientProvider, account, region.name, objectMapper)
        agents << new TitusInstanceCachingAgent(titusClientProvider, account, region.name, objectMapper)
      }
    }
    new TitusCachingProvider(agents)
  }

  @Bean
  ObjectMapper objectMapper() {
    ObjectMapper objectMapper = new ObjectMapper()
    objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
    objectMapper.configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false)
    objectMapper
  }
}
