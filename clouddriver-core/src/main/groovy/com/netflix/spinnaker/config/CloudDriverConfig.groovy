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

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.cats.agent.ExecutionInstrumentation
import com.netflix.spinnaker.cats.agent.NoopExecutionInstrumentation
import com.netflix.spinnaker.cats.redis.cache.RedisCacheOptions
import com.netflix.spinnaker.clouddriver.cache.CacheConfig
import com.netflix.spinnaker.clouddriver.cache.NoopOnDemandCacheUpdater
import com.netflix.spinnaker.clouddriver.cache.OnDemandCacheUpdater
import com.netflix.spinnaker.clouddriver.core.CloudProvider
import com.netflix.spinnaker.clouddriver.core.DynomiteConfig
import com.netflix.spinnaker.clouddriver.core.NoopAtomicOperationConverter
import com.netflix.spinnaker.clouddriver.core.NoopCloudProvider
import com.netflix.spinnaker.clouddriver.core.ProjectClustersService
import com.netflix.spinnaker.clouddriver.core.RedisConfig
import com.netflix.spinnaker.clouddriver.core.agent.CleanupPendingOnDemandCachesAgent
import com.netflix.spinnaker.clouddriver.core.agent.ProjectClustersCachingAgent
import com.netflix.spinnaker.clouddriver.core.limits.ServiceLimitConfiguration
import com.netflix.spinnaker.clouddriver.core.limits.ServiceLimitConfigurationBuilder
import com.netflix.spinnaker.clouddriver.core.provider.CoreProvider
import com.netflix.spinnaker.clouddriver.core.services.Front50Service
import com.netflix.spinnaker.clouddriver.model.ApplicationProvider
import com.netflix.spinnaker.clouddriver.model.CloudMetricProvider
import com.netflix.spinnaker.clouddriver.model.ClusterProvider
import com.netflix.spinnaker.clouddriver.model.ElasticIpProvider
import com.netflix.spinnaker.clouddriver.model.ImageProvider
import com.netflix.spinnaker.clouddriver.model.InstanceProvider
import com.netflix.spinnaker.clouddriver.model.InstanceTypeProvider
import com.netflix.spinnaker.clouddriver.model.KeyPairProvider
import com.netflix.spinnaker.clouddriver.model.LoadBalancerProvider
import com.netflix.spinnaker.clouddriver.model.ManifestProvider
import com.netflix.spinnaker.clouddriver.model.NetworkProvider
import com.netflix.spinnaker.clouddriver.model.NoopApplicationProvider
import com.netflix.spinnaker.clouddriver.model.NoopCloudMetricProvider
import com.netflix.spinnaker.clouddriver.model.NoopClusterProvider
import com.netflix.spinnaker.clouddriver.model.NoopElasticIpProvider
import com.netflix.spinnaker.clouddriver.model.NoopImageProvider
import com.netflix.spinnaker.clouddriver.model.NoopInstanceProvider
import com.netflix.spinnaker.clouddriver.model.NoopInstanceTypeProvider
import com.netflix.spinnaker.clouddriver.model.NoopKeyPairProvider
import com.netflix.spinnaker.clouddriver.model.NoopLoadBalancerProvider
import com.netflix.spinnaker.clouddriver.model.NoopManifestProvider
import com.netflix.spinnaker.clouddriver.model.NoopNetworkProvider
import com.netflix.spinnaker.clouddriver.model.NoopReservationReportProvider
import com.netflix.spinnaker.clouddriver.model.NoopSecurityGroupProvider
import com.netflix.spinnaker.clouddriver.model.NoopServerGroupManagerProvider
import com.netflix.spinnaker.clouddriver.model.NoopSubnetProvider
import com.netflix.spinnaker.clouddriver.model.ReservationReportProvider
import com.netflix.spinnaker.clouddriver.model.SecurityGroupProvider
import com.netflix.spinnaker.clouddriver.model.ServerGroupManager
import com.netflix.spinnaker.clouddriver.model.ServerGroupManagerProvider
import com.netflix.spinnaker.clouddriver.model.SubnetProvider
import com.netflix.spinnaker.clouddriver.names.NamerRegistry
import com.netflix.spinnaker.clouddriver.names.NamingStrategy
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperationConverter
import com.netflix.spinnaker.clouddriver.search.ApplicationSearchProvider
import com.netflix.spinnaker.clouddriver.search.NoopSearchProvider
import com.netflix.spinnaker.clouddriver.search.ProjectSearchProvider
import com.netflix.spinnaker.clouddriver.search.SearchProvider
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsProvider
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsRepository
import com.netflix.spinnaker.clouddriver.security.DefaultAccountCredentialsProvider
import com.netflix.spinnaker.clouddriver.security.MapBackedAccountCredentialsRepository
import com.netflix.spinnaker.kork.core.RetrySupport
import com.netflix.spinnaker.kork.jedis.RedisClientDelegate
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.ConfigurationPropertiesBinding
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.cglib.beans.BeanMap
import org.springframework.context.ApplicationContext
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import
import org.springframework.context.annotation.PropertySource
import org.springframework.core.env.Environment
import org.springframework.web.client.RestTemplate

