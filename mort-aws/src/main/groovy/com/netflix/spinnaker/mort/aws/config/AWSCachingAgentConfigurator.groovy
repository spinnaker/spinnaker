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

package com.netflix.spinnaker.mort.aws.config

import com.amazonaws.services.ec2.AmazonEC2
import com.netflix.amazoncomponents.security.AmazonClientProvider
import com.netflix.spinnaker.amos.AccountCredentialsRepository
import com.netflix.spinnaker.amos.aws.NetflixAmazonCredentials
import com.netflix.spinnaker.mort.aws.cache.*
import com.netflix.spinnaker.mort.model.CacheService
import com.netflix.spinnaker.mort.model.CachingAgent
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.DependsOn

@Configuration
class AWSCachingAgentConfigurator {

    @Bean
    @DependsOn('netflixAmazonCredentials')
    Map<String, CachingAgent> cachingAgents(AmazonClientProvider clientProvider,
                                     CacheService cacheService,
                                     AccountCredentialsRepository accountCredentialsRepository,
                                     ConfigurableListableBeanFactory beanFactory) {
        Map<String, CachingAgent> agents = [:]
        for (a in accountCredentialsRepository.all) {
            if (!NetflixAmazonCredentials.isAssignableFrom(a.class)) {
                continue
            }
            def account = (NetflixAmazonCredentials) a
            for (region in account.regions) {
                AmazonEC2 ec2 = clientProvider.getAmazonEC2(account, region.name)
                agents["securityGroupCacher-${account.name}-${region.name}".toString()] = new AmazonSecurityGroupCachingAgent(account.name, region.name, ec2, cacheService)
                agents["subnetCacher-${account.name}-${region.name}".toString()] = new AmazonSubnetCachingAgent(account.name, region.name, ec2, cacheService)
                agents["vpcCacher-${account.name}-${region.name}".toString()] = new AmazonVpcCachingAgent(account.name, region.name, ec2, cacheService)
                agents["keyPairCacher-${account.name}-${region.name}".toString()] = new AmazonKeyPairCachingAgent(account.name, region.name, ec2, cacheService)
                agents["instanceTypeCacher-${account.name}-${region.name}".toString()] = new AmazonInstanceTypeCachingAgent(account.name, region.name, ec2, cacheService)
                agents["elasticIpCacher-${account.name}-${region.name}".toString()] = new AmazonElasticIpCachingAgent(account.name, region.name, ec2, cacheService)
            }
        }

        agents.each { k, v -> beanFactory.registerSingleton(k, v)}

        agents
    }
}
