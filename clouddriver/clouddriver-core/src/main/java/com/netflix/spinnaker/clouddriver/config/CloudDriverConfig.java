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

package com.netflix.spinnaker.clouddriver.config;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.Module;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.datatype.joda.JodaModule;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.module.kotlin.KotlinModule;
import com.netflix.spectator.api.Registry;
import com.netflix.spinnaker.cats.agent.Agent;
import com.netflix.spinnaker.cats.agent.ExecutionInstrumentation;
import com.netflix.spinnaker.cats.agent.NoopExecutionInstrumentation;
import com.netflix.spinnaker.cats.redis.cache.RedisCacheOptions;
import com.netflix.spinnaker.clouddriver.cache.CacheConfig;
import com.netflix.spinnaker.clouddriver.cache.NoopOnDemandCacheUpdater;
import com.netflix.spinnaker.clouddriver.cache.OnDemandCacheUpdater;
import com.netflix.spinnaker.clouddriver.core.CloudProvider;
import com.netflix.spinnaker.clouddriver.core.NoopAtomicOperationConverter;
import com.netflix.spinnaker.clouddriver.core.NoopCloudProvider;
import com.netflix.spinnaker.clouddriver.core.ProjectClustersService;
import com.netflix.spinnaker.clouddriver.core.RedisConfig;
import com.netflix.spinnaker.clouddriver.core.agent.CleanupPendingOnDemandCachesAgent;
import com.netflix.spinnaker.clouddriver.core.agent.ProjectClustersCachingAgent;
import com.netflix.spinnaker.clouddriver.core.limits.ServiceLimitConfiguration;
import com.netflix.spinnaker.clouddriver.core.limits.ServiceLimitConfigurationBuilder;
import com.netflix.spinnaker.clouddriver.core.provider.CoreProvider;
import com.netflix.spinnaker.clouddriver.core.services.Front50Service;
import com.netflix.spinnaker.clouddriver.deploy.DefaultDescriptionAuthorizer;
import com.netflix.spinnaker.clouddriver.deploy.DescriptionAuthorizer;
import com.netflix.spinnaker.clouddriver.deploy.DescriptionAuthorizerService;
import com.netflix.spinnaker.clouddriver.jackson.ClouddriverApiModule;
import com.netflix.spinnaker.clouddriver.model.ApplicationProvider;
import com.netflix.spinnaker.clouddriver.model.CloudMetricProvider;
import com.netflix.spinnaker.clouddriver.model.ClusterProvider;
import com.netflix.spinnaker.clouddriver.model.ElasticIpProvider;
import com.netflix.spinnaker.clouddriver.model.ImageProvider;
import com.netflix.spinnaker.clouddriver.model.InstanceProvider;
import com.netflix.spinnaker.clouddriver.model.InstanceTypeProvider;
import com.netflix.spinnaker.clouddriver.model.KeyPairProvider;
import com.netflix.spinnaker.clouddriver.model.LoadBalancerProvider;
import com.netflix.spinnaker.clouddriver.model.NetworkProvider;
import com.netflix.spinnaker.clouddriver.model.NoopApplicationProvider;
import com.netflix.spinnaker.clouddriver.model.NoopCloudMetricProvider;
import com.netflix.spinnaker.clouddriver.model.NoopClusterProvider;
import com.netflix.spinnaker.clouddriver.model.NoopElasticIpProvider;
import com.netflix.spinnaker.clouddriver.model.NoopImageProvider;
import com.netflix.spinnaker.clouddriver.model.NoopInstanceProvider;
import com.netflix.spinnaker.clouddriver.model.NoopInstanceTypeProvider;
import com.netflix.spinnaker.clouddriver.model.NoopKeyPairProvider;
import com.netflix.spinnaker.clouddriver.model.NoopLoadBalancerProvider;
import com.netflix.spinnaker.clouddriver.model.NoopNetworkProvider;
import com.netflix.spinnaker.clouddriver.model.NoopReservationReportProvider;
import com.netflix.spinnaker.clouddriver.model.NoopSecurityGroupProvider;
import com.netflix.spinnaker.clouddriver.model.NoopServerGroupManagerProvider;
import com.netflix.spinnaker.clouddriver.model.NoopSubnetProvider;
import com.netflix.spinnaker.clouddriver.model.ReservationReportProvider;
import com.netflix.spinnaker.clouddriver.model.SecurityGroupProvider;
import com.netflix.spinnaker.clouddriver.model.ServerGroupManager;
import com.netflix.spinnaker.clouddriver.model.ServerGroupManagerProvider;
import com.netflix.spinnaker.clouddriver.model.SubnetProvider;
import com.netflix.spinnaker.clouddriver.names.NamerRegistry;
import com.netflix.spinnaker.clouddriver.names.NamingStrategy;
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperationConverter;
import com.netflix.spinnaker.clouddriver.orchestration.ExceptionClassifier;
import com.netflix.spinnaker.clouddriver.saga.SagaEvent;
import com.netflix.spinnaker.clouddriver.search.ApplicationSearchProvider;
import com.netflix.spinnaker.clouddriver.search.NoopSearchProvider;
import com.netflix.spinnaker.clouddriver.search.ProjectSearchProvider;
import com.netflix.spinnaker.clouddriver.search.SearchProvider;
import com.netflix.spinnaker.clouddriver.search.executor.SearchExecutorConfig;
import com.netflix.spinnaker.clouddriver.security.AccountCredentials;
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsProvider;
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsRepository;
import com.netflix.spinnaker.clouddriver.security.AccountDefinitionSecretManager;
import com.netflix.spinnaker.clouddriver.security.DefaultAccountCredentialsProvider;
import com.netflix.spinnaker.clouddriver.security.MapBackedAccountCredentialsRepository;
import com.netflix.spinnaker.clouddriver.security.config.SecurityConfig;
import com.netflix.spinnaker.config.PluginsAutoConfiguration;
import com.netflix.spinnaker.credentials.CompositeCredentialsRepository;
import com.netflix.spinnaker.credentials.Credentials;
import com.netflix.spinnaker.credentials.CredentialsRepository;
import com.netflix.spinnaker.credentials.definition.CredentialsLoader;
import com.netflix.spinnaker.credentials.poller.PollerConfiguration;
import com.netflix.spinnaker.credentials.poller.PollerConfigurationProperties;
import com.netflix.spinnaker.fiat.shared.FiatPermissionEvaluator;
import com.netflix.spinnaker.kork.artifacts.artifactstore.ArtifactDeserializer;
import com.netflix.spinnaker.kork.artifacts.artifactstore.ArtifactStore;
import com.netflix.spinnaker.kork.artifacts.artifactstore.ArtifactStoreConfiguration;
import com.netflix.spinnaker.kork.core.RetrySupport;
import com.netflix.spinnaker.kork.dynamicconfig.DynamicConfigService;
import com.netflix.spinnaker.kork.jackson.ObjectMapperSubtypeConfigurer;
import com.netflix.spinnaker.kork.jedis.RedisClientDelegate;
import jakarta.inject.Provider;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.PropertySource;
import org.springframework.core.annotation.Order;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.web.client.RestTemplate;

