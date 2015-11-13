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
package com.netflix.spinnaker.kato.config

import com.netflix.spinnaker.clouddriver.security.AccountCredentialsRepository
import com.netflix.spinnaker.kato.cf.deploy.handlers.CloudFoundryDeployHandler
import com.netflix.spinnaker.kato.cf.security.CloudFoundryAccountCredentials
import com.netflix.spinnaker.kato.cf.security.CloudFoundryClientFactory
import com.netflix.spinnaker.kato.cf.security.DefaultCloudFoundryClientFactory
import com.netflix.spinnaker.kato.deploy.DeployHandler
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.Configuration

import javax.annotation.PostConstruct

/**
 * Configure the components for a Cloud Foundry configuration.
 *
 *
 */
@ConditionalOnProperty('cf.enabled')
@Configuration
@EnableConfigurationProperties
@ComponentScan('com.netflix.spinnaker.kato.cf')
class KatoCloudFoundryConfig {

  // TODO Add other critical beans needed at the top (cloudfoundry beans?)

  @Bean
  CloudFoundryConfigurationProperties cfConfigurationProperties() {
    new CloudFoundryConfigurationProperties();
  }

  @Bean
  CloudFoundryCredentialsInitializer cloudFoundryCredentialsInitializer() {
    new CloudFoundryCredentialsInitializer();
  }

  @Bean
  @ConditionalOnMissingBean(CloudFoundryClientFactory)
  CloudFoundryClientFactory cloudFoundryClientFactory() {
    new DefaultCloudFoundryClientFactory()
  }

  @Bean
  @ConditionalOnMissingBean(CloudFoundryDeployHandler)
  DeployHandler deployHandler(CloudFoundryClientFactory clientFactory, @Value('${cf.jenkins.username}') String username,
                              @Value('${cf.jenkins.password}') String password) {
    new CloudFoundryDeployHandler(clientFactory, username, password)
  }

  @ConfigurationProperties('cf')
  static class CloudFoundryConfigurationProperties {
    List<CloudFoundryAccountCredentials> accounts = []
  }

  static class CloudFoundryCredentialsInitializer {
    @Autowired
    CloudFoundryConfigurationProperties cfConfigurationProperties

    @Autowired
    AccountCredentialsRepository accountCredentialsRepository

    @PostConstruct
    void init() {
      cfConfigurationProperties?.accounts?.each { account ->
        accountCredentialsRepository?.save(account.name, account)
      }
    }
  }
}
