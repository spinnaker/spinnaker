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

import com.netflix.spinnaker.clouddriver.proxmox.ProxmoxProvider;
import com.netflix.spinnaker.clouddriver.proxmox.security.ProxmoxCredentialsParser;
import com.netflix.spinnaker.clouddriver.proxmox.security.ProxmoxNamedAccountCredentials;
import com.netflix.spinnaker.clouddriver.security.AccountDefinitionRepository;
import com.netflix.spinnaker.clouddriver.security.AccountDefinitionSource;
import com.netflix.spinnaker.clouddriver.security.CredentialsInitializerSynchronizable;
import com.netflix.spinnaker.credentials.CredentialsLifecycleHandler;
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
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Main configuration class for the Proxmox cloud provider. This class is loaded when
 * proxmox.enabled=true.
 */
@Configuration
@EnableConfigurationProperties
@EnableScheduling
@ConditionalOnProperty("proxmox.enabled")
@ComponentScan({"com.netflix.spinnaker.clouddriver.proxmox"})
public class ProxmoxConfiguration {

  @Bean
  @ConfigurationProperties("proxmox")
  public ProxmoxConfigurationProperties proxmoxConfigurationProperties() {
    return new ProxmoxConfigurationProperties();
  }

  @Bean
  public CredentialsParser<
          ProxmoxConfigurationProperties.ProxmoxManagedAccount, ProxmoxNamedAccountCredentials>
      proxmoxCredentialsParser() {
    return new ProxmoxCredentialsParser();
  }

  @Bean
  CredentialsDefinitionSource<ProxmoxConfigurationProperties.ProxmoxManagedAccount>
      ecsAccountSource(
          AccountDefinitionRepository repository,
          Optional<
                  List<
                      CredentialsDefinitionSource<
                          ProxmoxConfigurationProperties.ProxmoxManagedAccount>>>
              additionalSources,
          ProxmoxConfigurationProperties proxmoxConfigurationProperties) {
    return new AccountDefinitionSource<>(
        repository,
        ProxmoxConfigurationProperties.ProxmoxManagedAccount.class,
        additionalSources.orElseGet(() -> List.of(proxmoxConfigurationProperties::getAccounts)));
  }

  @Bean
  @ConditionalOnMissingBean(
      value = ProxmoxNamedAccountCredentials.class,
      parameterizedContainer = AbstractCredentialsLoader.class)
  public AbstractCredentialsLoader<ProxmoxNamedAccountCredentials> proxmoxCredentialsLoader(
      @Nullable
          CredentialsDefinitionSource<ProxmoxConfigurationProperties.ProxmoxManagedAccount>
              proxmoxCredentialSource,
      ProxmoxConfigurationProperties accountProperties,
      CredentialsParser<
              ProxmoxConfigurationProperties.ProxmoxManagedAccount, ProxmoxNamedAccountCredentials>
          credentialsParser,
      CredentialsRepository<ProxmoxNamedAccountCredentials> proxmoxCredentialsRepository) {

    if (proxmoxCredentialSource == null) {
      proxmoxCredentialSource = accountProperties::getAccounts;
    }
    return new BasicCredentialsLoader<>(
        proxmoxCredentialSource, credentialsParser, proxmoxCredentialsRepository);
  }

  @Bean
  @ConditionalOnMissingBean(
      value = ProxmoxNamedAccountCredentials.class,
      parameterizedContainer = CredentialsRepository.class)
  public CredentialsRepository<ProxmoxNamedAccountCredentials> proxmoxCredentialsRepository(
      CredentialsLifecycleHandler<ProxmoxNamedAccountCredentials> eventHandler) {
    return new MapBackedCredentialsRepository<>(ProxmoxProvider.ID, eventHandler);
  }

  @Bean
  @ConditionalOnMissingBean(
      value = ProxmoxConfigurationProperties.ProxmoxManagedAccount.class,
      parameterizedContainer = CredentialsDefinitionSource.class)
  public CredentialsInitializerSynchronizable proxmoxCredentialsInitializerSynchronizable(
      AbstractCredentialsLoader<ProxmoxNamedAccountCredentials> loader) {
    final Poller<ProxmoxNamedAccountCredentials> poller = new Poller<>(loader);
    return new CredentialsInitializerSynchronizable() {
      @Override
      public void synchronize() {
        poller.run();
      }
    };
  }
}