@Configuration
@Import({
  RedisConfig.class,
  CacheConfig.class,
  SearchExecutorConfig.class,
  PluginsAutoConfiguration.class,
  ArtifactStoreConfiguration.class,
})
@PropertySource(
    value = "classpath:META-INF/clouddriver-core.properties",
    ignoreResourceNotFound = true)
@EnableConfigurationProperties({
  ProjectClustersCachingAgentProperties.class,
  ExceptionClassifierConfigurationProperties.class,
  PollerConfigurationProperties.class
})
class CloudDriverConfig {

  @Bean
  @ConditionalOnMissingBean(Clock.class)
  Clock clock() {
    return Clock.systemDefaultZone();
  }

  @Bean
  Jackson2ObjectMapperBuilderCustomizer defaultObjectMapperCustomizer(List<Module> modules) {
    return jacksonObjectMapperBuilder -> {
      modules.addAll(
          List.of(
              new Jdk8Module(),
              new JavaTimeModule(),
              new JodaModule(),
              new KotlinModule(),
              new ClouddriverApiModule()));
      jacksonObjectMapperBuilder.serializationInclusion(JsonInclude.Include.NON_NULL);
      jacksonObjectMapperBuilder.failOnEmptyBeans(false);
      jacksonObjectMapperBuilder.failOnUnknownProperties(false);
      jacksonObjectMapperBuilder.modules(modules);
    };
  }

  @Bean
  ObjectMapperSubtypeConfigurer.SubtypeLocator clouddriverSubtypeLocator() {
    return new ObjectMapperSubtypeConfigurer.ClassSubtypeLocator(
        SagaEvent.class, List.of("com.netflix.spinnaker.clouddriver.orchestration.sagas"));
  }

  @Bean
  String clouddriverUserAgentApplicationName() {
    return String.format("Spinnaker/%s", System.getProperty("Implementation-Version", "Unknown"));
  }

