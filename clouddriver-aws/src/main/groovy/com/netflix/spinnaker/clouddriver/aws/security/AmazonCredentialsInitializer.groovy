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
import com.netflix.spinnaker.amos.AccountCredentialsRepository
import com.netflix.spinnaker.amos.aws.NetflixAmazonCredentials
import com.netflix.spinnaker.amos.aws.NetflixAssumeRoleAmazonCredentials
import com.netflix.spinnaker.amos.aws.config.CredentialsConfig
import com.netflix.spinnaker.amos.aws.config.CredentialsLoader
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

import static com.amazonaws.regions.Regions.*

@Configuration
@EnableConfigurationProperties
class AmazonCredentialsInitializer {
  @Bean
  @ConfigurationProperties('aws')
  CredentialsConfig credentialsConfig() {
    new CredentialsConfig()
  }

  @Bean
  Class<? extends NetflixAmazonCredentials> credentialsType(CredentialsConfig credentialsConfig) {
    if (!credentialsConfig.accounts) {
      NetflixAmazonCredentials
    } else {
      NetflixAssumeRoleAmazonCredentials
    }
  }

  @Bean
  CredentialsLoader<? extends NetflixAmazonCredentials> credentialsLoader(AWSCredentialsProvider awsCredentialsProvider, Class<? extends NetflixAmazonCredentials> credentialsType) {
    new CredentialsLoader<? extends NetflixAmazonCredentials>(awsCredentialsProvider, credentialsType)
  }

  @Bean
  List<? extends NetflixAmazonCredentials> netflixAmazonCredentials(CredentialsLoader<? extends NetflixAmazonCredentials> credentialsLoader,
                                                                    CredentialsConfig credentialsConfig,
                                                                    AccountCredentialsRepository accountCredentialsRepository,
                                                                    @Value('${default.account.env:default}') String defaultEnv) {

    if (!credentialsConfig.accounts) {
      credentialsConfig.accounts = [new CredentialsConfig.Account(name: defaultEnv)]
      if (!credentialsConfig.defaultRegions) {
        credentialsConfig.defaultRegions = [US_EAST_1, US_WEST_1, US_WEST_2, EU_WEST_1].collect { new CredentialsConfig.Region(name: it.name) }
      }
    }

    List<? extends NetflixAmazonCredentials> accounts = credentialsLoader.load(credentialsConfig)

    for (act in accounts) {
      accountCredentialsRepository.save(act.name, act)
    }

    accounts
  }
}
