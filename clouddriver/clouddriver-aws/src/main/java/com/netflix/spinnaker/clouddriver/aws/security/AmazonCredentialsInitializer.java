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

package com.netflix.spinnaker.clouddriver.aws.security;

import com.amazonaws.auth.AWSCredentialsProvider;
import com.google.common.base.Strings;
import com.netflix.spinnaker.clouddriver.aws.AmazonCloudProvider;
import com.netflix.spinnaker.clouddriver.aws.security.config.AccountsConfiguration;
import com.netflix.spinnaker.clouddriver.aws.security.config.AccountsConfiguration.Account;
import com.netflix.spinnaker.clouddriver.aws.security.config.AmazonCredentialsParser;
import com.netflix.spinnaker.clouddriver.aws.security.config.CredentialsConfig;
import com.netflix.spinnaker.clouddriver.security.CredentialsInitializerSynchronizable;
import com.netflix.spinnaker.credentials.CredentialsLifecycleHandler;
import com.netflix.spinnaker.credentials.CredentialsRepository;
import com.netflix.spinnaker.credentials.MapBackedCredentialsRepository;
import com.netflix.spinnaker.credentials.definition.AbstractCredentialsLoader;
import com.netflix.spinnaker.credentials.definition.CredentialsDefinitionSource;
import com.netflix.spinnaker.credentials.definition.CredentialsParser;
import com.netflix.spinnaker.credentials.poller.Poller;
import javax.annotation.Nullable;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Primary;
import org.springframework.util.CollectionUtils;

@Configuration
@EnableConfigurationProperties(DefaultAccountConfigurationProperties.class)
public class AmazonCredentialsInitializer {

  @Bean
  @ConditionalOnMissingBean(CredentialsConfig.class)
  @ConfigurationProperties("aws")
  CredentialsConfig credentialsConfig() {
    return new CredentialsConfig();
  }

  @Bean
  @ConditionalOnMissingBean(AccountsConfiguration.class)
  @ConfigurationProperties("aws")
  AccountsConfiguration accountsConfiguration() {
    return new AccountsConfiguration();
  }

  @Bean
  Class<? extends NetflixAmazonCredentials> credentialsType(
      CredentialsConfig credentialsConfig, AccountsConfiguration accountsConfig) {
    if (CollectionUtils.isEmpty(accountsConfig.getAccounts())
        && Strings.isNullOrEmpty(credentialsConfig.getDefaultAssumeRole())) {
      return NetflixAmazonCredentials.class;
    } else {
      return NetflixAssumeRoleAmazonCredentials.class;
    }
  }

  @Bean
  @ConditionalOnMissingBean(name = "amazonCredentialsParser")
  CredentialsParser<Account, NetflixAmazonCredentials> amazonCredentialsParser(
      AWSCredentialsProvider awsCredentialsProvider,
      AmazonClientProvider amazonClientProvider,
      AWSAccountInfoLookup awsAccountInfoLookup,
      AWSAccountInfoLookupFactory awsAccountInfoLookupFactory,
      AWSCredentialsProviderFactory awsCredentialsProviderFactory,
      Class<? extends NetflixAmazonCredentials> credentialsType,
      CredentialsConfig credentialsConfig,
      AccountsConfiguration accountsConfig) {
    return new AmazonCredentialsParser<>(
        awsCredentialsProvider,
        amazonClientProvider,
        awsAccountInfoLookup,
        awsAccountInfoLookupFactory,
        awsCredentialsProviderFactory,
        (Class<NetflixAmazonCredentials>) credentialsType,
        credentialsConfig,
        accountsConfig);
  }

  @Bean
  @Primary
  @ConditionalOnMissingBean(name = "amazonCredentialsRepository")
  CredentialsRepository<NetflixAmazonCredentials> amazonCredentialsRepository(
      @Lazy CredentialsLifecycleHandler<NetflixAmazonCredentials> eventHandler) {
    return new MapBackedCredentialsRepository<>(AmazonCloudProvider.ID, eventHandler);
  }

  @Bean
  @ConditionalOnMissingBean(name = "amazonCredentialsLoader")
  AbstractCredentialsLoader<? extends NetflixAmazonCredentials> amazonCredentialsLoader(
      CredentialsParser<Account, NetflixAmazonCredentials> amazonCredentialsParser,
      @Nullable CredentialsDefinitionSource<Account> amazonCredentialsSource,
      CredentialsConfig credentialsConfig,
      AccountsConfiguration accountsConfig,
      CredentialsRepository<NetflixAmazonCredentials> repository,
      DefaultAccountConfigurationProperties defaultAccountConfigurationProperties) {
    if (amazonCredentialsSource == null) {
      amazonCredentialsSource = accountsConfig::getAccounts;
    }
    return new AmazonBasicCredentialsLoader<>(
        amazonCredentialsSource,
        amazonCredentialsParser,
        repository,
        credentialsConfig,
        accountsConfig,
        defaultAccountConfigurationProperties);
  }

  @Bean
  @ConditionalOnMissingBean(name = "amazonCredentialsInitializerSynchronizable")
  CredentialsInitializerSynchronizable amazonCredentialsInitializerSynchronizable(
      AbstractCredentialsLoader<? extends NetflixAmazonCredentials> amazonCredentialsLoader) {
    final Poller<? extends NetflixAmazonCredentials> poller = new Poller<>(amazonCredentialsLoader);
    return new CredentialsInitializerSynchronizable() {
      @Override
      public void synchronize() {
        poller.run();
      }
    };
  }
}