  @Bean
  @ConfigurationProperties("service-limits")
  ServiceLimitConfigurationBuilder serviceLimitConfigProperties() {
    return new ServiceLimitConfigurationBuilder();
  }

  @Bean
  ServiceLimitConfiguration serviceLimitConfiguration(
      ServiceLimitConfigurationBuilder serviceLimitConfigProperties) {
    return serviceLimitConfigProperties.build();
  }

  @Bean
  @ConditionalOnMissingBean(AccountCredentialsRepository.class)
  AccountCredentialsRepository accountCredentialsRepository() {
    return new MapBackedAccountCredentialsRepository();
  }

  @Bean
  @ConditionalOnMissingBean(AccountCredentialsProvider.class)
  AccountCredentialsProvider accountCredentialsProvider(
      AccountCredentialsRepository accountCredentialsRepository,
      CompositeCredentialsRepository<AccountCredentials<?>> compositeRepository) {
    return new DefaultAccountCredentialsProvider(accountCredentialsRepository, compositeRepository);
  }

  @Bean
  @ConditionalOnMissingBean(
      value = AccountCredentials.class,
      parameterizedContainer = CompositeCredentialsRepository.class)
  CompositeCredentialsRepository<AccountCredentials> compositeCredentialsRepository(
      List<CredentialsRepository<? extends AccountCredentials>> repositories) {
    return new CompositeCredentialsRepository<>(repositories);
  }

  @Bean
  PollerConfiguration pollerConfiguration(
      ObjectProvider<CredentialsLoader<? extends Credentials>> pollers,
      PollerConfigurationProperties pollerConfigurationProperties) {
    return new PollerConfiguration(pollerConfigurationProperties, pollers);
  }

  @Bean
  RestTemplate restTemplate() {
    return new RestTemplate();
  }

  @Bean
  @ConditionalOnMissingBean(OnDemandCacheUpdater.class)
  NoopOnDemandCacheUpdater noopOnDemandCacheUpdater() {
    return new NoopOnDemandCacheUpdater();
  }

  @Bean
  @ConditionalOnMissingBean(SearchProvider.class)
  NoopSearchProvider noopSearchProvider() {
    return new NoopSearchProvider();
  }

  @Bean
  @ConditionalOnExpression("${services.front50.enabled:true}")
  ApplicationSearchProvider applicationSearchProvider(Front50Service front50Service) {
    return new ApplicationSearchProvider(front50Service);
  }

  @Bean
  @ConditionalOnExpression("${services.front50.enabled:true}")
  ProjectSearchProvider projectSearchProvider(Front50Service front50Service) {
    return new ProjectSearchProvider(front50Service);
  }

  @Bean
  @ConditionalOnMissingBean(CloudProvider.class)
  CloudProvider noopCloudProvider() {
    return new NoopCloudProvider();
  }

  @Bean
  @ConditionalOnMissingBean(CloudMetricProvider.class)
  CloudMetricProvider noopCloudMetricProvider() {
    return new NoopCloudMetricProvider();
  }

  @Bean
  @ConditionalOnMissingBean(ApplicationProvider.class)
  ApplicationProvider noopApplicationProvider() {
    return new NoopApplicationProvider();
  }

  @Bean
  @ConditionalOnMissingBean(LoadBalancerProvider.class)
  LoadBalancerProvider noopLoadBalancerProvider() {
    return new NoopLoadBalancerProvider();
  }

  @Bean
  @ConditionalOnMissingBean(ClusterProvider.class)
  ClusterProvider noopClusterProvider() {
    return new NoopClusterProvider();
  }

  @Bean
  @ConditionalOnMissingBean(ReservationReportProvider.class)
  ReservationReportProvider noopReservationReportProvider() {
    return new NoopReservationReportProvider();
  }

  @Bean
  @ConditionalOnMissingBean(ExecutionInstrumentation.class)
  ExecutionInstrumentation noopExecutionInstrumentation() {
    return new NoopExecutionInstrumentation();
  }

  @Bean
  @ConditionalOnMissingBean(InstanceProvider.class)
  InstanceProvider noopInstanceProvider() {
    return new NoopInstanceProvider();
  }

  @Bean
  @ConditionalOnMissingBean(ImageProvider.class)
  ImageProvider noopImageProvider() {
    return new NoopImageProvider();
  }

  @Bean
  @ConditionalOnMissingBean(InstanceTypeProvider.class)
  InstanceTypeProvider noopInstanceTypeProvider() {
    return new NoopInstanceTypeProvider();
  }

