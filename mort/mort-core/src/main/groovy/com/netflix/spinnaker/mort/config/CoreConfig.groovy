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

import com.netflix.spinnaker.mort.model.*
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class CoreConfig {

  @Bean
  @ConditionalOnMissingBean(InstanceTypeProvider)
  InstanceTypeProvider noopInstanceTypeProvider() {
    new NoopInstanceTypeProvider()
  }

  @Bean
  @ConditionalOnMissingBean(KeyPairProvider)
  KeyPairProvider noopKeyPairProvider() {
    new NoopKeyPairProvider()
  }

  @Bean
  @ConditionalOnMissingBean(SecurityGroupProvider)
  SecurityGroupProvider noopSecurityGroupProvider() {
    new NoopSecurityGroupProvider()
  }

  @Bean
  @ConditionalOnMissingBean(SubnetProvider)
  SubnetProvider noopSubnetProvider() {
    new NoopSubnetProvider()
  }

  @Bean
  @ConditionalOnMissingBean(VpcProvider)
  VpcProvider noopVpcProvider() {
    new NoopVpcProvider()
  }

  @Bean
  @ConditionalOnMissingBean(ElasticIpProvider)
  ElasticIpProvider noopElasticIpProvider() {
    new NoopElasticIpProvider()
  }

  @Bean
  @ConditionalOnMissingBean(CachingAgent)
  CachingAgent noopCachingAgent() {
    new NoopCachingAgent()
  }
}
