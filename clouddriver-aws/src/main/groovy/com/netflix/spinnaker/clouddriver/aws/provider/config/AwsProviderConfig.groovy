/*
 * Copyright 2014 Netflix, Inc.
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

package com.netflix.spinnaker.clouddriver.aws.provider.config

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spectator.api.Registry
import com.netflix.spinnaker.cats.agent.Agent
import com.netflix.spinnaker.cats.agent.CachingAgent
import com.netflix.spinnaker.cats.provider.ProviderSynchronizerTypeWrapper
import com.netflix.spinnaker.clouddriver.aws.AmazonCloudProvider
import com.netflix.spinnaker.clouddriver.aws.security.AmazonClientProvider
import com.netflix.spinnaker.clouddriver.aws.security.AmazonCredentials
import com.netflix.spinnaker.clouddriver.aws.security.NetflixAmazonCredentials
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsRepository
import com.netflix.spinnaker.clouddriver.security.ProviderUtils
import com.netflix.spinnaker.clouddriver.aws.discovery.DiscoveryApiFactory
import com.netflix.spinnaker.clouddriver.aws.edda.EddaApiFactory
import com.netflix.spinnaker.clouddriver.aws.provider.AwsProvider
import com.netflix.spinnaker.clouddriver.aws.provider.agent.AmazonLoadBalancerInstanceStateCachingAgent
import com.netflix.spinnaker.clouddriver.aws.provider.agent.ClusterCachingAgent
import com.netflix.spinnaker.clouddriver.aws.provider.agent.DiscoveryCachingAgent
import com.netflix.spinnaker.clouddriver.aws.provider.agent.EddaLoadBalancerCachingAgent
import com.netflix.spinnaker.clouddriver.aws.provider.agent.ImageCachingAgent
import com.netflix.spinnaker.clouddriver.aws.provider.agent.InstanceCachingAgent
import com.netflix.spinnaker.clouddriver.aws.provider.agent.LaunchConfigCachingAgent
import com.netflix.spinnaker.clouddriver.aws.provider.agent.LoadBalancerCachingAgent
import com.netflix.spinnaker.clouddriver.aws.provider.agent.ReservationReportCachingAgent
import org.springframework.beans.factory.config.ConfigurableBeanFactory
import org.springframework.context.ApplicationContext
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.DependsOn
import org.springframework.context.annotation.Scope

import java.util.concurrent.ConcurrentHashMap
import java.util.regex.Pattern

@Configuration
class AwsProviderConfig {
  @Bean
  @DependsOn('netflixAmazonCredentials')
  AwsProvider awsProvider(AmazonCloudProvider amazonCloudProvider,
                          AmazonClientProvider amazonClientProvider,
                          AccountCredentialsRepository accountCredentialsRepository,
                          ObjectMapper objectMapper,
                          DiscoveryApiFactory discoveryApiFactory,
                          EddaApiFactory eddaApiFactory,
                          ApplicationContext ctx,
                          Registry registry) {
    def awsProvider =
      new AwsProvider(accountCredentialsRepository, Collections.newSetFromMap(new ConcurrentHashMap<Agent, Boolean>()))

    synchronizeAwsProvider(awsProvider,
                           amazonCloudProvider,
                           amazonClientProvider,
                           accountCredentialsRepository,
                           objectMapper,
                           discoveryApiFactory,
                           eddaApiFactory,
                           ctx,
                           registry)

    awsProvider
  }

  @Bean
  AwsProviderSynchronizerTypeWrapper awsProviderSynchronizerTypeWrapper() {
    new AwsProviderSynchronizerTypeWrapper()
  }

  class AwsProviderSynchronizerTypeWrapper implements ProviderSynchronizerTypeWrapper {
    @Override
    Class getSynchronizerType() {
      return AwsProviderSynchronizer
    }
  }

  class AwsProviderSynchronizer {}

  @Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
  @Bean
  AwsProviderSynchronizer synchronizeAwsProvider(AwsProvider awsProvider,
                                                 AmazonCloudProvider amazonCloudProvider,
                                                 AmazonClientProvider amazonClientProvider,
                                                 AccountCredentialsRepository accountCredentialsRepository,
                                                 ObjectMapper objectMapper,
                                                 DiscoveryApiFactory discoveryApiFactory,
                                                 EddaApiFactory eddaApiFactory,
                                                 ApplicationContext ctx,
                                                 Registry registry) {
    def scheduledAccounts = ProviderUtils.getScheduledAccounts(awsProvider)
    def allAccounts = ProviderUtils.buildThreadSafeSetOfAccounts(accountCredentialsRepository, NetflixAmazonCredentials)

    Map<String, Map<String, Set<NetflixAmazonCredentials>>> discoveryAccounts = [:].withDefault { [:].withDefault { [] as Set } }
    List<CachingAgent> newlyAddedAgents = []

    allAccounts.each { NetflixAmazonCredentials credentials ->
      for (AmazonCredentials.AWSRegion region : credentials.regions) {
        if (!scheduledAccounts.contains(credentials.name)) {
          newlyAddedAgents << new ClusterCachingAgent(amazonCloudProvider, amazonClientProvider, credentials, region.name, objectMapper, registry)
          newlyAddedAgents << new LaunchConfigCachingAgent(amazonClientProvider, credentials, region.name, objectMapper, registry)
          newlyAddedAgents << new ImageCachingAgent(amazonClientProvider, credentials, region.name, objectMapper, registry)
          newlyAddedAgents << new InstanceCachingAgent(amazonClientProvider, credentials, region.name, objectMapper, registry)
          newlyAddedAgents << new LoadBalancerCachingAgent(amazonCloudProvider, amazonClientProvider, credentials, region.name, objectMapper, registry)
          if (credentials.eddaEnabled) {
            newlyAddedAgents << new EddaLoadBalancerCachingAgent(eddaApiFactory.createApi(credentials.edda, region.name), credentials, region.name, objectMapper)
          } else {
            def agent = new AmazonLoadBalancerInstanceStateCachingAgent(amazonClientProvider, credentials, region.name, objectMapper)
            ctx.autowireCapableBeanFactory.autowireBean(agent)
            newlyAddedAgents << agent
          }
        }

        if (credentials.discoveryEnabled) {
          discoveryAccounts[credentials.discovery][region.name].add(credentials)
        }
      }
    }

    // If there is an agent scheduler, then this provider has been through the AgentController in the past.
    if (awsProvider.agentScheduler) {
      synchronizeReservationReportCachingAgentAccounts(awsProvider, allAccounts)
      synchronizeDiscoveryCachingAgentsAccounts(allAccounts,
                                                awsProvider,
                                                discoveryAccounts,
                                                newlyAddedAgents,
                                                discoveryApiFactory,
                                                objectMapper)
    } else {
      // This caching agent runs across all accounts in one iteration (to maintain consistency).
      newlyAddedAgents << new ReservationReportCachingAgent(amazonClientProvider, allAccounts, objectMapper)

      discoveryAccounts.each { disco, actMap ->
        actMap.each { region, accounts ->
          newlyAddedAgents << new DiscoveryCachingAgent(discoveryApiFactory.createApi(disco, region), accounts, region, objectMapper)
        }
      }
    }

    awsProvider.agents.addAll(newlyAddedAgents)
    awsProvider.synchronizeHealthAgents()

    new AwsProviderSynchronizer()
  }

  private void synchronizeReservationReportCachingAgentAccounts(AwsProvider awsProvider, Collection<NetflixAmazonCredentials> allAccounts) {
    ReservationReportCachingAgent reservationReportCachingAgent = awsProvider.agents.find { agent ->
      agent instanceof ReservationReportCachingAgent
    }

    if (reservationReportCachingAgent) {
      def reservationReportAccounts = reservationReportCachingAgent.accounts
      def oldAccountNames = reservationReportAccounts.collect { it.name }
      def newAccountNames = allAccounts.collect { it.name }
      def accountNamesToDelete = oldAccountNames - newAccountNames
      def accountNamesToAdd = newAccountNames - oldAccountNames

      accountNamesToDelete.each { accountNameToDelete ->
        def accountToDelete = reservationReportAccounts.find { it.name == accountNameToDelete }

        if (accountToDelete) {
          reservationReportAccounts.remove(accountToDelete)
        }
      }

      accountNamesToAdd.each { accountNameToAdd ->
        def accountToAdd = allAccounts.find { it.name == accountNameToAdd }

        if (accountToAdd) {
          reservationReportAccounts.add(accountToAdd)
        }
      }
    }
  }

  private void synchronizeDiscoveryCachingAgentsAccounts(Collection<NetflixAmazonCredentials> allAccounts,
                                                         AwsProvider awsProvider,
                                                         Map<String, Map<String, Set<NetflixAmazonCredentials>>> discoveryAccounts,
                                                         List<CachingAgent> newlyAddedAgents,
                                                         discoveryApiFactory,
                                                         ObjectMapper objectMapper) {
    // First, delete all non-specified accounts across all discovery caching agents.
    def allNewAccountNames = allAccounts.collect { it.name }

    awsProvider.agents.findAll { agent ->
      agent instanceof DiscoveryCachingAgent
    }.each { DiscoveryCachingAgent discoveryCachingAgent ->
      def accountsToDelete = [] as Set

      discoveryCachingAgent.accounts.each { account ->
        if (!allNewAccountNames.contains(account.name)) {
          accountsToDelete << account
        }
      }

      discoveryCachingAgent.accounts.removeAll(accountsToDelete)
    }

    // Next, add any specified account which is not already registered with a discovery caching agent.
    discoveryAccounts.each { disco, actMap ->
      actMap.each { region, accounts ->
        def discoveryEndpoint = disco.replaceAll(Pattern.quote('{{region}}'), region)

        DiscoveryCachingAgent discoveryCachingAgent = awsProvider.agents.find { agent ->
          agent instanceof DiscoveryCachingAgent && agent.agentType.startsWith(discoveryEndpoint)
        }

        if (discoveryCachingAgent) {
          // If the agent exists, add any accounts that are not already registered.
          def discoveryCachingAgentAccounts = discoveryCachingAgent.accounts
          def oldAccountNames = discoveryCachingAgentAccounts.collect { it.name }
          def newAccountNames = accounts.collect { it.name }
          def accountNamesToAdd = newAccountNames - oldAccountNames
          def accountsToAdd = accounts.findAll { account ->
            accountNamesToAdd.contains(account.name)
          }

          discoveryCachingAgentAccounts.addAll(accountsToAdd)
        } else {
          // If the agent doesn't exist yet, create a new one with all of the accounts.
          newlyAddedAgents << new DiscoveryCachingAgent(discoveryApiFactory.createApi(disco, region), accounts, region, objectMapper)
        }
      }
    }

    // Unschedule any discovery caching agents that are left with zero registered accounts.
    List<Agent> agentsToDelete = []

    awsProvider.agents.findAll { agent ->
      agent instanceof DiscoveryCachingAgent
    }.each { DiscoveryCachingAgent discoveryCachingAgent ->
      if (discoveryCachingAgent.accounts.size() == 0) {
        discoveryCachingAgent.agentScheduler.unschedule(discoveryCachingAgent)
        agentsToDelete << discoveryCachingAgent
      }
    }

    awsProvider.agents.removeAll(agentsToDelete)

    // We need to do the scheduling here (because accounts have been added to a running system).
    ProviderUtils.rescheduleAgents(awsProvider, newlyAddedAgents)
  }
}