import javax.inject.Provider
import java.time.Clock

@Configuration
@Import([
  RedisConfig,
  DynomiteConfig,
  CacheConfig
])
@PropertySource(value = "classpath:META-INF/clouddriver-core.properties", ignoreResourceNotFound = true)
@EnableConfigurationProperties(ProjectClustersCachingAgentProperties)
class CloudDriverConfig {

  @Bean
  @ConditionalOnMissingBean(Clock)
  Clock clock() {
    Clock.systemDefaultZone()
  }

  @Bean
  String clouddriverUserAgentApplicationName(Environment environment) {
    return "Spinnaker/${environment.getProperty("Implementation-Version", "Unknown")}"
  }

  @Bean
  @ConfigurationProperties('serviceLimits')
  ServiceLimitConfigurationBuilder serviceLimitConfigProperties() {
    return new ServiceLimitConfigurationBuilder()
  }

  @Bean
  ServiceLimitConfiguration serviceLimitConfiguration(ServiceLimitConfigurationBuilder serviceLimitConfigProperties) {
    return serviceLimitConfigProperties.build()
  }

  @Bean
  @ConditionalOnMissingBean(AccountCredentialsRepository)
  AccountCredentialsRepository accountCredentialsRepository() {
    new MapBackedAccountCredentialsRepository()
  }

  @Bean
  @ConditionalOnMissingBean(AccountCredentialsProvider)
  AccountCredentialsProvider accountCredentialsProvider(AccountCredentialsRepository accountCredentialsRepository) {
    new DefaultAccountCredentialsProvider(accountCredentialsRepository)
  }

  @Bean
  RestTemplate restTemplate() {
    new RestTemplate()
  }

  @Bean
  @ConditionalOnMissingBean(OnDemandCacheUpdater)
  NoopOnDemandCacheUpdater noopOnDemandCacheUpdater() {
    new NoopOnDemandCacheUpdater()
  }

  @Bean
  @ConditionalOnMissingBean(SearchProvider)
  NoopSearchProvider noopSearchProvider() {
    new NoopSearchProvider()
  }

  @Bean
  @ConditionalOnExpression('${services.front50.enabled:true}')
  ApplicationSearchProvider applicationSearchProvider(Front50Service front50Service) {
    new ApplicationSearchProvider(front50Service)
  }

  @Bean
  @ConditionalOnExpression('${services.front50.enabled:true}')
  ProjectSearchProvider projectSearchProvider(Front50Service front50Service) {
    new ProjectSearchProvider(front50Service)
  }

  @Bean
  @ConditionalOnMissingBean(CloudProvider)
  CloudProvider noopCloudProvider() {
    new NoopCloudProvider()
  }

  @Bean
  @ConditionalOnMissingBean(CloudMetricProvider)
  CloudMetricProvider noopCloudMetricProvider() {
    new NoopCloudMetricProvider()
  }

