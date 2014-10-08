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

package com.netflix.spinnaker.mort.config

import com.netflix.amazoncomponents.security.AmazonClientProvider
import com.netflix.spinnaker.amos.AccountCredentialsRepository
import com.netflix.spinnaker.amos.aws.NetflixAmazonCredentials
import com.netflix.spinnaker.mort.aws.cache.AmazonInstanceTypeCachingAgent
import com.netflix.spinnaker.mort.aws.cache.AmazonKeyPairCachingAgent
import com.netflix.spinnaker.mort.aws.cache.AmazonSecurityGroupCachingAgent
import com.netflix.spinnaker.mort.aws.cache.AmazonSubnetCachingAgent
import com.netflix.spinnaker.mort.aws.cache.AmazonVpcCachingAgent
import com.netflix.spinnaker.mort.model.CacheService
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class AWSCachingAgentConfigurator {

  @Bean
  Void init(AmazonClientProvider clientProvider,
            CacheService cacheService,
            AccountCredentialsRepository accountCredentialsRepository,
            ConfigurableListableBeanFactory beanFactory) {
    for (a in accountCredentialsRepository.all) {
      if (!NetflixAmazonCredentials.isAssignableFrom(a.class)) {
        continue
      }
      def account = (NetflixAmazonCredentials)a
      for (region in account.regions) {
        def ec2 = clientProvider.getAmazonEC2(account, region.name)
        beanFactory.registerSingleton("securityGroupCacher-${account.name}-${region.name}",
            new AmazonSecurityGroupCachingAgent(account.name, region.name, ec2, cacheService))
        beanFactory.registerSingleton("subnetCacher-${account.name}-${region.name}",
            new AmazonSubnetCachingAgent(account.name, region.name, ec2, cacheService))
        beanFactory.registerSingleton("vpcCacher-${account.name}-${region.name}",
            new AmazonVpcCachingAgent(account.name, region.name, ec2, cacheService))
        beanFactory.registerSingleton("keyPairCacher-${account.name}-${region.name}",
            new AmazonKeyPairCachingAgent(account.name, region.name, ec2, cacheService))
        beanFactory.registerSingleton("instanceTypeCacher-${account.name}-${region.name}",
            new AmazonInstanceTypeCachingAgent(account.name, region.name, ec2, cacheService))
      }
    }
    null
  }
}
