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

package com.netflix.spinnaker.oort.provider.aws.config

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.amazoncomponents.security.AmazonClientProvider
import com.netflix.spinnaker.amos.aws.AmazonCredentials
import com.netflix.spinnaker.amos.aws.NetflixAmazonCredentials
import com.netflix.spinnaker.cats.agent.CachingAgent
import com.netflix.spinnaker.oort.config.AwsConfigurationProperties
import com.netflix.spinnaker.oort.config.CredentialsInitializer
import com.netflix.spinnaker.oort.config.discovery.DiscoveryApiFactory
import com.netflix.spinnaker.oort.config.edda.EddaApiFactory
import com.netflix.spinnaker.oort.model.OnDemandCacheUpdater
import com.netflix.spinnaker.oort.provider.aws.AwsProvider
import com.netflix.spinnaker.oort.provider.aws.agent.ClusterCachingAgent
import com.netflix.spinnaker.oort.provider.aws.agent.DiscoveryCachingAgent
import com.netflix.spinnaker.oort.provider.aws.agent.EddaLoadBalancerCachingAgent
import com.netflix.spinnaker.oort.provider.aws.agent.Front50CachingAgent
import com.netflix.spinnaker.oort.provider.aws.agent.ImageCachingAgent
import com.netflix.spinnaker.oort.provider.aws.agent.InstanceCachingAgent
import com.netflix.spinnaker.oort.provider.aws.agent.LaunchConfigCachingAgent
import com.netflix.spinnaker.oort.provider.aws.agent.LoadBalancerCachingAgent
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.ConfigurableApplicationContext
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.client.RestTemplate

@Configuration
class AwsProviderConfig {

  // This is just so Spring gets the dependency graph right
  @Autowired
  CredentialsInitializer credentialsInitializer

  @Bean
  AwsProvider awsProvider(AmazonClientProvider amazonClientProvider, AwsConfigurationProperties awsConfigurationProperties, ObjectMapper objectMapper, DiscoveryApiFactory discoveryApiFactory, EddaApiFactory eddaApiFactory, RestTemplate restTemplate) {
    Map<String, Map<String, List<NetflixAmazonCredentials>>> discoveryAccounts = [:].withDefault { [:].withDefault { [] } }
    List<CachingAgent> agents = []
    for (NetflixAmazonCredentials credentials : awsConfigurationProperties.accounts) {
      if (credentials.front50Enabled) {
        agents << new Front50CachingAgent(credentials, restTemplate)
      }
      for (AmazonCredentials.AWSRegion region : credentials.regions) {
        agents << new ClusterCachingAgent(amazonClientProvider, credentials, region.name, objectMapper)
        agents << new LaunchConfigCachingAgent(amazonClientProvider, credentials, region.name, objectMapper)
        agents << new ImageCachingAgent(amazonClientProvider, credentials, region.name, objectMapper)
        agents << new InstanceCachingAgent(amazonClientProvider, credentials, region.name, objectMapper)
        agents << new LoadBalancerCachingAgent(amazonClientProvider, credentials, region.name, objectMapper)
        if (credentials.eddaEnabled) {
          agents << new EddaLoadBalancerCachingAgent(eddaApiFactory.createApi(credentials.edda, region.name), credentials, region.name, objectMapper)
        }
        if (credentials.discoveryEnabled) {
          discoveryAccounts[credentials.discovery][region.name].add(credentials)
        }
      }
    }
    discoveryAccounts.each { disco, actMap ->
      actMap.each { region, accounts ->
        agents << new DiscoveryCachingAgent(discoveryApiFactory.createApi(disco, region), accounts, region, objectMapper)
      }
    }

    new AwsProvider(agents)
  }



}
