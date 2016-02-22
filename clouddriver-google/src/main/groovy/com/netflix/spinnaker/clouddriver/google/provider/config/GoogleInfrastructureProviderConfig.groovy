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

package com.netflix.spinnaker.clouddriver.google.provider.config

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spectator.api.Registry
import com.netflix.spinnaker.cats.agent.Agent
import com.netflix.spinnaker.cats.provider.ProviderSynchronizerTypeWrapper
import com.netflix.spinnaker.clouddriver.google.GoogleCloudProvider
import com.netflix.spinnaker.clouddriver.google.GoogleConfiguration
import com.netflix.spinnaker.clouddriver.google.provider.GoogleInfrastructureProvider
import com.netflix.spinnaker.clouddriver.google.provider.agent.GoogleInstanceCachingAgent
import com.netflix.spinnaker.clouddriver.google.provider.agent.GoogleLoadBalancerCachingAgent
import com.netflix.spinnaker.clouddriver.google.provider.agent.GoogleNetworkCachingAgent
import com.netflix.spinnaker.clouddriver.google.provider.agent.GoogleSecurityGroupCachingAgent
import com.netflix.spinnaker.clouddriver.google.provider.agent.GoogleServerGroupCachingAgent
import com.netflix.spinnaker.clouddriver.google.provider.agent.GoogleSubnetCachingAgent
import com.netflix.spinnaker.clouddriver.google.security.GoogleNamedAccountCredentials
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsRepository
import com.netflix.spinnaker.clouddriver.security.ProviderUtils
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.beans.factory.config.ConfigurableBeanFactory
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.DependsOn
import org.springframework.context.annotation.Import
import org.springframework.context.annotation.Scope

import java.util.concurrent.ConcurrentHashMap

@Configuration
@Import(GoogleConfiguration)
@EnableConfigurationProperties
class GoogleInfrastructureProviderConfig {

  @Autowired
  GoogleConfiguration googleConfiguration

  @Value('${google.providerImpl}')
  String providerImpl

  @Bean
  @DependsOn('googleNamedAccountCredentials')
  GoogleInfrastructureProvider googleInfrastructureProvider(GoogleCloudProvider googleCloudProvider,
                                                            AccountCredentialsRepository accountCredentialsRepository,
                                                            ObjectMapper objectMapper,
                                                            Registry registry) {
    def googleInfrastructureProvider =
        new GoogleInfrastructureProvider(googleCloudProvider, Collections.newSetFromMap(new ConcurrentHashMap<Agent, Boolean>()))

    synchronizeGoogleInfrastructureProvider(googleInfrastructureProvider,
                                            googleCloudProvider,
                                            accountCredentialsRepository,
                                            objectMapper,
                                            registry)

    googleInfrastructureProvider
  }

  @Bean
  GoogleInfrastructureProviderSynchronizerTypeWrapper googleInfrastructureProviderSynchronizerTypeWrapper() {
    new GoogleInfrastructureProviderSynchronizerTypeWrapper()
  }

  class GoogleInfrastructureProviderSynchronizerTypeWrapper implements ProviderSynchronizerTypeWrapper {
    @Override
    Class getSynchronizerType() {
      return GoogleInfrastructureProviderSynchronizer
    }
  }

  class GoogleInfrastructureProviderSynchronizer {}

  @Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
  @Bean
  GoogleInfrastructureProviderSynchronizer synchronizeGoogleInfrastructureProvider(
      GoogleInfrastructureProvider googleInfrastructureProvider,
      GoogleCloudProvider googleCloudProvider,
      AccountCredentialsRepository accountCredentialsRepository,
      ObjectMapper objectMapper,
      Registry registry) {
    def scheduledAccounts = ProviderUtils.getScheduledAccounts(googleInfrastructureProvider)
    def allAccounts = ProviderUtils.buildThreadSafeSetOfAccounts(accountCredentialsRepository,
                                                                 GoogleNamedAccountCredentials)

    allAccounts.each { GoogleNamedAccountCredentials credentials ->
      if (!scheduledAccounts.contains(credentials.accountName)) {
        def newlyAddedAgents = []

        newlyAddedAgents << new GoogleSecurityGroupCachingAgent(googleCloudProvider,
                                                                credentials.accountName,
                                                                credentials.credentials.project,
                                                                credentials.credentials.compute,
                                                                objectMapper,
                                                                registry)
        newlyAddedAgents << new GoogleNetworkCachingAgent(googleCloudProvider,
                                                          credentials.accountName,
                                                          credentials.credentials,
                                                          objectMapper)

        credentials.regions.keySet().each { String region ->
          newlyAddedAgents << new GoogleSubnetCachingAgent(googleCloudProvider,
                                                           credentials.accountName,
                                                           region,
                                                           credentials.credentials,
                                                           objectMapper)
        }

        if (providerImpl == "new") {
          newlyAddedAgents << new GoogleInstanceCachingAgent(googleCloudProvider,
                                                             googleConfiguration.googleApplicationName(),
                                                             credentials.accountName,
                                                             credentials.credentials.project,
                                                             credentials.credentials.compute,
                                                             objectMapper)
          credentials.regions.keySet().each { String region ->
            newlyAddedAgents << new GoogleLoadBalancerCachingAgent(googleCloudProvider,
                                                                   googleConfiguration.googleApplicationName(),
                                                                   credentials.accountName,
                                                                   region,
                                                                   credentials.credentials.project,
                                                                   credentials.credentials.compute,
                                                                   objectMapper)
            newlyAddedAgents << new GoogleServerGroupCachingAgent(googleCloudProvider,
                                                                  googleConfiguration.googleApplicationName(),
                                                                  credentials.accountName,
                                                                  region,
                                                                  credentials.credentials.project,
                                                                  credentials.credentials.compute,
                                                                  objectMapper)
          }
        }

        // If there is an agent scheduler, then this provider has been through the AgentController in the past.
        // In that case, we need to do the scheduling here (because accounts have been added to a running system).
        if (googleInfrastructureProvider.agentScheduler) {
          ProviderUtils.rescheduleAgents(googleInfrastructureProvider, newlyAddedAgents)
        }

        googleInfrastructureProvider.agents.addAll(newlyAddedAgents)
      }
    }

    new GoogleInfrastructureProviderSynchronizer()
  }
}