  @Bean
  @ConditionalOnMissingBean(KeyPairProvider.class)
  KeyPairProvider noopKeyPairProvider() {
    return new NoopKeyPairProvider();
  }

  @Bean
  @ConditionalOnMissingBean(SecurityGroupProvider.class)
  SecurityGroupProvider noopSecurityGroupProvider() {
    return new NoopSecurityGroupProvider();
  }

  @Bean
  @ConditionalOnMissingBean(ServerGroupManager.class)
  ServerGroupManagerProvider noopServerGroupManagerProvider() {
    return new NoopServerGroupManagerProvider();
  }

  @Bean
  @ConditionalOnMissingBean(SubnetProvider.class)
  SubnetProvider noopSubnetProvider() {
    return new NoopSubnetProvider();
  }

  @Bean
  @ConditionalOnMissingBean(NetworkProvider.class)
  NetworkProvider noopVpcProvider() {
    return new NoopNetworkProvider();
  }

  @Bean
  @ConditionalOnMissingBean(ElasticIpProvider.class)
  ElasticIpProvider noopElasticIpProvider() {
    return new NoopElasticIpProvider();
  }

  @Bean
  ProjectClustersService projectClustersService(
      Front50Service front50Service,
      ObjectMapper objectMapper,
      Provider<List<ClusterProvider>> clusterProviders) {
    return new ProjectClustersService(front50Service, objectMapper, clusterProviders);
  }

  @Bean
  CoreProvider coreProvider(
      Optional<RedisCacheOptions> redisCacheOptions,
      Optional<RedisClientDelegate> redisClientDelegate,
      ApplicationContext applicationContext,
      ProjectClustersService projectClustersService,
      ProjectClustersCachingAgentProperties projectClustersCachingAgentProperties) {
    List<Agent> agents = new ArrayList<>();
    agents.add(
        new ProjectClustersCachingAgent(
            projectClustersService, projectClustersCachingAgentProperties));

    if (redisCacheOptions.isPresent() && redisClientDelegate.isPresent()) {
      agents.add(
          new CleanupPendingOnDemandCachesAgent(
              redisCacheOptions.get(), redisClientDelegate.get(), applicationContext));
    }

    return new CoreProvider(agents);
  }

  @Bean
  @ConditionalOnMissingBean(AtomicOperationConverter.class)
  AtomicOperationConverter atomicOperationConverter() {
    return new NoopAtomicOperationConverter();
  }

  @Bean
  public RetrySupport retrySupport() {
    return new RetrySupport();
  }

  @Bean
  NamerRegistry namerRegistry(Optional<List<NamingStrategy>> namingStrategies) {
    return new NamerRegistry(namingStrategies.orElse(List.of()));
  }

  @Bean
  DescriptionAuthorizerService descriptionAuthorizerService(
      Registry registry,
      Optional<FiatPermissionEvaluator> fiatPermissionEvaluator,
      SecurityConfig.OperationsSecurityConfigurationProperties opsSecurityConfigProps,
      AccountDefinitionSecretManager secretManager) {
    return new DescriptionAuthorizerService(
        registry, fiatPermissionEvaluator, opsSecurityConfigProps, secretManager);
  }

  @Bean
  @Order
  DescriptionAuthorizer descriptionAuthorizer(
      DescriptionAuthorizerService descriptionAuthorizerService) {
    return new DefaultDescriptionAuthorizer(descriptionAuthorizerService);
  }

  @Bean
  ExceptionClassifier exceptionClassifier(
      ExceptionClassifierConfigurationProperties properties,
      DynamicConfigService dynamicConfigService) {
    return new ExceptionClassifier(properties, dynamicConfigService);
  }

  @Bean
  ThreadPoolTaskScheduler threadPoolTaskScheduler(
      @Value("${scheduling-thread-pool-size:5}") int threadPoolSize) {
    ThreadPoolTaskScheduler threadPoolTaskScheduler = new ThreadPoolTaskScheduler();
    threadPoolTaskScheduler.setPoolSize(threadPoolSize);
    threadPoolTaskScheduler.setThreadNamePrefix("ThreadPoolTaskScheduler");
    return threadPoolTaskScheduler;
  }

  @Bean
  ArtifactDeserializer artifactDeserializer(
      ArtifactStore storage, @Qualifier("artifactObjectMapper") ObjectMapper objectMapper) {
    return new ArtifactDeserializer(objectMapper, storage);
  }
}
