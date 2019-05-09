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


import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spectator.api.Registry
import com.netflix.spinnaker.cats.agent.CachingAgent
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsRepository
import com.netflix.spinnaker.clouddriver.titus.TitusClientProvider
import com.netflix.spinnaker.clouddriver.titus.TitusCloudProvider
import com.netflix.spinnaker.clouddriver.titus.caching.agents.TitusInstanceCachingAgent
import com.netflix.spinnaker.clouddriver.titus.caching.agents.TitusStreamingUpdateAgent
import com.netflix.spinnaker.clouddriver.titus.caching.agents.TitusV2ClusterCachingAgent
import com.netflix.spinnaker.clouddriver.titus.caching.utils.AwsLookupUtil
import com.netflix.spinnaker.clouddriver.titus.caching.utils.CachingSchemaUtil
import com.netflix.spinnaker.clouddriver.titus.credentials.NetflixTitusCredentials
import com.netflix.spinnaker.kork.dynamicconfig.DynamicConfigService
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.DependsOn

import javax.inject.Provider

@Configuration
class TitusCachingProviderConfig {

  @Value('${titus.poll-interval-millis:30000}')
  Long pollIntervalMillis

  @Value('${titus.timeout-millis:300000}')
  Long timeoutMillis

  @Bean
  @DependsOn('netflixTitusCredentials')
  TitusCachingProvider titusCachingProvider(AccountCredentialsRepository accountCredentialsRepository,
                                            TitusCloudProvider titusCloudProvider,
                                            TitusClientProvider titusClientProvider,
                                            ObjectMapper objectMapper,
                                            Registry registry,
                                            Provider<AwsLookupUtil> awsLookupUtilProvider,
                                            Provider<CachingSchemaUtil> cachingSchemaUtilProvider,
                                            DynamicConfigService dynamicConfigService) {
    List<CachingAgent> agents = []
    def allAccounts = accountCredentialsRepository.all.findAll {
      it instanceof NetflixTitusCredentials
    } as Collection<NetflixTitusCredentials>
    allAccounts.each { NetflixTitusCredentials account ->
      account.regions.each { region ->
        if (region.featureFlags.contains("streaming")) {
          agents << new TitusStreamingUpdateAgent(
            titusClientProvider,
            account,
            region,
            objectMapper,
            registry,
            awsLookupUtilProvider,
            dynamicConfigService
          )
        } else { //use new split caching for this whole account
          agents << new TitusInstanceCachingAgent(
            titusClientProvider,
            account,
            region,
            objectMapper,
            registry,
            awsLookupUtilProvider
          )
          agents << new TitusV2ClusterCachingAgent(
            titusClientProvider,
            account,
            region,
            objectMapper,
            registry,
            awsLookupUtilProvider,
            pollIntervalMillis,
            timeoutMillis
          )
        }
      }
    }
    new TitusCachingProvider(agents, cachingSchemaUtilProvider)
  }
}
