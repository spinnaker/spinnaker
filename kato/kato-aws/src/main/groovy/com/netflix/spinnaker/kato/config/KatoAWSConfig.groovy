/*
 * Copyright 2014 Netflix, Inc.
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

package com.netflix.spinnaker.kato.config

import com.netflix.spinnaker.cats.agent.Agent
import com.netflix.spinnaker.cats.provider.ProviderSynchronizerTypeWrapper
import com.netflix.spinnaker.clouddriver.aws.security.AmazonClientProvider
import com.netflix.spinnaker.clouddriver.aws.security.NetflixAmazonCredentials
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsRepository
import com.netflix.spinnaker.clouddriver.security.ProviderUtils
import com.netflix.spinnaker.kato.aws.agent.CleanupDetachedInstancesAgent
import com.netflix.spinnaker.kato.aws.deploy.handlers.BasicAmazonDeployHandler
import com.netflix.spinnaker.kato.aws.deploy.userdata.LocalFileUserDataProvider
import com.netflix.spinnaker.kato.aws.deploy.userdata.UserDataProvider
import com.netflix.spinnaker.kato.aws.provider.AwsCleanupProvider
import com.netflix.spinnaker.kato.aws.services.RegionScopedProviderFactory
import org.springframework.beans.factory.config.ConfigurableBeanFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.DependsOn
import org.springframework.context.annotation.Scope

import java.util.concurrent.ConcurrentHashMap

@ConditionalOnProperty('aws.enabled')
@ComponentScan('com.netflix.spinnaker.kato.aws')
@EnableConfigurationProperties
@Configuration
class KatoAWSConfig {

  @Bean
  @ConditionalOnMissingBean(UserDataProvider)
  UserDataProvider userDataProvider() {
    new LocalFileUserDataProvider()
  }

  @Bean
  @ConfigurationProperties('aws.defaults')
  DeployDefaults deployDefaults() {
    new DeployDefaults()
  }

  static class DeployDefaults {
    String iamRole
    String classicLinkSecurityGroupName
  }

  @Bean
  @DependsOn('netflixAmazonCredentials')
  BasicAmazonDeployHandler basicAmazonDeployHandler(RegionScopedProviderFactory regionScopedProviderFactory,
                                                    AccountCredentialsRepository accountCredentialsRepository,
                                                    DeployDefaults deployDefaults) {
    new BasicAmazonDeployHandler(regionScopedProviderFactory, accountCredentialsRepository, deployDefaults)
  }

  @Bean
  @DependsOn('netflixAmazonCredentials')
  AwsCleanupProvider awsOperationProvider(AmazonClientProvider amazonClientProvider,
                                          AccountCredentialsRepository accountCredentialsRepository) {
    def awsCleanupProvider = new AwsCleanupProvider(Collections.newSetFromMap(new ConcurrentHashMap<Agent, Boolean>()))

    synchronizeAwsCleanupProvider(awsCleanupProvider, amazonClientProvider, accountCredentialsRepository)

    awsCleanupProvider
  }

  @Bean
  AwsCleanupProviderSynchronizerTypeWrapper awsCleanupProviderSynchronizerTypeWrapper() {
    new AwsCleanupProviderSynchronizerTypeWrapper()
  }

  class AwsCleanupProviderSynchronizerTypeWrapper implements ProviderSynchronizerTypeWrapper {
    @Override
    Class getSynchronizerType() {
      return AwsCleanupProviderSynchronizer
    }
  }

  class AwsCleanupProviderSynchronizer {}

  @Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
  @Bean
  AwsCleanupProviderSynchronizer synchronizeAwsCleanupProvider(AwsCleanupProvider awsCleanupProvider,
                                                               AmazonClientProvider amazonClientProvider,
                                                               AccountCredentialsRepository accountCredentialsRepository) {
    def allAccounts = ProviderUtils.buildThreadSafeSetOfAccounts(accountCredentialsRepository, NetflixAmazonCredentials)

    if (awsCleanupProvider.agentScheduler) {
      // If there is an agent scheduler, then this provider has been through the AgentController in the past.
      synchronizeCleanupDetachedInstancesAgentAccounts(awsCleanupProvider, allAccounts)
    } else {
      awsCleanupProvider.agents.add(new CleanupDetachedInstancesAgent(amazonClientProvider, allAccounts))
    }

    new AwsCleanupProviderSynchronizer()
  }

  private void synchronizeCleanupDetachedInstancesAgentAccounts(AwsCleanupProvider awsCleanupProvider,
                                                                Collection<NetflixAmazonCredentials> allAccounts) {
    CleanupDetachedInstancesAgent cleanupDetachedInstancesAgent = awsCleanupProvider.agents.find { agent ->
      agent instanceof CleanupDetachedInstancesAgent
    }

    if (cleanupDetachedInstancesAgent) {
      def cleanupDetachedInstancesAccounts = cleanupDetachedInstancesAgent.accounts
      def oldAccountNames = cleanupDetachedInstancesAccounts.collect { it.name }
      def newAccountNames = allAccounts.collect { it.name }
      def accountNamesToDelete = oldAccountNames - newAccountNames
      def accountNamesToAdd = newAccountNames - oldAccountNames

      accountNamesToDelete.each { accountNameToDelete ->
        def accountToDelete = cleanupDetachedInstancesAccounts.find { it.name == accountNameToDelete }

        if (accountToDelete) {
          cleanupDetachedInstancesAccounts.remove(accountToDelete)
        }
      }

      accountNamesToAdd.each { accountNameToAdd ->
        def accountToAdd = allAccounts.find { it.name == accountNameToAdd }

        if (accountToAdd) {
          cleanupDetachedInstancesAccounts.add(accountToAdd)
        }
      }
    }
  }
}
