/*
 * Copyright 2017 Cerner Corporation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package com.netflix.spinnaker.clouddriver.dcos.provider.config

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.netflix.spectator.api.Registry
import com.netflix.spinnaker.cats.agent.Agent
import com.netflix.spinnaker.cats.provider.ProviderSynchronizerTypeWrapper
import com.netflix.spinnaker.clouddriver.dcos.DcosCloudProvider
import com.netflix.spinnaker.clouddriver.dcos.provider.DcosProvider
import com.netflix.spinnaker.clouddriver.dcos.security.DcosAccountCredentials
import com.netflix.spinnaker.clouddriver.dcos.security.DcosClusterCredentials
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsProvider
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsRepository
import com.netflix.spinnaker.clouddriver.security.ProviderUtils
import org.springframework.beans.factory.config.ConfigurableBeanFactory
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.DependsOn
import org.springframework.context.annotation.Scope

import java.util.concurrent.ConcurrentHashMap

@Configuration
class DcosProviderConfig {
  @Bean
  @DependsOn('dcosCredentials')
  DcosProvider dcosProvider(DcosCloudProvider dcosCloudProvider,
                            AccountCredentialsProvider accountCredentialsProvider,
                            AccountCredentialsRepository accountCredentialsRepository,
                            ObjectMapper objectMapper,
                            Registry registry) {

    def provider = new DcosProvider(dcosCloudProvider, Collections.newSetFromMap(new ConcurrentHashMap<Agent, Boolean>()))
    synchronizeDcosProvider(provider, accountCredentialsProvider, accountCredentialsRepository, objectMapper, registry)
    provider
  }

  @Bean
  DcosProviderSynchronizerTypeWrapper dcosProviderSynchronizerTypeWrapper() {
    new DcosProviderSynchronizerTypeWrapper()
  }

  class DcosProviderSynchronizerTypeWrapper implements ProviderSynchronizerTypeWrapper {

    @Override
    Class getSynchronizerType() {
      return DcosProviderSynchronizer
    }
  }

  class DcosProviderSynchronizer {}

  @Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
  @Bean
  DcosProviderSynchronizer synchronizeDcosProvider(DcosProvider dcosProvider,
                                                   AccountCredentialsProvider accountCredentialsProvider,
                                                   AccountCredentialsRepository accountCredentialsRepository,
                                                   ObjectMapper objectMapper,
                                                   Registry registry) {

    def accounts = ProviderUtils.getScheduledAccounts(dcosProvider)
    def allAccounts = ProviderUtils.buildThreadSafeSetOfAccounts(accountCredentialsRepository, DcosAccountCredentials)

    objectMapper.enable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)

    def newlyAddedAgents = []
    allAccounts.each { DcosAccountCredentials credentials ->
      if (!accounts.contains(credentials.account)) {

        def allClusterCredentials = credentials.getCredentials().credentials

        allClusterCredentials.each { DcosClusterCredentials clusterCredentials ->
          // TODO We should try and enhance this at some point if DC/OS doesn't ever finer grained access controls on
          // secrets so that we aren't unnecessarily duplicating these agents since they are essentially storing the
          // exact same secrets for a single cluster
          // TODO(willgorman): restore in future PRs
//          newlyAddedAgents << new DcosSecretsCachingAgent(credentials.account, clusterCredentials.cluster,
//                  credentials, new DcosClientProvider(accountCredentialsProvider), objectMapper)
//
//          newlyAddedAgents << new DcosServerGroupCachingAgent(credentials.account, clusterCredentials.cluster,
//                  credentials, new DcosClientProvider(accountCredentialsProvider), objectMapper, registry)
//
//          newlyAddedAgents << new DcosLoadBalancerCachingAgent(credentials.account, clusterCredentials.cluster,
//                  credentials, new DcosClientProvider(accountCredentialsProvider), objectMapper, registry)
//
//          newlyAddedAgents << new DcosInstanceCachingAgent(credentials.account, clusterCredentials.cluster,
//                  credentials, new DcosClientProvider(accountCredentialsProvider), objectMapper)
        }
      }
    }

    if (!newlyAddedAgents.isEmpty()) {
      dcosProvider.agents.addAll(newlyAddedAgents)
    }

    new DcosProviderSynchronizer()
  }
}
