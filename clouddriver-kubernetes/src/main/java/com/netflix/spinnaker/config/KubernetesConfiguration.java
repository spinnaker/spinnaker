/*
 * Copyright 2015 Google, Inc.
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
import com.netflix.spinnaker.clouddriver.kubernetes.caching.KubernetesProvider;
import com.netflix.spinnaker.clouddriver.kubernetes.config.KubernetesConfigurationProperties;
import com.netflix.spinnaker.clouddriver.kubernetes.health.KubernetesHealthIndicator;
import com.netflix.spinnaker.clouddriver.kubernetes.security.KubernetesCredentials;
import com.netflix.spinnaker.clouddriver.kubernetes.security.KubernetesNamedAccountCredentials;
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
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableConfigurationProperties
@EnableScheduling
@ConditionalOnProperty("kubernetes.enabled")
@ComponentScan({"com.netflix.spinnaker.clouddriver.kubernetes"})
public class KubernetesConfiguration {
  @Bean
  @RefreshScope
  @ConfigurationProperties("kubernetes")
  public KubernetesConfigurationProperties kubernetesConfigurationProperties() {
    return new KubernetesConfigurationProperties();
  }

  @Bean
  public KubernetesHealthIndicator kubernetesHealthIndicator(
      Registry registry,
      CredentialsRepository<KubernetesNamedAccountCredentials> credentialsRepository) {
    return new KubernetesHealthIndicator(registry, credentialsRepository);
  }

  @Bean
  public KubernetesProvider kubernetesProvider() {
    return new KubernetesProvider();
  }

  @Bean
  @ConditionalOnMissingBean(
      value = KubernetesNamedAccountCredentials.class,
      parameterizedContainer = AbstractCredentialsLoader.class)
  public AbstractCredentialsLoader<KubernetesNamedAccountCredentials> kubernetesCredentialsLoader(
      @Nullable
          CredentialsDefinitionSource<KubernetesConfigurationProperties.ManagedAccount>
              kubernetesCredentialSource,
      KubernetesConfigurationProperties configurationProperties,
      KubernetesCredentials.Factory credentialFactory,
      CredentialsRepository<KubernetesNamedAccountCredentials> kubernetesCredentialsRepository) {

    if (kubernetesCredentialSource == null) {
      kubernetesCredentialSource = configurationProperties::getAccounts;
    }
    return new BasicCredentialsLoader<>(
        kubernetesCredentialSource,
        a -> new KubernetesNamedAccountCredentials(a, credentialFactory),
        kubernetesCredentialsRepository);
  }

  @Bean
  @ConditionalOnMissingBean(
      value = KubernetesNamedAccountCredentials.class,
      parameterizedContainer = CredentialsRepository.class)
  public CredentialsRepository<KubernetesNamedAccountCredentials> kubernetesCredentialsRepository(
      CredentialsLifecycleHandler<KubernetesNamedAccountCredentials> eventHandler) {
    return new MapBackedCredentialsRepository<>(KubernetesProvider.PROVIDER_NAME, eventHandler);
  }

  @Bean
  @ConditionalOnMissingBean(
      value = KubernetesConfigurationProperties.ManagedAccount.class,
      parameterizedContainer = CredentialsDefinitionSource.class)
  public CredentialsInitializerSynchronizable kubernetesCredentialsInitializerSynchronizable(
      AbstractCredentialsLoader<KubernetesNamedAccountCredentials> loader) {
    final Poller<KubernetesNamedAccountCredentials> poller = new Poller<>(loader);
    return new CredentialsInitializerSynchronizable() {
      @Override
      public void synchronize() {
        poller.run();
      }
    };
  }
}
