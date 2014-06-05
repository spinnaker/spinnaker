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
import com.netflix.spinnaker.oort.model.aws.AmazonApplication
import com.netflix.spinnaker.oort.model.aws.AmazonCluster
import com.netflix.spinnaker.oort.security.NamedAccountProvider
import com.netflix.spinnaker.oort.security.aws.AmazonNamedAccount
import groovy.transform.CompileStatic
import org.apache.directmemory.DirectMemory
import org.apache.directmemory.cache.CacheService
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.stereotype.Component
import org.springframework.web.client.RestTemplate

import javax.annotation.PostConstruct
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

@CompileStatic
@Configuration
class OortAwsConfig {

  static class ManagedAccount {
    String name
    String edda
    String front50
    String discovery
    List<String> regions
  }

  @Component
  @ConfigurationProperties("aws")
  static class AwsConfigurationProperties {
    List<ManagedAccount> accounts
  }

  @Bean
  AmazonClientProvider amazonClientProvider() {
    new AmazonClientProvider()
  }

  @Bean
  ExecutorService taskExecutor() {
    Executors.newFixedThreadPool(20)
  }

  @Bean
  CacheService<String, AmazonCluster> clusterCacheService() {
    new DirectMemory<String, AmazonCluster>().setNumberOfBuffers(1).setSize(50000000).setInitialCapacity(1000000).setConcurrencyLevel(1).setDisposalTime(600000).newCacheService()
  }

  @Bean
  CacheService<String, AmazonApplication> applicationCacheService() {
    new DirectMemory<String, AmazonApplication>().setNumberOfBuffers(1).setSize(50000000).setInitialCapacity(1000000).setConcurrencyLevel(1).setDisposalTime(600000).newCacheService()
  }

  @Bean
  @ConditionalOnMissingBean(RestTemplate)
  RestTemplate restTemplate() {
    new RestTemplate()
  }

  @Configuration
  static class AmazonCredentialsInitializer {
    @Autowired
    AWSCredentialsProvider awsCredentialsProvider

    @Autowired
    AwsConfigurationProperties awsConfigurationProperties

    @Autowired
    NamedAccountProvider namedAccountProvider

    @Autowired
    AmazonClientProvider amazonClientProvider

    @PostConstruct
    void init() {
      for (account in awsConfigurationProperties.accounts) {
        namedAccountProvider.put(new AmazonNamedAccount(awsCredentialsProvider, account.name, account.edda, account.front50, account.discovery, account.regions))
      }
    }
  }
}
