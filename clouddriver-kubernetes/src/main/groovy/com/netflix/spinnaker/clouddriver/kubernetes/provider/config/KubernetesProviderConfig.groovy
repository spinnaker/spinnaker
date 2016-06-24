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
import com.netflix.spinnaker.cats.thread.NamedThreadFactory
import com.netflix.spinnaker.clouddriver.kubernetes.KubernetesCloudProvider
import com.netflix.spinnaker.clouddriver.kubernetes.provider.KubernetesProvider
import com.netflix.spinnaker.clouddriver.kubernetes.provider.agent.*
import com.netflix.spinnaker.clouddriver.kubernetes.security.KubernetesNamedAccountCredentials
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsRepository
import com.netflix.spinnaker.clouddriver.security.ProviderUtils
import org.springframework.beans.factory.config.ConfigurableBeanFactory
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.DependsOn
import org.springframework.context.annotation.Scope

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

@Configuration
class KubernetesProviderConfig implements Runnable {
  @Bean
  @DependsOn('kubernetesNamedAccountCredentials')
  KubernetesProvider kubernetesProvider(KubernetesCloudProvider kubernetesCloudProvider,
                                        AccountCredentialsRepository accountCredentialsRepository,
                                        ObjectMapper objectMapper,
                                        Registry registry) {
    this.kubernetesProvider = new KubernetesProvider(kubernetesCloudProvider, Collections.newSetFromMap(new ConcurrentHashMap<Agent, Boolean>()))
    this.kubernetesCloudProvider = kubernetesCloudProvider
    this.accountCredentialsRepository = accountCredentialsRepository
    this.objectMapper = objectMapper
    this.registry = registry

    ScheduledExecutorService poller = Executors.newSingleThreadScheduledExecutor(new NamedThreadFactory(KubernetesProviderConfig.class.getSimpleName()))

    poller.scheduleAtFixedRate(this, 0, 30, TimeUnit.SECONDS)

    kubernetesProvider
  }

  private KubernetesProvider kubernetesProvider
  private KubernetesCloudProvider kubernetesCloudProvider
  private AccountCredentialsRepository accountCredentialsRepository
  private ObjectMapper objectMapper
  private Registry registry

  @Bean
  KubernetesProviderSynchronizerTypeWrapper kubernetesProviderSynchronizerTypeWrapper() {
    new KubernetesProviderSynchronizerTypeWrapper()
  }

  @Override
  void run() {
    synchronizeKubernetesProvider(kubernetesProvider, kubernetesCloudProvider, accountCredentialsRepository, objectMapper, registry)
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
    def allAccounts = ProviderUtils.buildThreadSafeSetOfAccounts(accountCredentialsRepository, KubernetesNamedAccountCredentials)

    kubernetesProvider.agents.clear()

    allAccounts.each { KubernetesNamedAccountCredentials credentials ->
      def newlyAddedAgents = []

      credentials.getNamespaces().forEach({ namespace ->
        newlyAddedAgents << new KubernetesServerGroupCachingAgent(kubernetesCloudProvider, credentials.name, credentials.credentials, namespace, objectMapper, registry)
        newlyAddedAgents << new KubernetesLoadBalancerCachingAgent(kubernetesCloudProvider, credentials.name, credentials.credentials, namespace, objectMapper, registry)
        newlyAddedAgents << new KubernetesInstanceCachingAgent(kubernetesCloudProvider, credentials.name, credentials.credentials, namespace, objectMapper, registry)
        newlyAddedAgents << new KubernetesSecurityGroupCachingAgent(kubernetesCloudProvider, credentials.name, credentials.credentials, namespace, objectMapper, registry)
        newlyAddedAgents << new KubernetesJobCachingAgent(kubernetesCloudProvider, credentials.name, credentials.credentials, namespace, objectMapper, registry)
      })

      // If there is an agent scheduler, then this provider has been through the AgentController in the past.
      // In that case, we need to do the scheduling here (because accounts have been added to a running system).
      if (kubernetesProvider.agentScheduler) {
        ProviderUtils.rescheduleAgents(kubernetesProvider, newlyAddedAgents)
      }

      kubernetesProvider.agents.addAll(newlyAddedAgents)
    }

    new KubernetesProviderSynchronizer()
  }
}
