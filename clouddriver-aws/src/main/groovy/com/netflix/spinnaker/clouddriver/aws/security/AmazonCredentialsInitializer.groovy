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
import com.netflix.spinnaker.clouddriver.aws.security.config.CredentialsConfig
import com.netflix.spinnaker.clouddriver.aws.security.config.CredentialsLoader
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsRepository
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

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
  Class<? extends NetflixAmazonCredentials> credentialsType(CredentialsConfig credentialsConfig) {
    if (!credentialsConfig.accounts && !credentialsConfig.defaultAssumeRole) {
      NetflixAmazonCredentials
    } else {
      NetflixAssumeRoleAmazonCredentials
    }
  }

  @Bean
  @ConditionalOnMissingBean(CredentialsLoader.class)
  CredentialsLoader<? extends NetflixAmazonCredentials> credentialsLoader(AWSCredentialsProvider awsCredentialsProvider,
                                                                          AmazonClientProvider amazonClientProvider,
                                                                          Class<? extends NetflixAmazonCredentials> credentialsType) {
    new CredentialsLoader<? extends NetflixAmazonCredentials>(awsCredentialsProvider, amazonClientProvider, credentialsType)
  }

  @Bean
  @ConditionalOnMissingBean(AmazonAccountsSynchronizer.class)
  AmazonAccountsSynchronizer amazonAccountsSynchronizer() {
    new DefaultAmazonAccountsSynchronizer()
  }

  @Bean
  List<? extends NetflixAmazonCredentials> netflixAmazonCredentials(
    CredentialsLoader<? extends NetflixAmazonCredentials> credentialsLoader,
    CredentialsConfig credentialsConfig,
    AccountCredentialsRepository accountCredentialsRepository,
    DefaultAccountConfigurationProperties defaultAccountConfigurationProperties,
    AmazonAccountsSynchronizer amazonAccountsSynchronizer) {

    amazonAccountsSynchronizer.synchronize(credentialsLoader, credentialsConfig, accountCredentialsRepository, defaultAccountConfigurationProperties, null)
  }

}