  @Bean
  @ConditionalOnMissingBean(ApplicationProvider)
  ApplicationProvider noopApplicationProvider() {
    new NoopApplicationProvider()
  }

  @Bean
  @ConditionalOnMissingBean(LoadBalancerProvider)
  LoadBalancerProvider noopLoadBalancerProvider() {
    new NoopLoadBalancerProvider()
  }

  @Bean
  @ConditionalOnMissingBean(ManifestProvider)
  ManifestProvider noopManifestProvider() {
    new NoopManifestProvider()
  }

  @Bean
  @ConditionalOnMissingBean(ClusterProvider)
  ClusterProvider noopClusterProvider() {
    new NoopClusterProvider()
  }

  @Bean
  @ConditionalOnMissingBean(ReservationReportProvider)
  ReservationReportProvider noopReservationReportProvider() {
    new NoopReservationReportProvider()
  }

  @Bean
  @ConditionalOnMissingBean(ExecutionInstrumentation)
  ExecutionInstrumentation noopExecutionInstrumentation() {
    new NoopExecutionInstrumentation()
  }

  @Bean
  @ConditionalOnMissingBean(InstanceProvider)
  InstanceProvider noopInstanceProvider() {
    new NoopInstanceProvider()
  }

  @Bean
  @ConditionalOnMissingBean(ImageProvider)
  ImageProvider noopImageProvider() {
    new NoopImageProvider()
  }

  @Bean
  @ConditionalOnMissingBean(InstanceTypeProvider)
  InstanceTypeProvider noopInstanceTypeProvider() {
    new NoopInstanceTypeProvider()
  }

  @Bean
  @ConditionalOnMissingBean(KeyPairProvider)
  KeyPairProvider noopKeyPairProvider() {
    new NoopKeyPairProvider()
  }

  @Bean
  @ConditionalOnMissingBean(SecurityGroupProvider)
  SecurityGroupProvider noopSecurityGroupProvider() {
    new NoopSecurityGroupProvider()
  }

  @Bean
  @ConditionalOnMissingBean(ServerGroupManager)
  ServerGroupManagerProvider noopServerGroupManagerProvider() {
    new NoopServerGroupManagerProvider()
  }

  @Bean
  @ConditionalOnMissingBean(SubnetProvider)
  SubnetProvider noopSubnetProvider() {
    new NoopSubnetProvider()
  }

  @Bean
  @ConditionalOnMissingBean(NetworkProvider)
  NetworkProvider noopVpcProvider() {
    new NoopNetworkProvider()
  }

  @Bean
  @ConditionalOnMissingBean(ElasticIpProvider)
  ElasticIpProvider noopElasticIpProvider() {
    new NoopElasticIpProvider()
  }

  @Bean
  ProjectClustersService projectClustersService(Front50Service front50Service,
                                                ObjectMapper objectMapper,
                                                Provider<List<ClusterProvider>> clusterProviders) {
    return new ProjectClustersService(front50Service, objectMapper, clusterProviders)
  }

  @Bean
  CoreProvider coreProvider(RedisCacheOptions redisCacheOptions,
                            RedisClientDelegate redisClientDelegate,
                            ApplicationContext applicationContext,
                            ProjectClustersService projectClustersService,
                            ProjectClustersCachingAgentProperties projectClustersCachingAgentProperties) {
    return new CoreProvider([
      new CleanupPendingOnDemandCachesAgent(redisCacheOptions, redisClientDelegate, applicationContext),
      new ProjectClustersCachingAgent(
        projectClustersService,
        projectClustersCachingAgentProperties
      )
    ])
  }

  @Bean
  @ConditionalOnMissingBean(AtomicOperationConverter)
  AtomicOperationConverter atomicOperationConverter() {
    new NoopAtomicOperationConverter()
  }

  @Bean
  public RetrySupport retrySupport() {
    return new RetrySupport();
  }

  @Bean
  NamerRegistry namerRegistry(Optional<List<NamingStrategy>> namingStrategies) {
    new NamerRegistry(namingStrategies.orElse([]))
  }
}
