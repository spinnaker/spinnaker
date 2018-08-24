/*
 * Copyright (c) 2017 Oracle America, Inc.
 *
 * The contents of this file are subject to the Apache License Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * If a copy of the Apache License Version 2.0 was not distributed with this file,
 * You can obtain one at https://www.apache.org/licenses/LICENSE-2.0.html
 */
package com.netflix.spinnaker.clouddriver.oracle.provider.config

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.netflix.spectator.api.Registry
import com.netflix.spinnaker.cats.agent.Agent
import com.netflix.spinnaker.cats.provider.ProviderSynchronizerTypeWrapper
import com.netflix.spinnaker.config.OracleConfiguration
import com.netflix.spinnaker.clouddriver.oracle.provider.OracleInfrastructureProvider
import com.netflix.spinnaker.clouddriver.oracle.provider.agent.*
import com.netflix.spinnaker.clouddriver.oracle.security.OracleNamedAccountCredentials
import com.netflix.spinnaker.clouddriver.oracle.service.servergroup.OracleServerGroupService
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsRepository
import com.netflix.spinnaker.clouddriver.security.ProviderUtils
import org.springframework.beans.factory.config.ConfigurableBeanFactory
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.*

import java.util.concurrent.ConcurrentHashMap

@Configuration
@Import(OracleConfiguration)
@EnableConfigurationProperties
class OracleInfrastructureProviderConfig {

  @Bean
  @DependsOn('oracleNamedAccountCredentials')
  OracleInfrastructureProvider oracleInfrastructureProvider(String clouddriverUserAgentApplicationName,
                                                                    AccountCredentialsRepository accountCredentialsRepository,
                                                                    ObjectMapper objectMapper,
                                                                    Registry registry,
                                                                    OracleServerGroupService oracleServerGroupService) {
    def oracleInfrastructureProvider =
      new OracleInfrastructureProvider(Collections.newSetFromMap(new ConcurrentHashMap<Agent, Boolean>()))

    synchronizeOracleInfrastructureProvider(clouddriverUserAgentApplicationName,
      oracleInfrastructureProvider,
      accountCredentialsRepository,
      objectMapper,
      registry,
      oracleServerGroupService
    )

    return oracleInfrastructureProvider
  }

  @Bean
  OracleInfrastructureProviderSynchronizerTypeWrapper oracleInfrastructureProviderSynchronizerTypeWrapper() {
    new OracleInfrastructureProviderSynchronizerTypeWrapper()
  }

  class OracleInfrastructureProviderSynchronizerTypeWrapper implements ProviderSynchronizerTypeWrapper {

    @Override
    Class getSynchronizerType() {
      return OracleInfrastructureProviderSynchronizer
    }
  }

  class OracleInfrastructureProviderSynchronizer {}

  @Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
  @Bean
  OracleInfrastructureProviderSynchronizer synchronizeOracleInfrastructureProvider(
    String clouddriverUserAgentApplicationName,
    OracleInfrastructureProvider oracleInfrastructureProvider,
    AccountCredentialsRepository accountCredentialsRepository,
    ObjectMapper objectMapper,
    Registry registry,
    OracleServerGroupService oracleServerGroupService) {
    def scheduledAccounts = ProviderUtils.getScheduledAccounts(oracleInfrastructureProvider)
    def allAccounts = ProviderUtils.buildThreadSafeSetOfAccounts(accountCredentialsRepository,
      OracleNamedAccountCredentials)

    objectMapper.enable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)

    allAccounts.each { OracleNamedAccountCredentials credentials ->
      if (!scheduledAccounts.contains(credentials.name)) {
        def newlyAddedAgents = []

        newlyAddedAgents << new OracleSecurityGroupCachingAgent(clouddriverUserAgentApplicationName,
          credentials,
          objectMapper,
          registry)

        newlyAddedAgents << new OracleInstanceCachingAgent(clouddriverUserAgentApplicationName,
          credentials,
          objectMapper)

        newlyAddedAgents << new OracleNetworkCachingAgent(clouddriverUserAgentApplicationName,
          credentials,
          objectMapper)

        newlyAddedAgents << new OracleSubnetCachingAgent(clouddriverUserAgentApplicationName,
          credentials,
          objectMapper)

        newlyAddedAgents << new OracleServerGroupCachingAgent(clouddriverUserAgentApplicationName,
          credentials,
          objectMapper,
          oracleServerGroupService)

        newlyAddedAgents << new OracleImageCachingAgent(clouddriverUserAgentApplicationName,
          credentials,
          objectMapper)

        newlyAddedAgents << new OracleLoadBalancerCachingAgent(clouddriverUserAgentApplicationName,
          credentials,
          objectMapper)

        // If there is an agent scheduler, then this provider has been through the AgentController in the past.
        // In that case, we need to do the scheduling here (because accounts have been added to a running system).
        if (oracleInfrastructureProvider.agentScheduler) {
          ProviderUtils.rescheduleAgents(oracleInfrastructureProvider, newlyAddedAgents)
        }

        oracleInfrastructureProvider.agents.addAll(newlyAddedAgents)
      }
    }

    return new OracleInfrastructureProviderSynchronizer()
  }
}
