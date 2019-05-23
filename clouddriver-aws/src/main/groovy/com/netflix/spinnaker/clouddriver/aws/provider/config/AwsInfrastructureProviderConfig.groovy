/*
 * Copyright 2015 Netflix, Inc.
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
import com.netflix.spinnaker.clouddriver.aws.provider.AwsInfrastructureProvider
import com.netflix.spinnaker.clouddriver.aws.provider.agent.AmazonElasticIpCachingAgent
import com.netflix.spinnaker.clouddriver.aws.provider.agent.AmazonInstanceTypeCachingAgent
import com.netflix.spinnaker.clouddriver.aws.provider.agent.AmazonKeyPairCachingAgent
import com.netflix.spinnaker.clouddriver.aws.provider.agent.AmazonSecurityGroupCachingAgent
import com.netflix.spinnaker.clouddriver.aws.provider.agent.AmazonSubnetCachingAgent
import com.netflix.spinnaker.clouddriver.aws.provider.agent.AmazonVpcCachingAgent
import com.netflix.spinnaker.clouddriver.aws.security.AmazonClientProvider
import com.netflix.spinnaker.clouddriver.aws.security.AmazonCredentials
import com.netflix.spinnaker.clouddriver.aws.security.EddaTimeoutConfig
import com.netflix.spinnaker.clouddriver.aws.security.NetflixAmazonCredentials
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsRepository
import com.netflix.spinnaker.clouddriver.security.ProviderUtils
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.DependsOn

import java.util.concurrent.ConcurrentHashMap

@Configuration
class AwsInfrastructureProviderConfig {
  @Bean
  @DependsOn('netflixAmazonCredentials')
  AwsInfrastructureProvider awsInfrastructureProvider(AmazonClientProvider amazonClientProvider,
                                                      AccountCredentialsRepository accountCredentialsRepository,
                                                      @Qualifier("amazonObjectMapper") ObjectMapper amazonObjectMapper,
                                                      Registry registry,
                                                      EddaTimeoutConfig eddaTimeoutConfig) {
    def awsInfrastructureProvider =
      new AwsInfrastructureProvider(Collections.newSetFromMap(new ConcurrentHashMap<Agent, Boolean>()))

    synchronizeAwsInfrastructureProvider(awsInfrastructureProvider,
                                         amazonClientProvider,
                                         accountCredentialsRepository,
                                         amazonObjectMapper,
                                         registry,
                                         eddaTimeoutConfig)

    awsInfrastructureProvider
  }

  private static void synchronizeAwsInfrastructureProvider(AwsInfrastructureProvider awsInfrastructureProvider,
                                                           AmazonClientProvider amazonClientProvider,
                                                           AccountCredentialsRepository accountCredentialsRepository,
                                                           @Qualifier("amazonObjectMapper") ObjectMapper amazonObjectMapper,
                                                           Registry registry,
                                                           EddaTimeoutConfig eddaTimeoutConfig) {
    def scheduledAccounts = ProviderUtils.getScheduledAccounts(awsInfrastructureProvider)
    def allAccounts = ProviderUtils.buildThreadSafeSetOfAccounts(accountCredentialsRepository, NetflixAmazonCredentials)

    Set<String> regions = new HashSet<>();
    allAccounts.each { NetflixAmazonCredentials credentials ->
      for (AmazonCredentials.AWSRegion region : credentials.regions) {
        if (!scheduledAccounts.contains(credentials.name)) {
          def newlyAddedAgents = []

          if (regions.add(region.name)) {
            newlyAddedAgents << new AmazonInstanceTypeCachingAgent(region.name, accountCredentialsRepository)
          }

          newlyAddedAgents << new AmazonElasticIpCachingAgent(amazonClientProvider, credentials, region.name)
          newlyAddedAgents << new AmazonKeyPairCachingAgent(amazonClientProvider, credentials, region.name)
          newlyAddedAgents << new AmazonSecurityGroupCachingAgent(amazonClientProvider, credentials, region.name, amazonObjectMapper, registry, eddaTimeoutConfig)
          newlyAddedAgents << new AmazonSubnetCachingAgent(amazonClientProvider, credentials, region.name, amazonObjectMapper)
          newlyAddedAgents << new AmazonVpcCachingAgent(amazonClientProvider, credentials, region.name, amazonObjectMapper)

          // If there is an agent scheduler, then this provider has been through the AgentController in the past.
          // In that case, we need to do the scheduling here (because accounts have been added to a running system).
          if (awsInfrastructureProvider.agentScheduler) {
            ProviderUtils.rescheduleAgents(awsInfrastructureProvider, newlyAddedAgents)
          }

          awsInfrastructureProvider.agents.addAll(newlyAddedAgents)
        }
      }
    }
  }
}
