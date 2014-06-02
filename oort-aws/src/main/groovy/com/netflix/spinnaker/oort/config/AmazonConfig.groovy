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

package com.netflix.spinnaker.oort.config

import com.amazonaws.auth.AWSCredentialsProvider
import com.netflix.amazoncomponents.security.AmazonClientProvider
import com.netflix.spinnaker.oort.remoting.DiscoverableRemoteResource
import com.netflix.spinnaker.oort.remoting.RemoteResource
import com.netflix.spinnaker.oort.security.NamedAccountCredentialsProvider
import com.netflix.spinnaker.oort.security.aws.AmazonAccountObject
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.stereotype.Component

import javax.annotation.PostConstruct

@Configuration
class AmazonConfig {
  @Value('${discovery.url.format:#{null}}')
  String discoveryUrlFormat

  @Bean
  RemoteResource front50() {
    def appName = "front50"
    new DiscoverableRemoteResource(appName, String.format(discoveryUrlFormat, "us-west-1", appName))
  }

  @Bean
  AmazonClientProvider amazonClientProvider() {
    new AmazonClientProvider()
  }

  static class ManagedAccount {
    String name
    String edda
    String discovery
    List<String> regions
  }

  @Component
  @ConfigurationProperties("aws")
  static class AwsConfigurationProperties {
    List<ManagedAccount> accounts
  }

  @Configuration
  static class AmazonCredentialsInitializer {
    @Autowired
    AWSCredentialsProvider awsCredentialsProvider

    @Autowired
    AwsConfigurationProperties awsConfigurationProperties

    @Autowired
    NamedAccountCredentialsProvider namedAccountCredentialsProvider

    @Autowired
    AmazonClientProvider amazonClientProvider

    @PostConstruct
    void init() {
      for (account in awsConfigurationProperties.accounts) {
        namedAccountCredentialsProvider.putAccount(new AmazonAccountObject(awsCredentialsProvider, account.name, account.edda, account.discovery, account.regions))
      }
    }
  }
}
