/*
 * Copyright 2014 Netflix, Inc.
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

import com.netflix.spinnaker.amos.AccountCredentialsRepository
import com.netflix.spinnaker.kato.docker.security.DockerAccountCredentials
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.Configuration
import org.springframework.web.client.RestTemplate

import javax.annotation.PostConstruct

@ConditionalOnProperty('docker.enabled')
@Configuration
@EnableConfigurationProperties
@ComponentScan('com.netflix.spinnaker.docker')
class KatoDockerConfig {

  @Bean
  @ConditionalOnMissingBean(RestTemplate)
  RestTemplate restTemplate() {
    new RestTemplate()
  }

  @Bean
  DockerConfigurationProperties dockerConfigurationProperties() {
    new DockerConfigurationProperties()
  }

  @Bean
  DockerCredentialsInitializer dockerCredentialsInitializer() {
    new DockerCredentialsInitializer()
  }

  @ConfigurationProperties("docker")
  static class DockerConfigurationProperties {
    List<DockerAccountCredentials> accounts
  }

  static class DockerCredentialsInitializer {
    @Autowired
    DockerConfigurationProperties dockerConfigurationProperties

    @Autowired
    AccountCredentialsRepository accountCredentialsRepository

    @PostConstruct
    void init() {
      for (account in dockerConfigurationProperties.accounts) {
        accountCredentialsRepository.save(account.name, account)
      }
    }
  }
}
