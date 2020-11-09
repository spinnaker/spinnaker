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

package com.netflix.spinnaker.clouddriver.aws.security

import com.amazonaws.auth.AWSCredentialsProvider
import com.netflix.spinnaker.clouddriver.aws.AmazonCloudProvider
import com.netflix.spinnaker.clouddriver.aws.security.config.AmazonCredentialsParser
import com.netflix.spinnaker.clouddriver.aws.security.config.CredentialsConfig
import com.netflix.spinnaker.clouddriver.aws.security.config.CredentialsConfig.Account
import com.netflix.spinnaker.clouddriver.security.CredentialsInitializerSynchronizable
import com.netflix.spinnaker.credentials.CredentialsLifecycleHandler
import com.netflix.spinnaker.credentials.CredentialsRepository
import com.netflix.spinnaker.credentials.MapBackedCredentialsRepository
import com.netflix.spinnaker.credentials.definition.AbstractCredentialsLoader
import com.netflix.spinnaker.credentials.definition.CredentialsDefinitionSource
import com.netflix.spinnaker.credentials.definition.CredentialsParser
import com.netflix.spinnaker.credentials.poller.Poller
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Lazy
import org.springframework.context.annotation.Primary

import javax.annotation.Nullable

@Configuration
@EnableConfigurationProperties(DefaultAccountConfigurationProperties)
class AmazonCredentialsInitializer {

  @Bean
  @ConditionalOnMissingBean(CredentialsConfig.class)
  @ConfigurationProperties('aws')
  CredentialsConfig credentialsConfig() {
    new CredentialsConfig()
  }

  @Bean
  Class<? extends NetflixAmazonCredentials> credentialsType(
    CredentialsConfig credentialsConfig
  ) {
    if (!credentialsConfig.accounts && !credentialsConfig.defaultAssumeRole) {
      NetflixAmazonCredentials
    } else {
      NetflixAssumeRoleAmazonCredentials
    }
  }

  @Bean
  @ConditionalOnMissingBean(
    name = "amazonCredentialsParser"
  )
  CredentialsParser<Account, NetflixAmazonCredentials> amazonCredentialsParser(
    AWSCredentialsProvider awsCredentialsProvider,
    AmazonClientProvider amazonClientProvider,
    Class<? extends NetflixAmazonCredentials> credentialsType, CredentialsConfig credentialsConfig
  ) {
    new AmazonCredentialsParser<>(awsCredentialsProvider, amazonClientProvider, credentialsType, credentialsConfig)
  }

  @Bean
  @Primary
  @ConditionalOnMissingBean(
    name = "amazonCredentialsRepository"
  )
  CredentialsRepository<NetflixAmazonCredentials> amazonCredentialsRepository(
    @Lazy CredentialsLifecycleHandler<NetflixAmazonCredentials> eventHandler
  ) {
    return new MapBackedCredentialsRepository<NetflixAmazonCredentials>(AmazonCloudProvider.ID, eventHandler)
  }

  @Bean
  @ConditionalOnMissingBean(
    name = "amazonCredentialsLoader"
  )
  AbstractCredentialsLoader<? extends NetflixAmazonCredentials> amazonCredentialsLoader(
    CredentialsParser<Account, NetflixAmazonCredentials>  amazonCredentialsParser,
    @Nullable CredentialsDefinitionSource<Account> amazonCredentialsSource,
    CredentialsConfig credentialsConfig,
    CredentialsRepository<NetflixAmazonCredentials> repository,
    DefaultAccountConfigurationProperties defaultAccountConfigurationProperties
  ) {
    if (amazonCredentialsSource == null) {
      amazonCredentialsSource = { -> credentialsConfig.getAccounts() } as CredentialsDefinitionSource
    }
    return new AmazonBasicCredentialsLoader<Account, NetflixAmazonCredentials>(
      amazonCredentialsSource,
      amazonCredentialsParser,
      repository,
      credentialsConfig,
      defaultAccountConfigurationProperties
    )
  }

  @Bean
  @ConditionalOnMissingBean(
    name = "amazonCredentialsInitializerSynchronizable"
  )
  CredentialsInitializerSynchronizable amazonCredentialsInitializerSynchronizable(
    AbstractCredentialsLoader<? extends NetflixAmazonCredentials> amazonCredentialsLoader
  ) {
    final Poller<? extends NetflixAmazonCredentials> poller = new Poller<>(amazonCredentialsLoader);
    return new CredentialsInitializerSynchronizable() {
      @Override
      void synchronize() {
        poller.run()
      }
    }
  }
}
