/*
 * Copyright 2015 Netflix, Inc.
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

package com.netflix.spinnaker.config

import com.amazonaws.retry.RetryPolicy.BackoffStrategy
import com.amazonaws.retry.RetryPolicy.RetryCondition
import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.awsobjectmapper.AmazonObjectMapperConfigurer
import com.netflix.spectator.api.Registry
import com.netflix.spinnaker.cats.agent.Agent
import com.netflix.spinnaker.cats.provider.ProviderSynchronizerTypeWrapper
import com.netflix.spinnaker.clouddriver.aws.AwsConfigurationProperties
import com.netflix.spinnaker.clouddriver.aws.agent.CleanupAlarmsAgent
import com.netflix.spinnaker.clouddriver.aws.agent.CleanupDetachedInstancesAgent
import com.netflix.spinnaker.clouddriver.aws.agent.ReconcileClassicLinkSecurityGroupsAgent
import com.netflix.spinnaker.clouddriver.aws.bastion.BastionConfig
import com.netflix.spinnaker.clouddriver.aws.deploy.BlockDeviceConfig
import com.netflix.spinnaker.clouddriver.aws.deploy.handlers.BasicAmazonDeployHandler
import com.netflix.spinnaker.clouddriver.aws.deploy.ops.securitygroup.SecurityGroupLookupFactory
import com.netflix.spinnaker.clouddriver.aws.deploy.scalingpolicy.DefaultScalingPolicyCopier
import com.netflix.spinnaker.clouddriver.aws.deploy.scalingpolicy.ScalingPolicyCopier
import com.netflix.spinnaker.clouddriver.aws.deploy.userdata.LocalFileUserDataProvider
import com.netflix.spinnaker.clouddriver.aws.deploy.userdata.NullOpUserDataProvider
import com.netflix.spinnaker.clouddriver.aws.deploy.userdata.UserDataProvider
import com.netflix.spinnaker.clouddriver.aws.event.AfterResizeEventHandler
import com.netflix.spinnaker.clouddriver.aws.event.DefaultAfterResizeEventHandler
import com.netflix.spinnaker.clouddriver.aws.model.AmazonBlockDevice
import com.netflix.spinnaker.clouddriver.aws.model.AmazonServerGroup
import com.netflix.spinnaker.clouddriver.aws.provider.AwsCleanupProvider
import com.netflix.spinnaker.clouddriver.aws.provider.view.AmazonClusterProvider
import com.netflix.spinnaker.clouddriver.aws.security.*
import com.netflix.spinnaker.clouddriver.aws.security.EddaTimeoutConfig.Builder
import com.netflix.spinnaker.clouddriver.aws.services.IdGenerator
import com.netflix.spinnaker.clouddriver.aws.services.RegionScopedProviderFactory
import com.netflix.spinnaker.clouddriver.core.limits.ServiceLimitConfiguration
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsRepository
import com.netflix.spinnaker.clouddriver.security.ProviderUtils
import com.netflix.spinnaker.kork.aws.AwsComponents
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.config.ConfigurableBeanFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.ApplicationContext
import org.springframework.context.annotation.*

import java.util.concurrent.ConcurrentHashMap

@Configuration
@ConditionalOnProperty('aws.enabled')
@ComponentScan(["com.netflix.spinnaker.clouddriver.aws"])
@EnableConfigurationProperties(AwsConfigurationProperties)
@Import([
  BastionConfig,
  AmazonCredentialsInitializer,
  AwsComponents
])
class AwsConfiguration {

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
  AmazonClientProvider amazonClientProvider(AwsConfigurationProperties awsConfigurationProperties, RetryCondition instrumentedRetryCondition, BackoffStrategy instrumentedBackoffStrategy, AWSProxy proxy, EddaTimeoutConfig eddaTimeoutConfig, ServiceLimitConfiguration serviceLimitConfiguration, Registry registry) {
    new AmazonClientProvider.Builder()
      .backoffStrategy(instrumentedBackoffStrategy)
      .retryCondition(instrumentedRetryCondition)
      .objectMapper(amazonObjectMapper())
      .maxErrorRetry(awsConfigurationProperties.client.maxErrorRetry)
      .maxConnections(awsConfigurationProperties.client.maxConnections)
      .maxConnectionsPerRoute(awsConfigurationProperties.client.maxConnectionsPerRoute)
      .proxy(proxy)
      .eddaTimeoutConfig(eddaTimeoutConfig)
      .useGzip(awsConfigurationProperties.client.useGzip)
      .serviceLimitConfiguration(serviceLimitConfiguration)
      .registry(registry)
      .addSpinnakerUserToUserAgent(awsConfigurationProperties.client.addSpinnakerUserToUserAgent)
      .build()
  }

  @Bean
  @Qualifier("amazonObjectMapper")
  ObjectMapper amazonObjectMapper() {
    return new AmazonObjectMapperConfigurer().createConfigured()
  }

  @Bean
  @ConditionalOnProperty(value = 'udf.enabled', matchIfMissing = true)
  UserDataProvider userDataProvider() {
    new LocalFileUserDataProvider()
  }

  @Bean
  @ConditionalOnMissingBean(ScalingPolicyCopier)
  DefaultScalingPolicyCopier defaultScalingPolicyCopier(AmazonClientProvider amazonClientProvider, IdGenerator idGenerator) {
    new DefaultScalingPolicyCopier(amazonClientProvider, idGenerator)
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

  public static class DeployDefaults {
    public static enum ReconcileMode {
      NONE,
      LOG,
      MODIFY
    }

    public static class LoadBalancerDefaults {
      Boolean crossZoneBalancingDefault = true
      Boolean connectionDrainingDefault = false
      Integer deregistrationDelayDefault = null
      Integer idleTimeout = 60
      Boolean deletionProtection = false
    }
    String iamRole
    String classicLinkSecurityGroupName
    boolean addAppGroupsToClassicLink = false
    int maxClassicLinkSecurityGroups = 5
    boolean addAppGroupToServerGroup = false
    boolean addAppStackDetailTags = false
    boolean createLoadBalancerIngressPermissions = false
    int maxSecurityGroups = 5
    ReconcileMode reconcileClassicLinkSecurityGroups = ReconcileMode.NONE
    List<String> reconcileClassicLinkAccounts = []
    String defaultBlockDeviceType = "standard"
    LoadBalancerDefaults loadBalancing = new LoadBalancerDefaults()
    AmazonBlockDevice unknownInstanceTypeBlockDevice = new AmazonBlockDevice(
      deviceName: "/dev/sdb", size: 20, volumeType: defaultBlockDeviceType
    )

    boolean isReconcileClassicLinkAccount(NetflixAmazonCredentials credentials) {
      if (reconcileClassicLinkSecurityGroups == ReconcileMode.NONE) {
        return false
      }
      List<String> reconcileAccounts = reconcileClassicLinkAccounts ?: []
      return reconcileAccounts.isEmpty() || reconcileAccounts.contains(credentials.getName())
    }

  }

  @Bean
  @DependsOn('netflixAmazonCredentials')
  BasicAmazonDeployHandler basicAmazonDeployHandler(RegionScopedProviderFactory regionScopedProviderFactory,
                                                    AccountCredentialsRepository accountCredentialsRepository,
                                                    DeployDefaults deployDefaults,
                                                    ScalingPolicyCopier scalingPolicyCopier,
                                                    BlockDeviceConfig blockDeviceConfig,
                                                    AmazonServerGroupProvider amazonServerGroupProvider) {
    new BasicAmazonDeployHandler(
      regionScopedProviderFactory,
      accountCredentialsRepository,
      amazonServerGroupProvider,
      deployDefaults,
      scalingPolicyCopier,
      blockDeviceConfig
    )
  }

  @Bean
  @DependsOn('deployDefaults')
  BlockDeviceConfig blockDeviceConfig(DeployDefaults deployDefaults) {
    new BlockDeviceConfig(deployDefaults)
  }

  @Bean
  @DependsOn('netflixAmazonCredentials')
  AwsCleanupProvider awsOperationProvider(AwsConfigurationProperties awsConfigurationProperties,
                                          AmazonClientProvider amazonClientProvider,
                                          AccountCredentialsRepository accountCredentialsRepository,
                                          DeployDefaults deployDefaults) {
    def awsCleanupProvider = new AwsCleanupProvider(Collections.newSetFromMap(new ConcurrentHashMap<Agent, Boolean>()))

    synchronizeAwsCleanupProvider(awsConfigurationProperties, awsCleanupProvider, amazonClientProvider, accountCredentialsRepository, deployDefaults)

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
  AwsCleanupProviderSynchronizer synchronizeAwsCleanupProvider(AwsConfigurationProperties awsConfigurationProperties,
                                                               AwsCleanupProvider awsCleanupProvider,
                                                               AmazonClientProvider amazonClientProvider,
                                                               AccountCredentialsRepository accountCredentialsRepository,
                                                               DeployDefaults deployDefaults) {
    def scheduledAccounts = ProviderUtils.getScheduledAccounts(awsCleanupProvider)
    Set<NetflixAmazonCredentials> allAccounts = ProviderUtils.buildThreadSafeSetOfAccounts(accountCredentialsRepository, NetflixAmazonCredentials)

    List<Agent> newlyAddedAgents = []

    allAccounts.each { account ->
      if (!scheduledAccounts.contains(account)) {
        account.regions.each { region ->
          if (deployDefaults.isReconcileClassicLinkAccount(account)) {
            newlyAddedAgents << new ReconcileClassicLinkSecurityGroupsAgent(
              amazonClientProvider, account, region.name, deployDefaults
            )
          }
        }
      }
    }

    if (!awsCleanupProvider.agentScheduler) {
      if (awsConfigurationProperties.cleanup.alarms.enabled) {
        awsCleanupProvider.agents.add(new CleanupAlarmsAgent(amazonClientProvider, accountCredentialsRepository, awsConfigurationProperties.cleanup.alarms.daysToKeep))
      }
      awsCleanupProvider.agents.add(new CleanupDetachedInstancesAgent(amazonClientProvider, accountCredentialsRepository))
    }
    awsCleanupProvider.agents.addAll(newlyAddedAgents)

    new AwsCleanupProviderSynchronizer()
  }

  @Bean
  AmazonServerGroupProvider amazonServerGroupProvider(ApplicationContext applicationContext) {
    return new AmazonServerGroupProvider(applicationContext)
  }

  @Bean
  @ConditionalOnMissingBean(AfterResizeEventHandler)
  DefaultAfterResizeEventHandler defaultAfterResizeEventHandler() {
    return new DefaultAfterResizeEventHandler();
  }

  class AmazonServerGroupProvider {
    ApplicationContext applicationContext

    AmazonServerGroupProvider(ApplicationContext applicationContext) {
      this.applicationContext = applicationContext
    }

    AmazonServerGroup getServerGroup(String account, String region, String serverGroupName) {
      return applicationContext.getBean(AmazonClusterProvider).getServerGroup(account, region, serverGroupName)
    }
  }
}
