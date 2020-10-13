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
import com.google.common.util.concurrent.ThreadFactoryBuilder
import com.netflix.spectator.api.Registry
import com.netflix.spinnaker.cats.agent.Agent
import com.netflix.spinnaker.cats.agent.AgentProvider
import com.netflix.spinnaker.clouddriver.aws.AmazonCloudProvider
import com.netflix.spinnaker.clouddriver.aws.edda.EddaApiFactory
import com.netflix.spinnaker.clouddriver.aws.provider.AwsProvider
import com.netflix.spinnaker.clouddriver.aws.provider.agent.ReservationReportCachingAgent
import com.netflix.spinnaker.clouddriver.aws.provider.view.AmazonS3DataProvider
import com.netflix.spinnaker.clouddriver.aws.security.AmazonClientProvider
import com.netflix.spinnaker.clouddriver.aws.security.EddaTimeoutConfig
import com.netflix.spinnaker.clouddriver.aws.security.NetflixAmazonCredentials
import com.netflix.spinnaker.clouddriver.model.ReservationReport
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsRepository
import com.netflix.spinnaker.clouddriver.security.ProviderUtils
import com.netflix.spinnaker.kork.dynamicconfig.DynamicConfigService
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.ApplicationContext
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.DependsOn

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
                          Optional<ExecutorService> reservationReportPool,
                          Optional<Collection<AgentProvider>> agentProviders,
                          EddaTimeoutConfig eddaTimeoutConfig,
                          DynamicConfigService dynamicConfigService) {
    def awsProvider =
      new AwsProvider(accountCredentialsRepository)

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
  @ConditionalOnProperty("reports.reservation.enabled")
  ExecutorService reservationReportPool(ReservationReportConfigurationProperties reservationReportConfigurationProperties) {
    return Executors.newFixedThreadPool(
        reservationReportConfigurationProperties.threadPoolSize,
        new ThreadFactoryBuilder()
          .setNameFormat(ReservationReport.class.getSimpleName() + "-%d")
          .build());
  }

  private void synchronizeAwsProvider(AwsProvider awsProvider,
                                      AmazonCloudProvider amazonCloudProvider,
                                      AmazonClientProvider amazonClientProvider,
                                      AmazonS3DataProvider amazonS3DataProvider,
                                      AccountCredentialsRepository accountCredentialsRepository,
                                      ObjectMapper objectMapper,
                                      EddaApiFactory eddaApiFactory,
                                      ApplicationContext ctx,
                                      Registry registry,
                                      Optional<ExecutorService> reservationReportPool,
                                      Collection<AgentProvider> agentProviders,
                                      EddaTimeoutConfig eddaTimeoutConfig,
                                      DynamicConfigService dynamicConfigService) {
    Set<NetflixAmazonCredentials> allAccounts = ProviderUtils.buildThreadSafeSetOfAccounts(accountCredentialsRepository, NetflixAmazonCredentials, AmazonCloudProvider.ID)
    List<Agent> newlyAddedAgents = []

    //only index public images once per region
    Set<String> publicRegions = []

    //sort the accounts in case of a reconfigure, we are more likely to re-index the public images in the same caching agent
    //TODO(cfieber)-rework this is after rework of AWS Image/NamedImage keys
    allAccounts.sort { it.name }.each { NetflixAmazonCredentials credentials ->
      def result = ProviderHelpers.buildAwsProviderAgents(credentials, amazonClientProvider, objectMapper,
        registry, eddaTimeoutConfig, awsProvider, amazonCloudProvider, dynamicConfigService, eddaApiFactory, ctx, publicRegions
      )
      newlyAddedAgents.addAll(result.agents)
      publicRegions.addAll(result.regionsToAdd)
    }

    // If there is an agent scheduler, then this provider has been through the AgentController in the past.
    if (reservationReportPool.isPresent()) {
      if (awsProvider.agentScheduler) {
        ProviderHelpers.synchronizeReservationReportCachingAgentAccounts(awsProvider, allAccounts)
      } else {
        // This caching agent runs across all accounts in one iteration (to maintain consistency).
        newlyAddedAgents << new ReservationReportCachingAgent(
          registry, amazonClientProvider, amazonS3DataProvider, allAccounts, objectMapper, reservationReportPool.get(), ctx
        )
      }
    }

    agentProviders.findAll { it.supports(AwsProvider.PROVIDER_NAME) }.each {
      newlyAddedAgents.addAll(it.agents())
    }

    awsProvider.addAgents(newlyAddedAgents)
    awsProvider.synchronizeHealthAgents()
  }
}
