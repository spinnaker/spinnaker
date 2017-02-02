/*
 * Copyright 2016 Google, Inc.
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

package com.netflix.spinnaker.clouddriver.appengine.provider.config

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.netflix.spectator.api.Registry
import com.netflix.spinnaker.cats.agent.Agent
import com.netflix.spinnaker.cats.provider.ProviderSynchronizerTypeWrapper
import com.netflix.spinnaker.cats.thread.NamedThreadFactory
import com.netflix.spinnaker.clouddriver.appengine.AppengineCloudProvider
import com.netflix.spinnaker.clouddriver.appengine.provider.AppengineProvider
import com.netflix.spinnaker.clouddriver.appengine.provider.agent.AppengineLoadBalancerCachingAgent
import com.netflix.spinnaker.clouddriver.appengine.provider.agent.AppengineServerGroupCachingAgent
import com.netflix.spinnaker.clouddriver.appengine.security.AppengineNamedAccountCredentials
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsRepository
import com.netflix.spinnaker.clouddriver.security.ProviderUtils
import org.springframework.beans.factory.config.ConfigurableBeanFactory
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.DependsOn
import org.springframework.context.annotation.Scope

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

@Configuration
class AppengineProviderConfig {
  @Bean
  @DependsOn('appengineNamedAccountCredentials')
  AppengineProvider appengineProvider(AppengineCloudProvider appengineCloudProvider,
                                      AccountCredentialsRepository accountCredentialsRepository,
                                      ObjectMapper objectMapper,
                                      Registry registry) {
    def appengineProvider = new AppengineProvider(appengineCloudProvider,
                                                   Collections.newSetFromMap(new ConcurrentHashMap<Agent, Boolean>()))

    synchronizeAppengineProvider(appengineProvider,
                                 accountCredentialsRepository,
                                 objectMapper,
                                 registry)
    appengineProvider
  }

  @Bean
  AppengineProviderSynchronizerTypeWrapper appengineProviderSynchronizerTypeWrapper() {
    new AppengineProviderSynchronizerTypeWrapper()
  }

  class AppengineProviderSynchronizerTypeWrapper implements ProviderSynchronizerTypeWrapper {
    @Override
    Class getSynchronizerType() {
      return AppengineProviderSynchronizer
    }
  }

  class AppengineProviderSynchronizer { }

  @Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
  @Bean
  AppengineProviderSynchronizer synchronizeAppengineProvider(AppengineProvider appengineProvider,
                                                             AccountCredentialsRepository accountCredentialsRepository,
                                                             ObjectMapper objectMapper,
                                                             Registry registry) {
    def scheduledAccounts = ProviderUtils.getScheduledAccounts(appengineProvider)
    def allAccounts = ProviderUtils.buildThreadSafeSetOfAccounts(accountCredentialsRepository,
                                                                 AppengineNamedAccountCredentials)

    objectMapper.enable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)

    def newlyAddedAgents = []
    allAccounts.each { AppengineNamedAccountCredentials credentials ->
      if (!scheduledAccounts.contains(credentials.name)) {
        newlyAddedAgents << new AppengineServerGroupCachingAgent(credentials.name,
                                                                 credentials,
                                                                 objectMapper,
                                                                 registry)

        newlyAddedAgents << new AppengineLoadBalancerCachingAgent(credentials.name,
                                                                  credentials,
                                                                  objectMapper,
                                                                  registry)
      }
    }

    if (!newlyAddedAgents.isEmpty()) {
      appengineProvider.agents.addAll(newlyAddedAgents)
    }

    new AppengineProviderSynchronizer()
  }
}
