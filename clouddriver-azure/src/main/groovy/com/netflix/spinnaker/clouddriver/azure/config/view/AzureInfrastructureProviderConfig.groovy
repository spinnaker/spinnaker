/*
 * Copyright 2015 The original authors.
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

package com.netflix.spinnaker.clouddriver.azure.config.view

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spectator.api.Registry
import com.netflix.spinnaker.cats.agent.CachingAgent
import com.netflix.spinnaker.cats.provider.ProviderSynchronizerTypeWrapper
import com.netflix.spinnaker.clouddriver.azure.AzureCloudProvider
import com.netflix.spinnaker.clouddriver.azure.security.AzureNamedAccountCredentials
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsRepository
import com.netflix.spinnaker.clouddriver.azure.common.AzureInfrastructureProvider
import org.springframework.beans.factory.config.ConfigurableBeanFactory
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.DependsOn
import org.springframework.context.annotation.Scope

@Configuration
class AzureInfrastructureProviderConfig {
  @Bean
  @DependsOn('azureNamedAccountCredentials')
  AzureInfrastructureProvider azureInfrastructureProvider(AzureCloudProvider azureCloudProvider,
                                                            AccountCredentialsRepository accountCredentialsRepository,
                                                            ObjectMapper objectMapper,
                                                            Registry registry) {
    List<CachingAgent> agents = []

    def allAccounts = accountCredentialsRepository.all.findAll {
      it instanceof AzureNamedAccountCredentials
    } as Collection<AzureNamedAccountCredentials>

    /* TODO: initialize Azure specific caching agents
    allAccounts.each { AzureNamedAccountCredentials credentials ->
      agents << new AzureSecurityGroupCachingAgent(azureCloudProvider, credentials.accountName, credentials.credentials, objectMapper, registry)
      agents << new AzureNetworkCachingAgent(azureCloudProvider, credentials.accountName, credentials.credentials, objectMapper)
    }
    */

    new AzureInfrastructureProvider(azureCloudProvider, agents)
  }

  @Bean
  AzureInfrastructureProviderSynchronizerTypeWrapper azureInfrastructureProviderSynchronizerTypeWrapper() {
    new AzureInfrastructureProviderSynchronizerTypeWrapper()
  }

  class AzureInfrastructureProviderSynchronizerTypeWrapper implements ProviderSynchronizerTypeWrapper {
    @Override
    Class getSynchronizerType() {
      return AzureInfrastructureProviderSynchronizer
    }
  }

  class AzureInfrastructureProviderSynchronizer {}

  @Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
  @Bean
  AzureInfrastructureProviderSynchronizer synchronizeAzureInfrastructureProvider(AzureInfrastructureProvider azureInfrastructureProvider,
                                                                             AzureCloudProvider azureCloudProvider,
                                                                             AccountCredentialsRepository accountCredentialsRepository,
                                                                             ObjectMapper objectMapper,
                                                                             Registry registry) {
  }
}

