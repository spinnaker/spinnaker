/*
 * Copyright 2015 Netflix, Inc.
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

package com.netflix.spinnaker.mort.aws.provider.config

import com.netflix.amazoncomponents.security.AmazonClientProvider
import com.netflix.awsobjectmapper.AmazonObjectMapper
import com.netflix.spinnaker.amos.AccountCredentialsRepository
import com.netflix.spinnaker.amos.aws.AmazonCredentials
import com.netflix.spinnaker.amos.aws.NetflixAmazonCredentials
import com.netflix.spinnaker.cats.agent.CachingAgent
import com.netflix.spinnaker.mort.aws.provider.AwsInfrastructureProvider
import com.netflix.spinnaker.mort.aws.provider.agent.AmazonElasticIpCachingAgent
import com.netflix.spinnaker.mort.aws.provider.agent.AmazonInstanceTypeCachingAgent
import com.netflix.spinnaker.mort.aws.provider.agent.AmazonKeyPairCachingAgent
import com.netflix.spinnaker.mort.aws.provider.agent.AmazonSecurityGroupCachingAgent
import com.netflix.spinnaker.mort.aws.provider.agent.AmazonSubnetCachingAgent
import com.netflix.spinnaker.mort.aws.provider.agent.AmazonVpcCachingAgent
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.DependsOn

@Configuration
class AwsInfrastructureProviderConfig {
  @Bean
  @DependsOn('netflixAmazonCredentials')
  AwsInfrastructureProvider awsInfrastructureProvider(AmazonClientProvider amazonClientProvider, AccountCredentialsRepository accountCredentialsRepository, AmazonObjectMapper amazonObjectMapper) {
    List<CachingAgent> agents = []

    def allAccounts = accountCredentialsRepository.all.findAll {
      it instanceof NetflixAmazonCredentials
    } as Collection<NetflixAmazonCredentials>

    allAccounts.each { NetflixAmazonCredentials credentials ->
      for (AmazonCredentials.AWSRegion region : credentials.regions) {
        agents << new AmazonElasticIpCachingAgent(amazonClientProvider, credentials, region.name)
        agents << new AmazonInstanceTypeCachingAgent(amazonClientProvider, credentials, region.name)
        agents << new AmazonKeyPairCachingAgent(amazonClientProvider, credentials, region.name)
        agents << new AmazonSecurityGroupCachingAgent(amazonClientProvider, credentials, region.name, amazonObjectMapper)
        agents << new AmazonSubnetCachingAgent(amazonClientProvider, credentials, region.name, amazonObjectMapper)
        agents << new AmazonVpcCachingAgent(amazonClientProvider, credentials, region.name)
      }
    }

    new AwsInfrastructureProvider(agents)
  }

}
