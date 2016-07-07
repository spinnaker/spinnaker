/*
 * Copyright 2016 Pivotal, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.clouddriver.cf.provider.config
import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spectator.api.Registry
import com.netflix.spinnaker.cats.agent.Agent
import com.netflix.spinnaker.cats.agent.CachingAgent
import com.netflix.spinnaker.cats.provider.ProviderSynchronizerTypeWrapper
import com.netflix.spinnaker.clouddriver.cf.provider.CloudFoundryProvider
import com.netflix.spinnaker.clouddriver.cf.provider.agent.ClusterCachingAgent
import com.netflix.spinnaker.clouddriver.cf.security.CloudFoundryAccountCredentials
import com.netflix.spinnaker.clouddriver.cf.utils.CloudFoundryClientFactory
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsRepository
import com.netflix.spinnaker.clouddriver.security.ProviderUtils
import org.springframework.beans.factory.config.ConfigurableBeanFactory
import org.springframework.context.ApplicationContext
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.DependsOn
import org.springframework.context.annotation.Scope

import java.util.concurrent.ConcurrentHashMap

@Configuration
class CloudFoundryProviderConfig {

  @Bean
  @DependsOn('cloudFoundryAccountCredentials')
  CloudFoundryProvider cloudFoundryProvider(AccountCredentialsRepository accountCredentialsRepository,
                                            CloudFoundryClientFactory cloudFoundryClientFactory,
                                            ObjectMapper objectMapper,
                                            ApplicationContext ctx,
                                            Registry registry) {
    def cloudFoundryProvider =
        new CloudFoundryProvider(Collections.newSetFromMap(new ConcurrentHashMap<Agent, Boolean>()))

    synchronizeCloudFoundryProvider(
        cloudFoundryProvider,
        accountCredentialsRepository,
        cloudFoundryClientFactory,
        objectMapper,
        ctx,
        registry)

    cloudFoundryProvider
  }

  @Bean
  CloudFoundrySynchronizerTypeWrapper cloudFoundrySynchronizerTypeWrapper() {
    new CloudFoundrySynchronizerTypeWrapper()
  }

  class CloudFoundrySynchronizerTypeWrapper implements ProviderSynchronizerTypeWrapper {
    @Override
    Class getSynchronizerType() {
      CloudFoundryProviderSynchronizer
    }
  }

  class CloudFoundryProviderSynchronizer {}

  @Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
  @Bean
  CloudFoundryProviderSynchronizer synchronizeCloudFoundryProvider(CloudFoundryProvider cloudFoundryProvider,
                                                                   AccountCredentialsRepository accountCredentialsRepository,
                                                                   CloudFoundryClientFactory cloudFoundryClientFactory,
                                                                   ObjectMapper objectMapper,
                                                                   ApplicationContext ctx,
                                                                   Registry registry) {
    def scheduledAccounts = ProviderUtils.getScheduledAccounts(cloudFoundryProvider)
    def allAccounts = ProviderUtils.buildThreadSafeSetOfAccounts(accountCredentialsRepository, CloudFoundryAccountCredentials)

    List<CachingAgent> newlyAddedAgents = []

    allAccounts.each { CloudFoundryAccountCredentials credentials ->
      if (!scheduledAccounts.contains(credentials.name)) {
        newlyAddedAgents << new ClusterCachingAgent(cloudFoundryClientFactory, credentials, objectMapper, registry)
      }
    }

    cloudFoundryProvider.agents.addAll(newlyAddedAgents)

    new CloudFoundryProviderSynchronizer()
  }

}
