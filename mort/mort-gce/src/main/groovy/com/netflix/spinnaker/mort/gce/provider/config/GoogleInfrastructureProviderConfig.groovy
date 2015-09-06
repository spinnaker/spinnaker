/*
 * Copyright 2015 Google, Inc.
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

package com.netflix.spinnaker.mort.gce.provider.config

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spectator.api.Registry
import com.netflix.spinnaker.amos.AccountCredentialsRepository
import com.netflix.spinnaker.amos.gce.GoogleNamedAccountCredentials
import com.netflix.spinnaker.cats.agent.CachingAgent
import com.netflix.spinnaker.clouddriver.google.GoogleCloudProvider
import com.netflix.spinnaker.mort.gce.provider.GoogleInfrastructureProvider
import com.netflix.spinnaker.mort.gce.provider.agent.GoogleSecurityGroupCachingAgent
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.DependsOn

@Configuration
class GoogleInfrastructureProviderConfig {
  @Bean
  @DependsOn('googleNamedAccountCredentials')
  GoogleInfrastructureProvider googleInfrastructureProvider(GoogleCloudProvider googleCloudProvider,
                                                            AccountCredentialsRepository accountCredentialsRepository,
                                                            ObjectMapper objectMapper,
                                                            Registry registry) {
    List<CachingAgent> agents = []

    def allAccounts = accountCredentialsRepository.all.findAll {
      it instanceof GoogleNamedAccountCredentials
    } as Collection<GoogleNamedAccountCredentials>

    allAccounts.each { GoogleNamedAccountCredentials credentials ->
      agents << new GoogleSecurityGroupCachingAgent(googleCloudProvider, credentials.accountName, credentials.credentials, objectMapper, registry)
    }

    new GoogleInfrastructureProvider(googleCloudProvider, agents)
  }
}
