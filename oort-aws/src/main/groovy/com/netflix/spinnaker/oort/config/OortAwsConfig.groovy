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
import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.amazoncomponents.data.AmazonObjectMapper
import com.netflix.amazoncomponents.security.AmazonClientProvider
import com.netflix.spinnaker.oort.data.aws.cachers.InfrastructureCachingAgent
import com.netflix.spinnaker.oort.data.aws.cachers.InfrastructureCachingAgentFactory
import com.netflix.spinnaker.oort.security.NamedAccountProvider
import com.netflix.spinnaker.oort.security.aws.AmazonNamedAccount
import groovy.transform.CompileStatic
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.ApplicationContext
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.stereotype.Component
import org.springframework.web.client.RestTemplate

import javax.annotation.PostConstruct

@CompileStatic
@Configuration
class OortAwsConfig {

  static class ManagedAccount {
    String name
    String edda
    String atlasHealth
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
  @ConditionalOnMissingBean(RestTemplate)
  RestTemplate restTemplate() {
    new RestTemplate()
  }

  @Bean
  ObjectMapper amazonObjectMapper() {
    new AmazonObjectMapper()
  }

  @Configuration
  static class AmazonInitializer {
    @Autowired
    AWSCredentialsProvider awsCredentialsProvider

    @Autowired
    AwsConfigurationProperties awsConfigurationProperties

    @Autowired
    NamedAccountProvider namedAccountProvider

    @Autowired
    AmazonClientProvider amazonClientProvider

    @Autowired
    ApplicationContext applicationContext

    @PostConstruct
    void init() {
      for (account in awsConfigurationProperties.accounts) {
        def namedAccount = new AmazonNamedAccount(awsCredentialsProvider, account.name, account.edda, account.atlasHealth, account.front50, account.discovery, account.regions)
        namedAccountProvider.put(namedAccount)
        for (region in namedAccount.regions) {
          autowireAndInitialize InfrastructureCachingAgentFactory.getImageCachingAgent(namedAccount, region)
          autowireAndInitialize InfrastructureCachingAgentFactory.getClusterCachingAgent(namedAccount, region)
          autowireAndInitialize InfrastructureCachingAgentFactory.getInstanceCachingAgent(namedAccount, region)
          autowireAndInitialize InfrastructureCachingAgentFactory.getAtlasHealthCachingAgent(namedAccount, region)
          autowireAndInitialize InfrastructureCachingAgentFactory.getLaunchConfigCachingAgent(namedAccount, region)
          autowireAndInitialize InfrastructureCachingAgentFactory.getLoadBalancerCachingAgent(namedAccount, region)
        }
      }
    }

    private void autowireAndInitialize(InfrastructureCachingAgent agent) {
      applicationContext.autowireCapableBeanFactory.autowireBean(agent)
      agent.init()
    }
  }
}
