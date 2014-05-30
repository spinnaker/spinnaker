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


package com.netflix.spinnaker.kato.deploy.gce.config

import com.netflix.spinnaker.kato.security.NamedAccountCredentialsHolder
import com.netflix.spinnaker.kato.security.gce.GoogleNamedAccountCredentials
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.stereotype.Component

import javax.annotation.PostConstruct

@Component
class GoogleConfig {
  @Autowired
  NamedAccountCredentialsHolder namedAccountCredentialsHolder

  static class ManagedAccount {
    String name
    String project
    String pkcs12Password
  }

  @Component
  @ConfigurationProperties("google")
  static class GoogleConfigurationProperties {
    String kmsServer
    List<ManagedAccount> accounts = []
  }

  @Autowired
  GoogleConfigurationProperties googleConfigurationProperties

  @PostConstruct
  void init() {
    for (managedAccount in googleConfigurationProperties.accounts) {
      namedAccountCredentialsHolder.put(managedAccount.name, new GoogleNamedAccountCredentials(googleConfigurationProperties.kmsServer, managedAccount.pkcs12Password, managedAccount.project))
    }
  }
}
