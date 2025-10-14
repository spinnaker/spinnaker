/*
 * Copyright 2025 Harness, Inc.
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

package com.netflix.spinnaker.clouddriver.azure.security;

import com.netflix.spinnaker.clouddriver.azure.config.AzureConfigurationProperties;
import com.netflix.spinnaker.clouddriver.security.CredentialsInitializerSynchronizable;
import com.netflix.spinnaker.credentials.CredentialsLifecycleHandler;
import com.netflix.spinnaker.credentials.CredentialsRepository;
import com.netflix.spinnaker.credentials.MapBackedCredentialsRepository;
import com.netflix.spinnaker.credentials.definition.AbstractCredentialsLoader;
import com.netflix.spinnaker.credentials.definition.BasicCredentialsLoader;
import com.netflix.spinnaker.credentials.definition.CredentialsDefinitionSource;
import com.netflix.spinnaker.credentials.definition.CredentialsParser;
import com.netflix.spinnaker.credentials.poller.Poller;
import javax.annotation.Nullable;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;

@Configuration
public class AzureCredentialsConfiguration {

  @Bean
  @ConditionalOnMissingBean(
      value = AzureNamedAccountCredentials.class,
      parameterizedContainer = CredentialsRepository.class)
  public CredentialsRepository<AzureNamedAccountCredentials> azureCredentialsRepository(
      @Lazy CredentialsLifecycleHandler<AzureNamedAccountCredentials> lifecycleHandler) {
    return new MapBackedCredentialsRepository<>("azure", lifecycleHandler);
  }

  @Bean
  @ConditionalOnMissingBean(name = "azureCredentialsLoader")
  public AbstractCredentialsLoader<AzureNamedAccountCredentials> azureCredentialsLoader(
      CredentialsParser<AzureConfigurationProperties.ManagedAccount, AzureNamedAccountCredentials>
          azureCredentialsParser,
      CredentialsRepository<AzureNamedAccountCredentials> repository,
      AzureConfigurationProperties azureConfigurationProperties,
      @Nullable
          CredentialsDefinitionSource<AzureConfigurationProperties.ManagedAccount>
              azureCredentialsSource) {

    // Fix type inference issue by explicitly creating the proper CredentialsDefinitionSource
    CredentialsDefinitionSource<AzureConfigurationProperties.ManagedAccount> source =
        azureCredentialsSource != null
            ? azureCredentialsSource
            : () -> azureConfigurationProperties.getAccounts();

    // Use explicit generic type parameters for BasicCredentialsLoader
    return new BasicCredentialsLoader<
        AzureConfigurationProperties.ManagedAccount, AzureNamedAccountCredentials>(
        source, azureCredentialsParser, repository);
  }

  @Bean
  @ConditionalOnMissingBean(name = "azureCredentialsInitializerSynchronizable")
  public CredentialsInitializerSynchronizable azureCredentialsInitializerSynchronizable(
      AbstractCredentialsLoader<AzureNamedAccountCredentials> azureCredentialsLoader) {
    final Poller<AzureNamedAccountCredentials> poller = new Poller<>(azureCredentialsLoader);
    return new CredentialsInitializerSynchronizable() {
      @Override
      public void synchronize() {
        poller.run();
      }
    };
  }
}
