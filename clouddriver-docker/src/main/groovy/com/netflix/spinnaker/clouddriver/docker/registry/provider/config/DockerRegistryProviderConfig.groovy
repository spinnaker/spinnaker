/*
 * Copyright 2016 Google, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.clouddriver.docker.registry.provider.config

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spectator.api.Registry
import com.netflix.spinnaker.cats.agent.Agent
import com.netflix.spinnaker.clouddriver.docker.registry.DockerRegistryCloudProvider
import com.netflix.spinnaker.clouddriver.docker.registry.provider.DockerRegistryProvider
import com.netflix.spinnaker.clouddriver.docker.registry.provider.agent.DockerRegistryImageCachingAgent
import com.netflix.spinnaker.clouddriver.docker.registry.security.DockerRegistryNamedAccountCredentials
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsRepository
import com.netflix.spinnaker.clouddriver.security.ProviderUtils
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.DependsOn

import java.util.concurrent.ConcurrentHashMap

@Configuration
class DockerRegistryProviderConfig {
  @Bean
  @DependsOn('dockerRegistryNamedAccountCredentials')
  DockerRegistryProvider dockerRegistryProvider(DockerRegistryCloudProvider dockerRegistryCloudProvider,
                                                AccountCredentialsRepository accountCredentialsRepository,
                                                ObjectMapper objectMapper,
                                                Registry registry) {
    def dockerRegistryProvider = new DockerRegistryProvider(dockerRegistryCloudProvider, Collections.newSetFromMap(new ConcurrentHashMap<Agent, Boolean>()))

    synchronizeDockerRegistryProvider(dockerRegistryProvider, dockerRegistryCloudProvider, accountCredentialsRepository, objectMapper, registry)

    dockerRegistryProvider
  }

  private static void synchronizeDockerRegistryProvider(DockerRegistryProvider dockerRegistryProvider,
                                                        DockerRegistryCloudProvider dockerRegistryCloudProvider,
                                                        AccountCredentialsRepository accountCredentialsRepository,
                                                        ObjectMapper objectMapper,
                                                        Registry registry) {
    def scheduledAccounts = ProviderUtils.getScheduledAccounts(dockerRegistryProvider)
    def allAccounts = ProviderUtils.buildThreadSafeSetOfAccounts(accountCredentialsRepository, DockerRegistryNamedAccountCredentials)

    allAccounts.each { DockerRegistryNamedAccountCredentials credentials ->
      if (!scheduledAccounts.contains(credentials.accountName)) {
        def newlyAddedAgents = []

        credentials.cacheThreads.times { i ->
          newlyAddedAgents << new DockerRegistryImageCachingAgent(dockerRegistryCloudProvider, credentials.accountName, credentials.credentials, i, credentials.cacheThreads, credentials.cacheIntervalSeconds, credentials.registry)
        }

        // If there is an agent scheduler, then this provider has been through the AgentController in the past.
        // In that case, we need to do the scheduling here (because accounts have been added to a running system).
        if (dockerRegistryProvider.agentScheduler) {
          ProviderUtils.rescheduleAgents(dockerRegistryProvider, newlyAddedAgents)
        }

        dockerRegistryProvider.agents.addAll(newlyAddedAgents)
      }
    }
  }
}
