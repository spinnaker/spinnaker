/*
 * Copyright 2021 Armory
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

package com.netflix.spinnaker.config;

import com.netflix.spectator.api.Registry;
import com.netflix.spinnaker.clouddriver.docker.registry.api.v2.client.DockerOkClientProvider;
import com.netflix.spinnaker.clouddriver.docker.registry.config.DockerRegistryConfigurationProperties;
import com.netflix.spinnaker.clouddriver.docker.registry.config.DockerRegistryConfigurationProperties.ManagedAccount;
import com.netflix.spinnaker.clouddriver.docker.registry.health.DockerRegistryHealthIndicator;
import com.netflix.spinnaker.clouddriver.docker.registry.provider.DockerRegistryProvider;
import com.netflix.spinnaker.clouddriver.docker.registry.security.DockerRegistryCredentialsInitializer;
import com.netflix.spinnaker.clouddriver.docker.registry.security.DockerRegistryNamedAccountCredentials;
import com.netflix.spinnaker.clouddriver.security.CredentialsInitializerSynchronizable;
import com.netflix.spinnaker.credentials.CredentialsLifecycleHandler;
import com.netflix.spinnaker.credentials.CredentialsRepository;
import com.netflix.spinnaker.credentials.MapBackedCredentialsRepository;
import com.netflix.spinnaker.credentials.definition.AbstractCredentialsLoader;
import com.netflix.spinnaker.credentials.definition.BasicCredentialsLoader;
import com.netflix.spinnaker.credentials.definition.CredentialsDefinitionSource;
import com.netflix.spinnaker.credentials.poller.Poller;
import javax.annotation.Nullable;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableConfigurationProperties
@EnableScheduling
@ConditionalOnProperty("docker-registry.enabled")
@ComponentScan("com.netflix.spinnaker.clouddriver.docker.registry")
@Import(DockerRegistryCredentialsInitializer.class)
public class DockerRegistryConfiguration {

  @Bean
  @RefreshScope
  @ConfigurationProperties("docker-registry")
  public DockerRegistryConfigurationProperties dockerRegistryConfigurationProperties() {
    return new DockerRegistryConfigurationProperties();
  }

  @Bean
  public DockerRegistryHealthIndicator dockerRegistryHealthIndicator(
      Registry registry,
      CredentialsRepository<DockerRegistryNamedAccountCredentials> credentialsRepository) {
    return new DockerRegistryHealthIndicator(registry, credentialsRepository);
  }

  @Bean
  @ConditionalOnMissingBean(
      value = DockerRegistryNamedAccountCredentials.class,
      parameterizedContainer = AbstractCredentialsLoader.class)
  public AbstractCredentialsLoader<DockerRegistryNamedAccountCredentials>
      dockerRegistryCredentialsLoader(
          @Nullable CredentialsDefinitionSource<ManagedAccount> dockerRegistryCredentialsSource,
          DockerRegistryConfigurationProperties accountProperties,
          DockerOkClientProvider dockerOkClientProvider,
          CredentialsRepository<DockerRegistryNamedAccountCredentials>
              dockerRegistryCredentialsRepository) {

    if (dockerRegistryCredentialsSource == null) {
      dockerRegistryCredentialsSource = accountProperties::getAccounts;
    }

    return new BasicCredentialsLoader<>(
        dockerRegistryCredentialsSource,
        a ->
            (new DockerRegistryNamedAccountCredentials.Builder())
                .accountName(a.getName())
                .environment(a.getEnvironment() != null ? a.getEnvironment() : a.getName())
                .accountType(a.getAccountType() != null ? a.getAccountType() : a.getName())
                .address(a.getAddress())
                .password(a.getPassword())
                .passwordCommand(a.getPasswordCommand())
                .username(a.getUsername())
                .email(a.getEmail())
                .passwordFile(a.getPasswordFile())
                .catalogFile(a.getCatalogFile())
                .repositoriesRegex(a.getRepositoriesRegex())
                .dockerconfigFile(a.getDockerconfigFile())
                .cacheThreads(a.getCacheThreads())
                .cacheIntervalSeconds(a.getCacheIntervalSeconds())
                .clientTimeoutMillis(a.getClientTimeoutMillis())
                .paginateSize(a.getPaginateSize())
                .trackDigests(a.getTrackDigests())
                .inspectDigests(a.getInspectDigests())
                .sortTagsByDate(a.getSortTagsByDate())
                .insecureRegistry(a.getInsecureRegistry())
                .repositories(a.getRepositories())
                .skip(a.getSkip())
                .dockerOkClientProvider(dockerOkClientProvider)
                .build(),
        dockerRegistryCredentialsRepository);
  }

  @Bean
  @ConditionalOnMissingBean(
      value = DockerRegistryNamedAccountCredentials.class,
      parameterizedContainer = CredentialsRepository.class)
  public CredentialsRepository<DockerRegistryNamedAccountCredentials>
      dockerRegistryCredentialsRepository(
          CredentialsLifecycleHandler<DockerRegistryNamedAccountCredentials> eventHandler) {
    return new MapBackedCredentialsRepository<>(DockerRegistryProvider.PROVIDER_NAME, eventHandler);
  }

  @Bean
  @ConditionalOnMissingBean(
      value = ManagedAccount.class,
      parameterizedContainer = CredentialsDefinitionSource.class)
  public CredentialsInitializerSynchronizable dockerRegistryCredentialsInitializerSynchronizable(
      AbstractCredentialsLoader<DockerRegistryNamedAccountCredentials> loader) {
    final Poller<DockerRegistryNamedAccountCredentials> poller = new Poller<>(loader);
    return new CredentialsInitializerSynchronizable() {
      @Override
      public void synchronize() {
        poller.run();
      }
    };
  }
}
