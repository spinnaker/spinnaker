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
import com.google.common.collect.ArrayListMultimap
import com.google.common.collect.Iterables
import com.google.common.collect.Multimap
import com.netflix.spectator.api.Registry
import com.netflix.spinnaker.cats.agent.Agent
import com.netflix.spinnaker.cats.provider.Provider
import com.netflix.spinnaker.clouddriver.dcos.DcosClientProvider
import com.netflix.spinnaker.clouddriver.dcos.DcosCloudProvider
import com.netflix.spinnaker.clouddriver.dcos.provider.DcosProvider
import com.netflix.spinnaker.clouddriver.dcos.provider.agent.DcosClusterAware
import com.netflix.spinnaker.clouddriver.dcos.provider.agent.DcosLoadBalancerCachingAgent
import com.netflix.spinnaker.clouddriver.dcos.provider.agent.DcosSecretsCachingAgent
import com.netflix.spinnaker.clouddriver.dcos.provider.agent.DcosServerGroupCachingAgent
import com.netflix.spinnaker.clouddriver.dcos.security.DcosAccountCredentials
import com.netflix.spinnaker.clouddriver.dcos.security.DcosClusterCredentials
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsProvider
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsRepository
import com.netflix.spinnaker.clouddriver.security.ProviderUtils
import groovy.util.logging.Slf4j
import org.apache.commons.lang3.tuple.Pair
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.DependsOn

import java.util.concurrent.ConcurrentHashMap

@Slf4j
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

  private static void synchronizeDcosProvider(DcosProvider dcosProvider,
                                              AccountCredentialsProvider accountCredentialsProvider,
                                              AccountCredentialsRepository accountCredentialsRepository,
                                              ObjectMapper objectMapper,
                                              Registry registry) {

    Set<Pair<String, String>> scheduledAgents = getScheduledClusterAgents(dcosProvider)

    Set<DcosAccountCredentials> allAccounts = ProviderUtils.buildThreadSafeSetOfAccounts(accountCredentialsRepository, DcosAccountCredentials)

    objectMapper.enable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)

    def newlyAddedAgents = []

    Multimap<Pair<String, String>, DcosAccountCredentials> accountsByCluster = ArrayListMultimap.create()

    allAccounts.each { DcosAccountCredentials account ->
      account.getCredentials().credentials.each { DcosClusterCredentials clusterCredentials ->
        accountsByCluster.put(Pair.of(clusterCredentials.cluster, clusterCredentials.dcosConfig.credentials.uid), account)
      }
    }

    for (Map.Entry<Pair<String, String>, Collection<DcosAccountCredentials>> entry : accountsByCluster.asMap().entrySet()) {
      if (!scheduledAgents.contains(entry.key)) {
        newlyAddedAgents << new DcosSecretsCachingAgent(entry.key.left, Iterables.getFirst(entry.value, null), new DcosClientProvider(accountCredentialsProvider), objectMapper)
        newlyAddedAgents << new DcosServerGroupCachingAgent(entry.value, entry.key.left, new DcosClientProvider(accountCredentialsProvider), objectMapper, registry)
        newlyAddedAgents << new DcosLoadBalancerCachingAgent(entry.value, entry.key.left, new DcosClientProvider(accountCredentialsProvider), objectMapper, registry)
      } else {
        synchronizeAgent(dcosProvider, entry.key, entry.value)
      }
    }

    if (!newlyAddedAgents.isEmpty()) {
      dcosProvider.agents.addAll(newlyAddedAgents)
    }
  }

  static def synchronizeAgent(DcosProvider dcosProvider, Pair<String, String> clusterKey, Collection<DcosAccountCredentials> allAccounts) {

    DcosClusterAware clusterAwareAgent = dcosProvider.agents.find { agent ->
      agent instanceof DcosClusterAware && ((DcosClusterAware) agent).clusterName == clusterKey.left && ((DcosClusterAware)agent).serviceAccountUID == clusterKey.right
    } as DcosClusterAware

    if (clusterAwareAgent) {
      def agentAccounts = clusterAwareAgent.accounts
      def oldAccountNames = agentAccounts.collect { it.name }
      def newAccountNames = allAccounts.collect { it.name }
      def accountNamesToDelete = oldAccountNames - newAccountNames
      def accountNamesToAdd = newAccountNames - oldAccountNames

      accountNamesToDelete.each { accountNameToDelete ->
        def accountToDelete = agentAccounts.find { it.name == accountNameToDelete }

        if (accountToDelete) {
          agentAccounts.remove(accountToDelete)
        }
      }

      accountNamesToAdd.each { accountNameToAdd ->
        def accountToAdd = allAccounts.find { it.name == accountNameToAdd }

        if (accountToAdd) {
          agentAccounts.add(accountToAdd)
        }
      }
    }
  }

  static Set<Pair<String, String>> getScheduledClusterAgents(Provider provider) {
    provider.agents.findAll { agent ->
      agent instanceof DcosClusterAware
    }.collect { agent ->
      def dcosAgent = agent as DcosClusterAware
      Pair.of(dcosAgent.clusterName, dcosAgent.serviceAccountUID)
    } as Set
  }
}
