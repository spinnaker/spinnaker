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
import com.fasterxml.jackson.databind.SerializationFeature
import com.netflix.spectator.api.Registry
import com.netflix.spinnaker.cats.agent.Agent
import com.netflix.spinnaker.config.GoogleConfiguration
import com.netflix.spinnaker.clouddriver.google.config.GoogleConfigurationProperties
import com.netflix.spinnaker.clouddriver.google.provider.GoogleInfrastructureProvider
import com.netflix.spinnaker.clouddriver.google.provider.agent.*
import com.netflix.spinnaker.clouddriver.google.security.GoogleNamedAccountCredentials
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsRepository
import com.netflix.spinnaker.clouddriver.security.ProviderUtils
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.*

import java.util.concurrent.ConcurrentHashMap

@Configuration
@Import(GoogleConfiguration)
@EnableConfigurationProperties
class GoogleInfrastructureProviderConfig {

  @Autowired Registry registry

  @Bean
  @DependsOn('googleNamedAccountCredentials')
  GoogleInfrastructureProvider googleInfrastructureProvider(String clouddriverUserAgentApplicationName,
                                                            GoogleConfigurationProperties googleConfigurationProperties,
                                                            AccountCredentialsRepository accountCredentialsRepository,
                                                            ObjectMapper objectMapper,
                                                            Registry registry) {
    def googleInfrastructureProvider =
        new GoogleInfrastructureProvider(Collections.newSetFromMap(new ConcurrentHashMap<Agent, Boolean>()))

    synchronizeGoogleInfrastructureProvider(clouddriverUserAgentApplicationName,
                                            googleConfigurationProperties,
                                            googleInfrastructureProvider,
                                            accountCredentialsRepository,
                                            objectMapper,
                                            registry)

    googleInfrastructureProvider
  }

  private static void synchronizeGoogleInfrastructureProvider(
    String clouddriverUserAgentApplicationName,
    GoogleConfigurationProperties googleConfigurationProperties,
    GoogleInfrastructureProvider googleInfrastructureProvider,
    AccountCredentialsRepository accountCredentialsRepository,
    ObjectMapper objectMapper,
    Registry registry) {
    def scheduledAccounts = ProviderUtils.getScheduledAccounts(googleInfrastructureProvider)
    def allAccounts = ProviderUtils.buildThreadSafeSetOfAccounts(accountCredentialsRepository,
                                                                 GoogleNamedAccountCredentials)

    objectMapper.enable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)

    allAccounts.each { GoogleNamedAccountCredentials credentials ->
      if (!scheduledAccounts.contains(credentials.name)) {
        def newlyAddedAgents = []
        def regions = credentials.regions.collect { it.name }

        newlyAddedAgents << new GoogleSecurityGroupCachingAgent(clouddriverUserAgentApplicationName,
                                                                credentials,
                                                                objectMapper,
                                                                registry)
        newlyAddedAgents << new GoogleNetworkCachingAgent(clouddriverUserAgentApplicationName,
                                                          credentials,
                                                          objectMapper,
                                                          registry)

        newlyAddedAgents << new GoogleGlobalAddressCachingAgent(clouddriverUserAgentApplicationName,
                                                                credentials,
                                                                objectMapper,
                                                                registry)

        regions.each { String region ->
          newlyAddedAgents << new GoogleSubnetCachingAgent(clouddriverUserAgentApplicationName,
                                                           credentials,
                                                           objectMapper,
                                                           registry,
                                                           region)
          newlyAddedAgents << new GoogleRegionalAddressCachingAgent(clouddriverUserAgentApplicationName,
                                                                    credentials,
                                                                    objectMapper,
                                                                    registry,
                                                                    region)
        }

        newlyAddedAgents << new GoogleHealthCheckCachingAgent(clouddriverUserAgentApplicationName,
                                                              credentials,
                                                              objectMapper,
                                                              registry)
        newlyAddedAgents << new GoogleHttpHealthCheckCachingAgent(clouddriverUserAgentApplicationName,
                                                                  credentials,
                                                                  objectMapper,
                                                                  registry)
        newlyAddedAgents << new GoogleSslLoadBalancerCachingAgent(clouddriverUserAgentApplicationName,
                                                                  credentials,
                                                                  objectMapper,
                                                                  registry)
        newlyAddedAgents << new GoogleSslCertificateCachingAgent(clouddriverUserAgentApplicationName,
                                                                 credentials,
                                                                 objectMapper,
                                                                 registry)
        newlyAddedAgents << new GoogleTcpLoadBalancerCachingAgent(clouddriverUserAgentApplicationName,
                                                                  credentials,
                                                                  objectMapper,
                                                                  registry)
        newlyAddedAgents << new GoogleBackendServiceCachingAgent(clouddriverUserAgentApplicationName,
                                                                 credentials,
                                                                 objectMapper,
                                                                 registry)
        newlyAddedAgents << new GoogleInstanceCachingAgent(clouddriverUserAgentApplicationName,
                                                           credentials,
                                                           objectMapper,
                                                           registry)
        newlyAddedAgents << new GoogleImageCachingAgent(clouddriverUserAgentApplicationName,
                                                        credentials,
                                                        objectMapper,
                                                        registry,
                                                        credentials.imageProjects,
                                                        googleConfigurationProperties.baseImageProjects)
        newlyAddedAgents << new GoogleHttpLoadBalancerCachingAgent(clouddriverUserAgentApplicationName,
                                                                   credentials,
                                                                   objectMapper,
                                                                   registry)
        regions.each { String region ->
          newlyAddedAgents << new GoogleInternalLoadBalancerCachingAgent(clouddriverUserAgentApplicationName,
                                                                         credentials,
                                                                         objectMapper,
                                                                         registry,
                                                                         region)
          newlyAddedAgents << new GoogleNetworkLoadBalancerCachingAgent(clouddriverUserAgentApplicationName,
                                                                        credentials,
                                                                        objectMapper,
                                                                        registry,
                                                                        region)
          newlyAddedAgents << new GoogleRegionalServerGroupCachingAgent(clouddriverUserAgentApplicationName,
                                                                        credentials,
                                                                        objectMapper,
                                                                        registry,
                                                                        region,
                                                                        googleConfigurationProperties.maxMIGPageSize)
          newlyAddedAgents << new GoogleZonalServerGroupCachingAgent(clouddriverUserAgentApplicationName,
                                                                     credentials,
                                                                     objectMapper,
                                                                     registry,
                                                                     region,
                                                                     googleConfigurationProperties.maxMIGPageSize)
        }

        // If there is an agent scheduler, then this provider has been through the AgentController in the past.
        // In that case, we need to do the scheduling here (because accounts have been added to a running system).
        if (googleInfrastructureProvider.agentScheduler) {
          ProviderUtils.rescheduleAgents(googleInfrastructureProvider, newlyAddedAgents)
        }

        googleInfrastructureProvider.agents.addAll(newlyAddedAgents)
      }
    }
  }
}
