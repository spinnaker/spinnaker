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

package com.netflix.spinnaker.clouddriver.aws

import com.amazonaws.metrics.AwsSdkMetrics
import com.amazonaws.retry.RetryPolicy
import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.awsobjectmapper.AmazonObjectMapper
import com.netflix.spectator.aws.SpectatorMetricCollector
import com.netflix.spinnaker.cats.agent.Agent
import com.netflix.spinnaker.cats.provider.ProviderSynchronizerTypeWrapper
import com.netflix.spinnaker.clouddriver.aws.agent.CleanupDetachedInstancesAgent
import com.netflix.spinnaker.clouddriver.aws.bastion.BastionConfig
import com.netflix.spinnaker.clouddriver.aws.deploy.handlers.BasicAmazonDeployHandler
import com.netflix.spinnaker.clouddriver.aws.deploy.userdata.LocalFileUserDataProvider
import com.netflix.spinnaker.clouddriver.aws.deploy.userdata.NullOpUserDataProvider
import com.netflix.spinnaker.clouddriver.aws.deploy.userdata.UserDataProvider
import com.netflix.spinnaker.clouddriver.aws.model.SecurityGroupLookupFactory
import com.netflix.spinnaker.clouddriver.aws.provider.AwsCleanupProvider
import com.netflix.spinnaker.clouddriver.aws.security.AWSProxy
import com.netflix.spinnaker.clouddriver.aws.security.EddaTimeoutConfig
import com.netflix.spinnaker.clouddriver.aws.security.AmazonClientProvider
import com.netflix.spinnaker.clouddriver.aws.security.AmazonCredentialsInitializer
import com.netflix.spinnaker.clouddriver.aws.security.NetflixAmazonCredentials
import com.netflix.spinnaker.clouddriver.aws.services.RegionScopedProviderFactory
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsRepository
import com.netflix.spinnaker.clouddriver.security.ProviderUtils
import com.netflix.spinnaker.kork.aws.AwsComponents
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.beans.factory.config.ConfigurableBeanFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.DependsOn
import org.springframework.context.annotation.Import
import org.springframework.context.annotation.Scope

import javax.annotation.PostConstruct
import java.util.concurrent.ConcurrentHashMap

@Configuration
@ConditionalOnProperty('aws.enabled')
@ComponentScan(["com.netflix.spinnaker.clouddriver.aws"])
@EnableConfigurationProperties
@Import([
  BastionConfig,
  AmazonCredentialsInitializer,
  AwsComponents
])
class AwsConfiguration {
  @Value('${aws.metrics.enabled:false}')
  boolean metricsEnabled

  @Value('${aws.client.maxErrorRetry:3}')
  int maxErrorRetry

  @Value('${aws.client.maxConnections:200}')
  int maxConnections

  @Value('${aws.client.maxConnectionsPerRoute:20}')
  int maxConnectionsPerRoute

  @Autowired
  SpectatorMetricCollector spectatorMetricCollector

  @PostConstruct
  void checkMetricsEnabled() {
    if (!metricsEnabled) {
      AwsSdkMetrics.setMetricCollector(null)
    }
  }

  @Bean
  @ConfigurationProperties('aws.edda')
  EddaTimeoutConfig.Builder eddaTimeoutConfigBuilder() {
    new EddaTimeoutConfig.Builder()
  }

  @Bean
  EddaTimeoutConfig eddaTimeoutConfig(EddaTimeoutConfig.Builder eddaTimeoutConfigBuilder) {
    eddaTimeoutConfigBuilder.build()
  }

  @Bean
  AmazonClientProvider amazonClientProvider(RetryPolicy.RetryCondition instrumentedRetryCondition, RetryPolicy.BackoffStrategy instrumentedBackoffStrategy, AWSProxy proxy, EddaTimeoutConfig eddaTimeoutConfig) {
    new AmazonClientProvider.Builder()
      .backoffStrategy(instrumentedBackoffStrategy)
      .retryCondition(instrumentedRetryCondition)
      .objectMapper(amazonObjectMapper())
      .maxErrorRetry(maxErrorRetry)
      .maxConnections(maxConnections)
      .maxConnectionsPerRoute(maxConnectionsPerRoute)
      .proxy(proxy)
      .eddaTimeoutConfig(eddaTimeoutConfig)
      .build()
  }

  @Bean
  ObjectMapper amazonObjectMapper() {
    new AmazonObjectMapper()
  }

  @Bean
  @ConditionalOnProperty(value = 'udf.enabled', matchIfMissing = true)
  UserDataProvider userDataProvider() {
    new LocalFileUserDataProvider()
  }

  @Bean
  @ConditionalOnMissingBean(UserDataProvider)
  NullOpUserDataProvider nullOpUserDataProvider() {
    new NullOpUserDataProvider()
  }

  @Bean
  @ConfigurationProperties('aws.defaults')
  DeployDefaults deployDefaults() {
    new DeployDefaults()
  }

  static class DeployDefaults {
    String iamRole
    String classicLinkSecurityGroupName
    boolean addAppGroupsToClassicLink = false
    int maxClassicLinkSecurityGroups = 5
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
  @DependsOn('netflixAmazonCredentials')
  SecurityGroupLookupFactory securityGroupLookup(AmazonClientProvider amazonClientProvider,
                                          AccountCredentialsRepository accountCredentialsRepository) {
    new SecurityGroupLookupFactory(amazonClientProvider, accountCredentialsRepository)
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
