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

package com.netflix.spinnaker.oort.aws.provider.config

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.amazoncomponents.security.AmazonClientProvider
import com.netflix.spinnaker.amos.AccountCredentials
import com.netflix.spinnaker.amos.AccountCredentialsRepository
import com.netflix.spinnaker.amos.aws.AmazonCredentials
import com.netflix.spinnaker.amos.aws.NetflixAmazonCredentials
import com.netflix.spinnaker.cats.agent.CachingAgent
import com.netflix.spinnaker.oort.aws.discovery.DiscoveryApiFactory
import com.netflix.spinnaker.oort.aws.edda.EddaApiFactory
import com.netflix.spinnaker.oort.aws.provider.AwsProvider
import com.netflix.spinnaker.oort.aws.provider.agent.*
import org.springframework.context.ApplicationContext
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.DependsOn

@Configuration
class AwsProviderConfig {

  @Bean
  @DependsOn('netflixAmazonCredentials')
  AwsProvider awsProvider(AmazonClientProvider amazonClientProvider, AccountCredentialsRepository accountCredentialsRepository, ObjectMapper objectMapper, DiscoveryApiFactory discoveryApiFactory, EddaApiFactory eddaApiFactory, ApplicationContext ctx) {
    Map<String, Map<String, List<NetflixAmazonCredentials>>> discoveryAccounts = [:].withDefault { [:].withDefault { [] } }
    List<CachingAgent> agents = []

    for (AccountCredentials cred : accountCredentialsRepository.all) {
      if (!(cred instanceof NetflixAmazonCredentials)) {
        continue
      }
      NetflixAmazonCredentials credentials = (NetflixAmazonCredentials) cred
      for (AmazonCredentials.AWSRegion region : credentials.regions) {
        agents << new ClusterCachingAgent(amazonClientProvider, credentials, region.name, objectMapper)
        agents << new LaunchConfigCachingAgent(amazonClientProvider, credentials, region.name, objectMapper)
        agents << new ImageCachingAgent(amazonClientProvider, credentials, region.name, objectMapper)
        agents << new InstanceCachingAgent(amazonClientProvider, credentials, region.name, objectMapper)
        agents << new LoadBalancerCachingAgent(amazonClientProvider, credentials, region.name, objectMapper)
        if (credentials.eddaEnabled) {
          agents << new EddaLoadBalancerCachingAgent(eddaApiFactory.createApi(credentials.edda, region.name), credentials, region.name, objectMapper)
        } else {
          def agent = new AmazonLoadBalancerInstanceStateCachingAgent(amazonClientProvider, credentials, region.name, objectMapper)
          ctx.autowireCapableBeanFactory.autowireBean(agent)
          agents << agent
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
