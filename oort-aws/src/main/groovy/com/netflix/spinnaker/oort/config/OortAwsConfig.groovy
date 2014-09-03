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
import com.netflix.spinnaker.amos.AccountCredentialsRepository
import com.netflix.spinnaker.amos.aws.NetflixAmazonCredentials
import com.netflix.spinnaker.oort.config.atlas.AtlasHealthApiFactory
import com.netflix.spinnaker.oort.config.discovery.DiscoveryApiFactory
import com.netflix.spinnaker.oort.data.aws.cachers.InfrastructureCachingAgent
import com.netflix.spinnaker.oort.data.aws.cachers.InfrastructureCachingAgentFactory
import com.netflix.spinnaker.oort.security.aws.OortNetflixAmazonCredentials
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

  @Component
  @ConfigurationProperties("aws")
  static class AwsConfigurationProperties {
    List<OortNetflixAmazonCredentials> accounts
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
    AccountCredentialsRepository accountCredentialsRepository

    @Autowired
    AmazonClientProvider amazonClientProvider

    @Autowired
    ApplicationContext applicationContext

    @Autowired
    DiscoveryApiFactory discoveryApiFactory

    @Autowired
    AtlasHealthApiFactory atlasHealthApiFactory

    @PostConstruct
    void init() {
      Map<String, Map<String, List<NetflixAmazonCredentials>>> discoveryAccounts = [:].withDefault { [:].withDefault { [] }}
      for (namedAccount in awsConfigurationProperties.accounts) {
        namedAccount.credentialsProvider = awsCredentialsProvider
        accountCredentialsRepository.save(namedAccount.name, namedAccount)
        for (region in namedAccount.regions) {
          autowireAndInitialize InfrastructureCachingAgentFactory.getImageCachingAgent(namedAccount, region.name)
          autowireAndInitialize InfrastructureCachingAgentFactory.getClusterCachingAgent(namedAccount, region.name)
          autowireAndInitialize InfrastructureCachingAgentFactory.getInstanceCachingAgent(namedAccount, region.name)
          if (namedAccount.atlasHealth) {
            autowireAndInitialize InfrastructureCachingAgentFactory.getAtlasHealthCachingAgent(namedAccount, region.name, atlasHealthApiFactory)
          }
          autowireAndInitialize InfrastructureCachingAgentFactory.getLaunchConfigCachingAgent(namedAccount, region.name)
          autowireAndInitialize InfrastructureCachingAgentFactory.getLoadBalancerCachingAgent(namedAccount, region.name)
          if (namedAccount.discovery) {
            discoveryAccounts[namedAccount.discovery][region.name].add(namedAccount)
          }
        }
      }
      discoveryAccounts.each { disco, actMap ->
        actMap.each { region, accounts ->
          autowireAndInitialize InfrastructureCachingAgentFactory.getDiscoveryCachingAgent(accounts, region, discoveryApiFactory)
        }
      }
    }

    private void autowireAndInitialize(InfrastructureCachingAgent agent) {
      applicationContext.autowireCapableBeanFactory.autowireBean(agent)
      agent.init()
    }
  }
}
