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


package com.netflix.spinnaker.kato.gce.deploy.config

import com.netflix.spinnaker.amos.AccountCredentialsRepository
import com.netflix.spinnaker.amos.gce.GoogleNamedAccountCredentials
import com.netflix.spinnaker.kato.gce.deploy.GoogleOperationPoller
import org.apache.log4j.Logger
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.stereotype.Component

import javax.annotation.PostConstruct

@Component
class GoogleConfig {
  private static final Logger log = Logger.getLogger(this.class.simpleName)

  public static final int ASYNC_OPERATION_TIMEOUT_SECONDS_DEFAULT = 300
  public static final int ASYNC_OPERATION_MAX_POLLING_INTERVAL_SECONDS = 8

  @Autowired
  AccountCredentialsRepository accountCredentialsRepository

  @Bean
  GoogleOperationPoller googleOperationPoller() {
    new GoogleOperationPoller()
  }

  static class ManagedAccount {
    String name
    String project
  }

  @Component
  @ConfigurationProperties("google")
  static class GoogleConfigurationProperties {
    String kmsServer
    List<ManagedAccount> accounts = []
    int asyncOperationTimeoutSecondsDefault = ASYNC_OPERATION_TIMEOUT_SECONDS_DEFAULT
    int asyncOperationMaxPollingIntervalSeconds = ASYNC_OPERATION_MAX_POLLING_INTERVAL_SECONDS
  }

  @Autowired
  GoogleConfigurationProperties googleConfigurationProperties

  @PostConstruct
  void init() {
    for (managedAccount in googleConfigurationProperties.accounts) {
      try {
        accountCredentialsRepository.save(managedAccount.name, new GoogleNamedAccountCredentials(googleConfigurationProperties.kmsServer, managedAccount.name, managedAccount.project))
      } catch (e) {
        log.info "Could not load account ${managedAccount.name} for Google", e
      }
    }
  }
}
