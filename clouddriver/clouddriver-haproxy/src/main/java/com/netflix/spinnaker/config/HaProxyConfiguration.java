/*
 * Copyright 2026 McIntosh.farm
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
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

import com.netflix.spinnaker.clouddriver.haproxy.HaProxyProvider;
import com.netflix.spinnaker.clouddriver.haproxy.security.HaProxyCredentialsLifecycleHandler;
import com.netflix.spinnaker.clouddriver.haproxy.security.HaProxyCredentialsParser;
import com.netflix.spinnaker.clouddriver.haproxy.security.HaProxyNamedAccountCredentials;
import com.netflix.spinnaker.clouddriver.security.AccountDefinitionRepository;
import com.netflix.spinnaker.clouddriver.security.AccountDefinitionSource;
import com.netflix.spinnaker.clouddriver.security.CredentialsInitializerSynchronizable;
import com.netflix.spinnaker.credentials.CredentialsRepository;
import com.netflix.spinnaker.credentials.MapBackedCredentialsRepository;
import com.netflix.spinnaker.credentials.definition.AbstractCredentialsLoader;
import com.netflix.spinnaker.credentials.definition.BasicCredentialsLoader;
import com.netflix.spinnaker.credentials.definition.CredentialsDefinitionSource;
import com.netflix.spinnaker.credentials.definition.CredentialsParser;
import com.netflix.spinnaker.credentials.poller.Poller;
import java.util.List;
import java.util.Optional;
import javax.annotation.Nullable;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Main configuration class for the HAProxy cloud provider. This class is loaded when
 * haproxy.enabled=true.
 */
@Configuration
@EnableConfigurationProperties
@EnableScheduling
@ConditionalOnProperty("haproxy.enabled")
@ComponentScan({"com.netflix.spinnaker.clouddriver.haproxy"})
public class HaProxyConfiguration {

  @Bean
  public HaProxyProvider haProxyProvider() {
    return new HaProxyProvider();
  }

  @Bean
  public HaProxyConfigurationProperties haProxyConfigurationProperties() {
    return new HaProxyConfigurationProperties();
  }

  @Bean
  public CredentialsParser<
          HaProxyConfigurationProperties.HaProxyManagedAccount, HaProxyNamedAccountCredentials>
      haProxyCredentialsParser() {
    return new HaProxyCredentialsParser();
  }

  @Bean
  @ConditionalOnProperty({"account.storage.enabled"})
  CredentialsDefinitionSource<HaProxyConfigurationProperties.HaProxyManagedAccount>
      haProxyAccountSource(
          AccountDefinitionRepository repository,
          Optional<
                  List<
                      CredentialsDefinitionSource<
                          HaProxyConfigurationProperties.HaProxyManagedAccount>>>
              additionalSources,
          HaProxyConfigurationProperties haProxyConfigurationProperties) {
    return new AccountDefinitionSource<>(
        repository,
        HaProxyConfigurationProperties.HaProxyManagedAccount.class,
        additionalSources.orElseGet(() -> List.of(haProxyConfigurationProperties::getAccounts)));
  }

  @Bean
  @ConditionalOnMissingBean(
      value = HaProxyNamedAccountCredentials.class,
      parameterizedContainer = AbstractCredentialsLoader.class)
  public AbstractCredentialsLoader<HaProxyNamedAccountCredentials> haProxyCredentialsLoader(
      @Nullable
          CredentialsDefinitionSource<HaProxyConfigurationProperties.HaProxyManagedAccount>
              haProxyCredentialSource,
      HaProxyConfigurationProperties accountProperties,
      CredentialsParser<
              HaProxyConfigurationProperties.HaProxyManagedAccount, HaProxyNamedAccountCredentials>
          credentialsParser,
      CredentialsRepository<HaProxyNamedAccountCredentials> haProxyCredentialsRepository) {

    if (haProxyCredentialSource == null) {
      haProxyCredentialSource = accountProperties::getAccounts;
    }
    return new BasicCredentialsLoader<>(
        haProxyCredentialSource, credentialsParser, haProxyCredentialsRepository);
  }

  @Bean
  @ConditionalOnMissingBean(
      value = HaProxyNamedAccountCredentials.class,
      parameterizedContainer = CredentialsRepository.class)
  public CredentialsRepository<HaProxyNamedAccountCredentials> haProxyCredentialsRepository(
      HaProxyCredentialsLifecycleHandler eventHandler) {
    return new MapBackedCredentialsRepository<>(HaProxyProvider.ID, eventHandler);
  }

  @Bean
  @ConditionalOnMissingBean(
      value = HaProxyConfigurationProperties.HaProxyManagedAccount.class,
      parameterizedContainer = CredentialsDefinitionSource.class)
  public CredentialsInitializerSynchronizable haProxyCredentialsInitializerSynchronizable(
      AbstractCredentialsLoader<HaProxyNamedAccountCredentials> loader) {
    final Poller<HaProxyNamedAccountCredentials> poller = new Poller<>(loader);
    return new CredentialsInitializerSynchronizable() {
      @Override
      public void synchronize() {
        poller.run();
      }
    };
  }
}
