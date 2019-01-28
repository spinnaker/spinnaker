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
import com.netflix.spinnaker.cats.agent.AgentProvider
import com.netflix.spinnaker.cats.provider.ProviderSynchronizerTypeWrapper
import com.netflix.spinnaker.clouddriver.aws.AmazonCloudProvider
import com.netflix.spinnaker.clouddriver.aws.provider.agent.AmazonApplicationLoadBalancerCachingAgent
import com.netflix.spinnaker.clouddriver.aws.provider.agent.AmazonCertificateCachingAgent
import com.netflix.spinnaker.clouddriver.aws.provider.agent.AmazonCloudFormationCachingAgent
import com.netflix.spinnaker.clouddriver.aws.provider.agent.AmazonLoadBalancerCachingAgent

import com.netflix.spinnaker.clouddriver.aws.provider.agent.ReservedInstancesCachingAgent
import com.netflix.spinnaker.clouddriver.aws.provider.view.AmazonS3DataProvider
import com.netflix.spinnaker.clouddriver.aws.security.AmazonClientProvider
import com.netflix.spinnaker.clouddriver.aws.security.AmazonCredentials
import com.netflix.spinnaker.clouddriver.aws.security.EddaTimeoutConfig
import com.netflix.spinnaker.clouddriver.aws.security.NetflixAmazonCredentials
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsRepository
import com.netflix.spinnaker.clouddriver.security.ProviderUtils
import com.netflix.spinnaker.clouddriver.aws.edda.EddaApiFactory
import com.netflix.spinnaker.clouddriver.aws.provider.AwsProvider
import com.netflix.spinnaker.clouddriver.aws.provider.agent.AmazonLoadBalancerInstanceStateCachingAgent
import com.netflix.spinnaker.clouddriver.aws.provider.agent.ClusterCachingAgent
import com.netflix.spinnaker.clouddriver.aws.provider.agent.EddaLoadBalancerCachingAgent
import com.netflix.spinnaker.clouddriver.aws.provider.agent.ImageCachingAgent
import com.netflix.spinnaker.clouddriver.aws.provider.agent.InstanceCachingAgent
import com.netflix.spinnaker.clouddriver.aws.provider.agent.LaunchConfigCachingAgent
import com.netflix.spinnaker.clouddriver.aws.provider.agent.ReservationReportCachingAgent
import com.netflix.spinnaker.kork.dynamicconfig.DynamicConfigService
import org.springframework.beans.factory.config.ConfigurableBeanFactory
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.ApplicationContext
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.DependsOn
import org.springframework.context.annotation.Scope

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

@Configuration
@EnableConfigurationProperties(ReservationReportConfigurationProperties)
class AwsProviderConfig {
  @Bean
  @DependsOn('netflixAmazonCredentials')
  AwsProvider awsProvider(AmazonCloudProvider amazonCloudProvider,
                          AmazonClientProvider amazonClientProvider,
                          AmazonS3DataProvider amazonS3DataProvider,
                          AccountCredentialsRepository accountCredentialsRepository,
                          ObjectMapper objectMapper,
                          EddaApiFactory eddaApiFactory,
                          ApplicationContext ctx,
                          Registry registry,
                          ExecutorService reservationReportPool,
                          Optional<Collection<AgentProvider>> agentProviders,
                          EddaTimeoutConfig eddaTimeoutConfig,
                          DynamicConfigService dynamicConfigService) {
    def awsProvider =
      new AwsProvider(accountCredentialsRepository, Collections.newSetFromMap(new ConcurrentHashMap<Agent, Boolean>()))

    synchronizeAwsProvider(awsProvider,
                           amazonCloudProvider,
                           amazonClientProvider,
                           amazonS3DataProvider,
                           accountCredentialsRepository,
                           objectMapper,
                           eddaApiFactory,
                           ctx,
                           registry,
                           reservationReportPool,
                           agentProviders.orElse(Collections.emptyList()),
                           eddaTimeoutConfig,
                           dynamicConfigService)

    awsProvider
  }

