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
import com.amazonaws.retry.RetryPolicy.BackoffStrategy
import com.amazonaws.retry.RetryPolicy.RetryCondition
import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.awsobjectmapper.AmazonObjectMapperConfigurer
import com.netflix.spectator.aws.SpectatorMetricCollector
import com.netflix.spinnaker.cats.agent.Agent
import com.netflix.spinnaker.cats.provider.ProviderSynchronizerTypeWrapper
import com.netflix.spinnaker.clouddriver.aws.agent.CleanupAlarmsAgent
import com.netflix.spinnaker.clouddriver.aws.agent.CleanupDetachedInstancesAgent
import com.netflix.spinnaker.clouddriver.aws.agent.ReconcileClassicLinkSecurityGroupsAgent
import com.netflix.spinnaker.clouddriver.aws.bastion.BastionConfig
import com.netflix.spinnaker.clouddriver.aws.deploy.converters.AllowLaunchAtomicOperationConverter
import com.netflix.spinnaker.clouddriver.aws.deploy.handlers.BasicAmazonDeployHandler
import com.netflix.spinnaker.clouddriver.aws.deploy.handlers.DefaultMigrateClusterConfigurationStrategy
import com.netflix.spinnaker.clouddriver.aws.deploy.handlers.DefaultMigrateLoadBalancerStrategy
import com.netflix.spinnaker.clouddriver.aws.deploy.handlers.DefaultMigrateSecurityGroupStrategy
import com.netflix.spinnaker.clouddriver.aws.deploy.handlers.DefaultMigrateServerGroupStrategy
import com.netflix.spinnaker.clouddriver.aws.deploy.handlers.MigrateClusterConfigurationStrategy
import com.netflix.spinnaker.clouddriver.aws.deploy.handlers.MigrateLoadBalancerStrategy
import com.netflix.spinnaker.clouddriver.aws.deploy.handlers.MigrateSecurityGroupStrategy
import com.netflix.spinnaker.clouddriver.aws.deploy.handlers.MigrateServerGroupStrategy
import com.netflix.spinnaker.clouddriver.aws.deploy.ops.securitygroup.SecurityGroupLookupFactory
import com.netflix.spinnaker.clouddriver.aws.deploy.userdata.LocalFileUserDataProvider
import com.netflix.spinnaker.clouddriver.aws.deploy.userdata.NullOpUserDataProvider
import com.netflix.spinnaker.clouddriver.aws.deploy.userdata.UserDataProvider
import com.netflix.spinnaker.clouddriver.aws.deploy.validators.BasicAmazonDeployDescriptionValidator
import com.netflix.spinnaker.clouddriver.aws.provider.AwsCleanupProvider
import com.netflix.spinnaker.clouddriver.aws.security.AWSProxy
import com.netflix.spinnaker.clouddriver.aws.security.AmazonClientProvider
import com.netflix.spinnaker.clouddriver.aws.security.AmazonCredentialsInitializer
import com.netflix.spinnaker.clouddriver.aws.security.EddaTimeoutConfig
import com.netflix.spinnaker.clouddriver.aws.security.EddaTimeoutConfig.Builder
import com.netflix.spinnaker.clouddriver.aws.security.NetflixAmazonCredentials
import com.netflix.spinnaker.clouddriver.aws.services.RegionScopedProviderFactory
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsRepository
import com.netflix.spinnaker.clouddriver.security.ProviderUtils
import com.netflix.spinnaker.kork.aws.AwsComponents
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
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

  @Value('${aws.cleanup.alarms.enabled:false}')
  boolean alarmCleanupEnabled

  @Value('${aws.cleanup.alarms.daysToKeep:90}')
  int daysToKeepAlarms

  @Value('${aws.migration.infrastructureApplications:[]}')
  List<String> infrastructureApplications

  @Value('${aws.client.useGzip:true}')
  boolean awsClientUseGzip

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
  Builder eddaTimeoutConfigBuilder() {
    return new Builder()
  }

  @Bean
  EddaTimeoutConfig eddaTimeoutConfig(Builder eddaTimeoutConfigBuilder) {
    eddaTimeoutConfigBuilder.build()
  }

  @Bean
  AmazonClientProvider amazonClientProvider(RetryCondition instrumentedRetryCondition, BackoffStrategy instrumentedBackoffStrategy, AWSProxy proxy, EddaTimeoutConfig eddaTimeoutConfig) {
    new AmazonClientProvider.Builder()
      .backoffStrategy(instrumentedBackoffStrategy)
      .retryCondition(instrumentedRetryCondition)
      .objectMapper(amazonObjectMapper())
      .maxErrorRetry(maxErrorRetry)
      .maxConnections(maxConnections)
      .maxConnectionsPerRoute(maxConnectionsPerRoute)
      .proxy(proxy)
      .eddaTimeoutConfig(eddaTimeoutConfig)
      .useGzip(awsClientUseGzip)
      .build()
  }

  @Bean
  @Qualifier("amazon")
  ObjectMapper amazonObjectMapper() {
    AmazonObjectMapperConfigurer.createConfigured()
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
  @ConditionalOnMissingBean
  @Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
  MigrateSecurityGroupStrategy migrateSecurityGroupStrategy(AmazonClientProvider amazonClientProvider) {
    new DefaultMigrateSecurityGroupStrategy(amazonClientProvider, infrastructureApplications)
  }

  @Bean
  @ConditionalOnMissingBean
  @Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
  MigrateLoadBalancerStrategy migrateLoadBalancerStrategy(AmazonClientProvider amazonClientProvider,
                                                          RegionScopedProviderFactory regionScopedProviderFactory,
                                                          DeployDefaults deployDefaults) {
    new DefaultMigrateLoadBalancerStrategy(amazonClientProvider, regionScopedProviderFactory, deployDefaults)
  }

  @Bean
  @ConditionalOnMissingBean
  @Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
  MigrateServerGroupStrategy migrateServerGroupStrategy(AmazonClientProvider amazonClientProvider,
                                                        BasicAmazonDeployHandler basicAmazonDeployHandler,
                                                        RegionScopedProviderFactory regionScopedProviderFactory,
                                                        BasicAmazonDeployDescriptionValidator basicAmazonDeployDescriptionValidator,
                                                        AllowLaunchAtomicOperationConverter allowLaunchAtomicOperationConverter,
                                                        DeployDefaults deployDefaults) {
    new DefaultMigrateServerGroupStrategy(amazonClientProvider, basicAmazonDeployHandler,
      regionScopedProviderFactory, basicAmazonDeployDescriptionValidator, allowLaunchAtomicOperationConverter,
      deployDefaults)
  }

  @Bean
  @ConditionalOnMissingBean
  @Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
  MigrateClusterConfigurationStrategy migrateClusterConfigurationStrategy(AmazonClientProvider amazonClientProvider,
                                                                          RegionScopedProviderFactory regionScopedProviderFactory,
                                                                          DeployDefaults deployDefaults) {
    new DefaultMigrateClusterConfigurationStrategy(amazonClientProvider, regionScopedProviderFactory, deployDefaults)
  }

  @Bean
  @ConfigurationProperties('aws.defaults')
  DeployDefaults deployDefaults() {
    new DeployDefaults()
  }

  public static class DeployDefaults {
    public static enum ReconcileMode {
      NONE,
      LOG,
      MODIFY
    }
    String iamRole
    String classicLinkSecurityGroupName
    boolean addAppGroupsToClassicLink = false
    int maxClassicLinkSecurityGroups = 5
    boolean addAppGroupToServerGroup = false
    int maxSecurityGroups = 5
    ReconcileMode reconcileClassicLinkSecurityGroups = ReconcileMode.NONE
    List<String> reconcileClassicLinkAccounts = []

    boolean isReconcileClassicLinkAccount(NetflixAmazonCredentials credentials) {
      if (reconcileClassicLinkSecurityGroups == ReconcileMode.NONE) {
        return false
      }
      List<String> reconcileAccounts = reconcileClassicLinkAccounts ?: []
      return reconcileAccounts.isEmpty() || reconcileAccounts.contains(credentials.getName());
    }

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
                                          AccountCredentialsRepository accountCredentialsRepository,
                                          DeployDefaults deployDefaults) {
    def awsCleanupProvider = new AwsCleanupProvider(Collections.newSetFromMap(new ConcurrentHashMap<Agent, Boolean>()))

    synchronizeAwsCleanupProvider(awsCleanupProvider, amazonClientProvider, accountCredentialsRepository, deployDefaults)

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
                                                               AccountCredentialsRepository accountCredentialsRepository,
                                                               DeployDefaults deployDefaults) {
    def scheduledAccounts = ProviderUtils.getScheduledAccounts(awsCleanupProvider)
    Set<NetflixAmazonCredentials> allAccounts = ProviderUtils.buildThreadSafeSetOfAccounts(accountCredentialsRepository, NetflixAmazonCredentials)

    List<Agent> newlyAddedAgents = []

    allAccounts.each { account ->
      if (!scheduledAccounts.contains(account)) {
        account.regions.each { region ->
          newlyAddedAgents << new ReconcileClassicLinkSecurityGroupsAgent(amazonClientProvider, account, region.name, deployDefaults)
        }
      }
    }

    if (!awsCleanupProvider.agentScheduler) {
      if (alarmCleanupEnabled) {
        awsCleanupProvider.agents.add(new CleanupAlarmsAgent(amazonClientProvider, accountCredentialsRepository, daysToKeepAlarms))
      }
      awsCleanupProvider.agents.add(new CleanupDetachedInstancesAgent(amazonClientProvider, accountCredentialsRepository))
    }
    awsCleanupProvider.agents.addAll(newlyAddedAgents)

    new AwsCleanupProviderSynchronizer()
  }
}
