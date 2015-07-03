/*
 * Copyright 2015 Google, Inc.
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

package com.netflix.spinnaker.clouddriver.google.config

import com.netflix.spinnaker.amos.AccountCredentialsRepository
import com.netflix.spinnaker.amos.gce.GoogleNamedAccountCredentials
import com.netflix.spinnaker.clouddriver.google.health.GoogleHealthIndicator
import org.apache.log4j.Logger
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.annotation.EnableScheduling

import javax.annotation.PostConstruct

@Configuration
@EnableConfigurationProperties
@EnableScheduling
@ConditionalOnProperty('google.enabled')
class GoogleConfig {
  private static final Logger log = Logger.getLogger(this.class.simpleName)

  @Autowired
  AccountCredentialsRepository accountCredentialsRepository

  @Bean
  @ConfigurationProperties("google")
  GoogleConfigurationProperties googleConfigurationProperties() {
    new GoogleConfigurationProperties()
  }

  @Bean
  GoogleHealthIndicator googleHealthIndicator() {
    new GoogleHealthIndicator()
  }

  @PostConstruct
  void init() {
    def config = googleConfigurationProperties()
    for (managedAccount in config.accounts) {
      try {
        accountCredentialsRepository.save(managedAccount.name, new GoogleNamedAccountCredentials(config.kmsServer, managedAccount.name, managedAccount.project))
      } catch (e) {
        log.info "Could not load account ${managedAccount.name} for Google", e
      }
    }
  }
}

