/*
 * Copyright 2016 Target Inc.
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

package com.netflix.spinnaker.clouddriver.openstack.provider.config

import com.fasterxml.jackson.databind.ObjectMapper
import com.google.common.collect.Sets
import com.netflix.spectator.api.Registry
import com.netflix.spinnaker.cats.agent.CachingAgent
import com.netflix.spinnaker.cats.provider.ProviderSynchronizerTypeWrapper
import com.netflix.spinnaker.clouddriver.openstack.provider.OpenstackInfrastructureProvider
import com.netflix.spinnaker.clouddriver.openstack.provider.agent.OpenstackFloatingIPCachingAgent
import com.netflix.spinnaker.clouddriver.openstack.provider.agent.OpenstackImageCachingAgent
import com.netflix.spinnaker.clouddriver.openstack.provider.agent.OpenstackInstanceCachingAgent
import com.netflix.spinnaker.clouddriver.openstack.provider.agent.OpenstackInstanceTypeCachingAgent
import com.netflix.spinnaker.clouddriver.openstack.provider.agent.OpenstackLoadBalancerCachingAgent
import com.netflix.spinnaker.clouddriver.openstack.provider.agent.OpenstackNetworkCachingAgent
import com.netflix.spinnaker.clouddriver.openstack.provider.agent.OpenstackSecurityGroupCachingAgent
import com.netflix.spinnaker.clouddriver.openstack.provider.agent.OpenstackServerGroupCachingAgent
import com.netflix.spinnaker.clouddriver.openstack.provider.agent.OpenstackSubnetCachingAgent
import com.netflix.spinnaker.clouddriver.openstack.security.OpenstackNamedAccountCredentials
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsRepository
import com.netflix.spinnaker.clouddriver.security.ProviderUtils
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.config.ConfigurableBeanFactory
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.DependsOn
import org.springframework.context.annotation.Scope

@Configuration
class OpenstackInfrastructureProviderConfig {

  @Bean
  ObjectMapper infraObjectMapper() {
    new ObjectMapper()
  }

  @Bean
  @DependsOn('openstackNamedAccountCredentials')
  OpenstackInfrastructureProvider openstackInfastructureProvider(AccountCredentialsRepository accountCredentialsRepository,
                                                                 @Qualifier('infraObjectMapper') ObjectMapper objectMapper, Registry registry) {
    OpenstackInfrastructureProvider provider = new OpenstackInfrastructureProvider(Sets.newConcurrentHashSet())
    synchronizeOpenstackProvider(provider, accountCredentialsRepository, objectMapper, registry)
    provider
  }

  @Bean
  OpenstackProviderSynchronizerTypeWrapper openstackProviderSynchronizerTypeWrapper() {
    new OpenstackProviderSynchronizerTypeWrapper()
  }

  class OpenstackProviderSynchronizerTypeWrapper implements ProviderSynchronizerTypeWrapper {
    @Override
    Class getSynchronizerType() {
      return OpenstackProviderSynchronizer
    }
  }

  class OpenstackProviderSynchronizer {}

  @Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
  @Bean
  OpenstackProviderSynchronizer synchronizeOpenstackProvider(OpenstackInfrastructureProvider openstackInfastructureProvider,
                                                             AccountCredentialsRepository accountCredentialsRepository,
                                                             @Qualifier('infraObjectMapper') ObjectMapper objectMapper,
                                                             Registry registry) {
    def scheduledAccounts = ProviderUtils.getScheduledAccounts(openstackInfastructureProvider)
    def allAccounts = ProviderUtils.buildThreadSafeSetOfAccounts(accountCredentialsRepository, OpenstackNamedAccountCredentials)

    List<CachingAgent> newlyAddedAgents = []

    allAccounts.each { OpenstackNamedAccountCredentials credentials ->
      if (!scheduledAccounts.contains(credentials.name)) {
        credentials.credentials.provider.allRegions.each { String region ->
          newlyAddedAgents << new OpenstackInstanceCachingAgent(credentials, region, objectMapper)
          newlyAddedAgents << new OpenstackServerGroupCachingAgent(credentials, region, objectMapper, registry)
          newlyAddedAgents << new OpenstackSubnetCachingAgent(credentials, region, objectMapper)
          newlyAddedAgents << new OpenstackNetworkCachingAgent(credentials, region, objectMapper)
          newlyAddedAgents << new OpenstackImageCachingAgent(credentials, region, objectMapper)
          newlyAddedAgents << new OpenstackSecurityGroupCachingAgent(credentials, region, objectMapper, registry)
          newlyAddedAgents << new OpenstackFloatingIPCachingAgent(credentials, region, objectMapper)
          newlyAddedAgents << new OpenstackLoadBalancerCachingAgent(credentials, region, objectMapper, registry)
          newlyAddedAgents << new OpenstackInstanceTypeCachingAgent(credentials, region, objectMapper)
        }
      }
    }

    if (!newlyAddedAgents.isEmpty()) {
      openstackInfastructureProvider.agents.addAll(newlyAddedAgents)
    }

    new OpenstackProviderSynchronizer()
  }
}
