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
import com.netflix.spinnaker.cats.agent.CachingAgent
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsRepository
import com.netflix.spinnaker.clouddriver.titus.TitanClientProvider
import com.netflix.spinnaker.clouddriver.titus.credentials.NetflixTitanCredentials
import com.netflix.spinnaker.clouddriver.titus.caching.agents.TitanClusterCachingAgent
import com.netflix.spinnaker.clouddriver.titus.caching.agents.TitanImageCachingAgent
import com.netflix.spinnaker.clouddriver.titus.caching.agents.TitanInstanceCachingAgent
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.DependsOn

@Configuration
class TitanCachingProviderConfig {

  @Bean
  @DependsOn('netflixTitanCredentials')
  TitanCachingProvider titanCachingProvider(AccountCredentialsRepository accountCredentialsRepository,
                                            TitanClientProvider titanClientProvider,
                                            ObjectMapper objectMapper) {
    List<CachingAgent> agents = []
    def allAccounts = accountCredentialsRepository.all.findAll { it instanceof NetflixTitanCredentials } as Collection<NetflixTitanCredentials>
    allAccounts.each { NetflixTitanCredentials account ->
      account.regions.each { region ->
        agents << new TitanClusterCachingAgent(titanClientProvider, account, region.name, objectMapper)
        agents << new TitanImageCachingAgent(titanClientProvider, account, region.name, objectMapper)
        agents << new TitanInstanceCachingAgent(titanClientProvider, account, region.name, objectMapper)
      }
    }
    new TitanCachingProvider(agents)
  }

  @Bean
  ObjectMapper objectMapper() {
    ObjectMapper objectMapper = new ObjectMapper()
    objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
    objectMapper.configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false)
    objectMapper
  }
}
