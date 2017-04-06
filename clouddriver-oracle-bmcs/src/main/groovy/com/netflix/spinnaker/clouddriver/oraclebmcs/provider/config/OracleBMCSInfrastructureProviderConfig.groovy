/*
 * Copyright (c) 2017 Oracle America, Inc.
 *
 * The contents of this file are subject to the Apache License Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * If a copy of the Apache License Version 2.0 was not distributed with this file,
 * You can obtain one at https://www.apache.org/licenses/LICENSE-2.0.html
 */
package com.netflix.spinnaker.clouddriver.oraclebmcs.provider.config

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.netflix.spectator.api.Registry
import com.netflix.spinnaker.cats.agent.Agent
import com.netflix.spinnaker.cats.provider.ProviderSynchronizerTypeWrapper
import com.netflix.spinnaker.clouddriver.oraclebmcs.OracleBMCSConfiguration
import com.netflix.spinnaker.clouddriver.oraclebmcs.provider.OracleBMCSInfrastructureProvider
import com.netflix.spinnaker.clouddriver.oraclebmcs.provider.agent.*
import com.netflix.spinnaker.clouddriver.oraclebmcs.security.OracleBMCSNamedAccountCredentials
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsRepository
import com.netflix.spinnaker.clouddriver.security.ProviderUtils
import org.springframework.beans.factory.config.ConfigurableBeanFactory
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.*

import java.util.concurrent.ConcurrentHashMap

@Configuration
@Import(OracleBMCSConfiguration)
@EnableConfigurationProperties
class OracleBMCSInfrastructureProviderConfig {

  @Bean
  @DependsOn('oracleBMCSNamedAccountCredentials')
  OracleBMCSInfrastructureProvider oracleBMCSInfrastructureProvider(String clouddriverUserAgentApplicationName,
                                                                    AccountCredentialsRepository accountCredentialsRepository,
                                                                    ObjectMapper objectMapper,
                                                                    Registry registry) {
    def oracleBMCSInfrastructureProvider =
      new OracleBMCSInfrastructureProvider(Collections.newSetFromMap(new ConcurrentHashMap<Agent, Boolean>()))

    synchronizeOracleBMCSInfrastructureProvider(clouddriverUserAgentApplicationName,
      oracleBMCSInfrastructureProvider,
      accountCredentialsRepository,
      objectMapper,
      registry
    )

    return oracleBMCSInfrastructureProvider
  }

  @Bean
  OracleBMCSInfrastructureProviderSynchronizerTypeWrapper oracleBMCSInfrastructureProviderSynchronizerTypeWrapper() {
    new OracleBMCSInfrastructureProviderSynchronizerTypeWrapper()
  }

  class OracleBMCSInfrastructureProviderSynchronizerTypeWrapper implements ProviderSynchronizerTypeWrapper {
    @Override
    Class getSynchronizerType() {
      return OracleBMCSInfrastructureProviderSynchronizer
    }
  }

  class OracleBMCSInfrastructureProviderSynchronizer {}

  @Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
  @Bean
  OracleBMCSInfrastructureProviderSynchronizer synchronizeOracleBMCSInfrastructureProvider(
    String clouddriverUserAgentApplicationName,
    OracleBMCSInfrastructureProvider oracleBMCSInfrastructureProvider,
    AccountCredentialsRepository accountCredentialsRepository,
    ObjectMapper objectMapper,
    Registry registry) {
    def scheduledAccounts = ProviderUtils.getScheduledAccounts(oracleBMCSInfrastructureProvider)
    def allAccounts = ProviderUtils.buildThreadSafeSetOfAccounts(accountCredentialsRepository,
      OracleBMCSNamedAccountCredentials)

    objectMapper.enable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)

    allAccounts.each { OracleBMCSNamedAccountCredentials credentials ->
      if (!scheduledAccounts.contains(credentials.name)) {
        def newlyAddedAgents = []

        newlyAddedAgents << new OracleBMCSSecurityGroupCachingAgent(clouddriverUserAgentApplicationName,
          credentials,
          objectMapper,
          registry)

        newlyAddedAgents << new OracleBMCSInstanceCachingAgent(clouddriverUserAgentApplicationName,
          credentials,
          objectMapper)

        newlyAddedAgents << new OracleBMCSNetworkCachingAgent(clouddriverUserAgentApplicationName,
          credentials,
          objectMapper)

        newlyAddedAgents << new OracleBMCSSubnetCachingAgent(clouddriverUserAgentApplicationName,
          credentials,
          objectMapper)

        newlyAddedAgents << new OracleBMCSImageCachingAgent(clouddriverUserAgentApplicationName,
          credentials,
          objectMapper)

        // If there is an agent scheduler, then this provider has been through the AgentController in the past.
        // In that case, we need to do the scheduling here (because accounts have been added to a running system).
        if (oracleBMCSInfrastructureProvider.agentScheduler) {
          ProviderUtils.rescheduleAgents(oracleBMCSInfrastructureProvider, newlyAddedAgents)
        }

        oracleBMCSInfrastructureProvider.agents.addAll(newlyAddedAgents)
      }
    }

    return new OracleBMCSInfrastructureProviderSynchronizer()
  }
}