  @Bean
  ExecutorService reservationReportPool(ReservationReportConfigurationProperties reservationReportConfigurationProperties) {
    return Executors.newFixedThreadPool(reservationReportConfigurationProperties.threadPoolSize)
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
                                                 AmazonS3DataProvider amazonS3DataProvider,
                                                 AccountCredentialsRepository accountCredentialsRepository,
                                                 ObjectMapper objectMapper,
                                                 EddaApiFactory eddaApiFactory,
                                                 ApplicationContext ctx,
                                                 Registry registry,
                                                 ExecutorService reservationReportPool,
                                                 Collection<AgentProvider> agentProviders,
                                                 EddaTimeoutConfig eddaTimeoutConfig,
                                                 DynamicConfigService dynamicConfigService) {
    def scheduledAccounts = ProviderUtils.getScheduledAccounts(awsProvider)
    Set<NetflixAmazonCredentials> allAccounts = ProviderUtils.buildThreadSafeSetOfAccounts(accountCredentialsRepository, NetflixAmazonCredentials)

    List<Agent> newlyAddedAgents = []

    //only index public images once per region
    Set<String> publicRegions = []

    //sort the accounts in case of a reconfigure, we are more likely to re-index the public images in the same caching agent
    //TODO(cfieber)-rework this is after rework of AWS Image/NamedImage keys
    allAccounts.sort { it.name }.each { NetflixAmazonCredentials credentials ->
      for (AmazonCredentials.AWSRegion region : credentials.regions) {
        if (!scheduledAccounts.contains(credentials.name)) {
          newlyAddedAgents << new ClusterCachingAgent(amazonCloudProvider, amazonClientProvider, credentials, region.name, objectMapper, registry, eddaTimeoutConfig)
          newlyAddedAgents << new LaunchConfigCachingAgent(amazonClientProvider, credentials, region.name, objectMapper, registry)
          newlyAddedAgents << new ImageCachingAgent(amazonClientProvider, credentials, region.name, objectMapper, registry, false, dynamicConfigService)
          if (!publicRegions.contains(region.name)) {
            newlyAddedAgents << new ImageCachingAgent(amazonClientProvider, credentials, region.name, objectMapper, registry, true, dynamicConfigService)
            publicRegions.add(region.name)
          }
          newlyAddedAgents << new InstanceCachingAgent(amazonClientProvider, credentials, region.name, objectMapper, registry)
          newlyAddedAgents << new AmazonLoadBalancerCachingAgent(amazonCloudProvider, amazonClientProvider, credentials, region.name, eddaApiFactory.createApi(credentials.edda, region.name), objectMapper, registry)
          newlyAddedAgents << new AmazonApplicationLoadBalancerCachingAgent(amazonCloudProvider, amazonClientProvider, credentials, region.name, eddaApiFactory.createApi(credentials.edda, region.name), objectMapper, registry, eddaTimeoutConfig)
          newlyAddedAgents << new ReservedInstancesCachingAgent(amazonClientProvider, credentials, region.name, objectMapper, registry)
          newlyAddedAgents << new AmazonCertificateCachingAgent(amazonClientProvider, credentials, region.name, objectMapper, registry)

          if (dynamicConfigService.isEnabled("aws.features.cloudFormation", false)) {
            newlyAddedAgents << new AmazonCloudFormationCachingAgent(amazonClientProvider, credentials, region.name)
          }

          if (credentials.eddaEnabled && !eddaTimeoutConfig.disabledRegions.contains(region.name)) {
            newlyAddedAgents << new EddaLoadBalancerCachingAgent(eddaApiFactory.createApi(credentials.edda, region.name), credentials, region.name, objectMapper)
          } else {
            newlyAddedAgents << new AmazonLoadBalancerInstanceStateCachingAgent(
              amazonClientProvider, credentials, region.name, objectMapper, ctx
            )
          }
        }
      }
    }

    // If there is an agent scheduler, then this provider has been through the AgentController in the past.
    if (awsProvider.agentScheduler) {
      synchronizeReservationReportCachingAgentAccounts(awsProvider, allAccounts)
    } else {
      // This caching agent runs across all accounts in one iteration (to maintain consistency).
      newlyAddedAgents << new ReservationReportCachingAgent(
        registry, amazonClientProvider, amazonS3DataProvider, allAccounts, objectMapper, reservationReportPool, ctx
      )
    }

    agentProviders.findAll { it.supports(AwsProvider.PROVIDER_NAME) }.each {
      newlyAddedAgents.addAll(it.agents())
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
}
