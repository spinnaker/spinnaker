/*
 * Copyright 2016 Google, Inc.
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

package com.netflix.spinnaker.clouddriver.kubernetes.provider.config

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spectator.api.Registry
import com.netflix.spinnaker.cats.agent.Agent
import com.netflix.spinnaker.cats.provider.ProviderSynchronizerTypeWrapper
import com.netflix.spinnaker.clouddriver.kubernetes.KubernetesCloudProvider
import com.netflix.spinnaker.clouddriver.kubernetes.deploy.KubernetesUtil
import com.netflix.spinnaker.clouddriver.kubernetes.provider.KubernetesProvider
import com.netflix.spinnaker.clouddriver.kubernetes.provider.agent.KubernetesServerGroupCachingAgent
import com.netflix.spinnaker.clouddriver.kubernetes.security.KubernetesNamedAccountCredentials
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsRepository
import com.netflix.spinnaker.clouddriver.security.ProviderUtils
import org.springframework.beans.factory.config.ConfigurableBeanFactory
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.DependsOn
import org.springframework.context.annotation.Scope

import java.util.concurrent.ConcurrentHashMap

@Configuration
class KubernetesProviderConfig {
  @Bean
  @DependsOn('kubernetesNamedAccountCredentials')
  KubernetesProvider kubernetesProvider(KubernetesCloudProvider kubernetesCloudProvider,
                                        AccountCredentialsRepository accountCredentialsRepository,
                                        ObjectMapper objectMapper,
                                        Registry registry) {
    def kubernetesProvider = new KubernetesProvider(kubernetesCloudProvider, Collections.newSetFromMap(new ConcurrentHashMap<Agent, Boolean>()))

    synchronizeKubernetesProvider(kubernetesProvider, kubernetesCloudProvider, accountCredentialsRepository, objectMapper, registry)

    kubernetesProvider
  }

  @Bean
  KubernetesProviderSynchronizerTypeWrapper kubernetesProviderSynchronizerTypeWrapper() {
    new KubernetesProviderSynchronizerTypeWrapper()
  }

  class KubernetesProviderSynchronizerTypeWrapper implements ProviderSynchronizerTypeWrapper {
    @Override
    Class getSynchronizerType() {
      return KubernetesProviderSynchronizer
    }
  }

  class KubernetesProviderSynchronizer {}

  @Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
  @Bean
  KubernetesProviderSynchronizer synchronizeKubernetesProvider(KubernetesProvider kubernetesProvider,
                                                               KubernetesCloudProvider kubernetesCloudProvider,
                                                               AccountCredentialsRepository accountCredentialsRepository,
                                                               ObjectMapper objectMapper,
                                                               Registry registry) {
    def scheduledAccounts = ProviderUtils.getScheduledAccounts(kubernetesProvider)
    def allAccounts = ProviderUtils.buildThreadSafeSetOfAccounts(accountCredentialsRepository, KubernetesNamedAccountCredentials)

    def util = new KubernetesUtil()
    allAccounts.each { KubernetesNamedAccountCredentials credentials ->
      if (!scheduledAccounts.contains(credentials.accountName)) {
        def newlyAddedAgents = []

        credentials.credentials.namespaces.forEach({ namespace ->
          newlyAddedAgents << new KubernetesServerGroupCachingAgent(kubernetesCloudProvider, credentials.accountName, credentials.credentials, namespace, objectMapper, registry, util)
        })

        // If there is an agent scheduler, then this provider has been through the AgentController in the past.
        // In that case, we need to do the scheduling here (because accounts have been added to a running system).
        if (kubernetesProvider.agentScheduler) {
          ProviderUtils.rescheduleAgents(kubernetesProvider, newlyAddedAgents)
        }

        kubernetesProvider.agents.addAll(newlyAddedAgents)
      }
    }

    new KubernetesProviderSynchronizer()
  }
}
