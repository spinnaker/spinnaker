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

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.amazoncomponents.data.AmazonObjectMapper
import com.netflix.amazoncomponents.security.AmazonClientProvider
import com.netflix.spinnaker.amos.AccountCredentialsRepository
import com.netflix.spinnaker.amos.aws.NetflixAmazonCredentials
import com.netflix.spinnaker.oort.config.discovery.DiscoveryApiFactory
import com.netflix.spinnaker.oort.config.edda.EddaApiFactory
import com.netflix.spinnaker.oort.data.aws.cachers.InfrastructureCachingAgent
import com.netflix.spinnaker.oort.data.aws.cachers.InfrastructureCachingAgentFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.context.ApplicationContext
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.client.RestTemplate

import javax.annotation.PostConstruct

@Configuration
class OortAwsConfig {

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
    AccountCredentialsRepository accountCredentialsRepository

    @Autowired
    ApplicationContext applicationContext

    @Autowired
    DiscoveryApiFactory discoveryApiFactory

    @Autowired
    EddaApiFactory eddaApiFactory

    // This is just so Spring gets the dependency graph right
    @Autowired
    CredentialsInitializer credentialsInitializer

    @PostConstruct
    void init() {
      Map<String, Map<String, List<NetflixAmazonCredentials>>> discoveryAccounts = [:].withDefault { [:].withDefault { [] } }
      for (a in accountCredentialsRepository.all) {
        if (!NetflixAmazonCredentials.isAssignableFrom(a.class)) {
          continue
        }
        NetflixAmazonCredentials account = (NetflixAmazonCredentials) a
        if (account.front50Enabled) {
          autowireAndInitialize InfrastructureCachingAgentFactory.getFront50CachingAgent(account)
        }
        for (region in (account?.regions ?: [])) {
          autowireAndInitialize InfrastructureCachingAgentFactory.getImageCachingAgent(account, region.name)
          autowireAndInitialize InfrastructureCachingAgentFactory.getClusterCachingAgent(account, region.name)
          autowireAndInitialize InfrastructureCachingAgentFactory.getInstanceCachingAgent(account, region.name)
          if (account.eddaEnabled) {
            autowireAndInitialize InfrastructureCachingAgentFactory.getEddaLoadBalancerCachingAgent(account, region.name, eddaApiFactory)
          }
          autowireAndInitialize InfrastructureCachingAgentFactory.getLaunchConfigCachingAgent(account, region.name)
          autowireAndInitialize InfrastructureCachingAgentFactory.getLoadBalancerCachingAgent(account, region.name)
          if (account.discoveryEnabled) {
            discoveryAccounts[account.discovery][region.name].add(account)
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
