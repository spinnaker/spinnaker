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
import com.netflix.spinnaker.kato.security.docker.DockerAccountCredentials
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.stereotype.Component
import org.springframework.web.client.RestTemplate

import javax.annotation.PostConstruct

@Configuration
class KatoDockerConfig {

  @Bean
  @ConditionalOnMissingBean(RestTemplate)
  RestTemplate restTemplate() {
    new RestTemplate()
  }

  @Component
  @ConfigurationProperties("docker")
  static class DockerConfigurationProperties {
    List<DockerAccountCredentials> accounts
  }

  @Component
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
