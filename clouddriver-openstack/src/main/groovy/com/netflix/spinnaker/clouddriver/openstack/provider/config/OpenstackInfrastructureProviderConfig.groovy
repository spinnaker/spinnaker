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

import com.google.common.collect.Sets
import com.netflix.spinnaker.cats.agent.CachingAgent
import com.netflix.spinnaker.cats.provider.ProviderSynchronizerTypeWrapper
import com.netflix.spinnaker.clouddriver.openstack.provider.OpenstackInfastructureProvider
import com.netflix.spinnaker.clouddriver.openstack.provider.agent.OpenstackInstanceCachingAgent
import com.netflix.spinnaker.clouddriver.openstack.security.OpenstackNamedAccountCredentials
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsRepository
import com.netflix.spinnaker.clouddriver.security.ProviderUtils
import org.springframework.beans.factory.config.ConfigurableBeanFactory
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.DependsOn
import org.springframework.context.annotation.Scope

@Configuration
class OpenstackInfrastructureProviderConfig {
  @Bean
  @DependsOn('openstackNamedAccountCredentials')
  OpenstackInfastructureProvider openstackInfastructureProvider(AccountCredentialsRepository accountCredentialsRepository) {
    OpenstackInfastructureProvider provider = new OpenstackInfastructureProvider(Sets.newConcurrentHashSet())
    synchronizeOpenstackProvider(provider, accountCredentialsRepository)
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
  OpenstackProviderSynchronizer synchronizeOpenstackProvider(OpenstackInfastructureProvider openstackInfastructureProvider,
                                                             AccountCredentialsRepository accountCredentialsRepository) {
    def scheduledAccounts = ProviderUtils.getScheduledAccounts(openstackInfastructureProvider)
    def allAccounts = ProviderUtils.buildThreadSafeSetOfAccounts(accountCredentialsRepository, OpenstackNamedAccountCredentials)

    List<CachingAgent> newlyAddedAgents = []

    allAccounts.each { OpenstackNamedAccountCredentials credentials ->
      if (!scheduledAccounts.contains(credentials.name)) {
        credentials.regions.each { String region ->
          newlyAddedAgents << new OpenstackInstanceCachingAgent(credentials, region)
        }
      }
    }

    if (!newlyAddedAgents.isEmpty()) {
      openstackInfastructureProvider.agents.addAll(newlyAddedAgents)
    }

    new OpenstackProviderSynchronizer()
  }
}
