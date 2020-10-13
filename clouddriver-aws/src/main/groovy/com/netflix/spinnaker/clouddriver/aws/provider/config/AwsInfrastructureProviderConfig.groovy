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
import com.netflix.spinnaker.clouddriver.aws.AmazonCloudProvider
import com.netflix.spinnaker.clouddriver.aws.provider.AwsInfrastructureProvider
import com.netflix.spinnaker.clouddriver.aws.security.AmazonClientProvider
import com.netflix.spinnaker.clouddriver.aws.security.EddaTimeoutConfig
import com.netflix.spinnaker.clouddriver.aws.security.NetflixAmazonCredentials
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsRepository
import com.netflix.spinnaker.clouddriver.security.ProviderUtils
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.DependsOn

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
      new AwsInfrastructureProvider()

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
    def allAccounts = ProviderUtils.buildThreadSafeSetOfAccounts(accountCredentialsRepository, NetflixAmazonCredentials, AmazonCloudProvider.ID)

    Set<String> regions = new HashSet<>()
    def newlyAddedAgents = []
    allAccounts.each { NetflixAmazonCredentials credentials ->
      def result = ProviderHelpers.buildAwsInfrastructureAgents(credentials, awsInfrastructureProvider, accountCredentialsRepository,
        amazonClientProvider, amazonObjectMapper, registry, eddaTimeoutConfig, regions)
      regions.addAll(result.getRegionsToAdd())
      newlyAddedAgents.addAll(result.getAgents())
    }

    // If there is an agent scheduler, then this provider has been through the AgentController in the past.
    // In that case, we need to do the scheduling here (because accounts have been added to a running system).
    if (awsInfrastructureProvider.agentScheduler) {
      ProviderUtils.rescheduleAgents(awsInfrastructureProvider, newlyAddedAgents)
    }
    awsInfrastructureProvider.addAgents(newlyAddedAgents)
  }
}
