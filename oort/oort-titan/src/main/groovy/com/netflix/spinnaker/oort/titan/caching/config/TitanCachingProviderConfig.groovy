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

package com.netflix.spinnaker.oort.titan.caching.config

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.amos.AccountCredentialsRepository
import com.netflix.spinnaker.cats.agent.CachingAgent
import com.netflix.spinnaker.oort.titan.TitanClientProvider
import com.netflix.spinnaker.oort.titan.caching.TitanCachingProvider
import com.netflix.spinnaker.oort.titan.caching.agents.ClusterCachingAgent
import com.netflix.spinnaker.oort.titan.caching.agents.ImageCachingAgent
import com.netflix.spinnaker.oort.titan.caching.agents.InstanceCachingAgent
import com.netflix.spinnaker.oort.titan.credentials.NetflixTitanCredentials
import com.netflix.spinnaker.oort.titan.credentials.config.CredentialsConfig
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * @author sthadeshwar
 */
@Configuration
class TitanCachingProviderConfig {

  @Bean
  TitanCachingProvider titanProvider(AccountCredentialsRepository accountCredentialsRepository, TitanClientProvider titanClientProvider, ObjectMapper objectMapper) {
    List<CachingAgent> agents = []
    def allAccounts = accountCredentialsRepository.all.findAll { it instanceof NetflixTitanCredentials } as Collection<NetflixTitanCredentials>
    allAccounts.each { NetflixTitanCredentials account ->
      for (CredentialsConfig.Region region : account.regions) {
        agents << new ClusterCachingAgent(titanClientProvider, account, region.name, objectMapper)
        agents << new ImageCachingAgent(titanClientProvider, account, region.name, objectMapper)
        agents << new InstanceCachingAgent(titanClientProvider, account, region.name, objectMapper)
      }
    }
    new TitanCachingProvider(agents)
  }

}
