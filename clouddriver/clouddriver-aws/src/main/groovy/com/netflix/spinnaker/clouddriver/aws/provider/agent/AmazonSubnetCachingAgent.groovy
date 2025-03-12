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

package com.netflix.spinnaker.clouddriver.aws.provider.agent

import com.amazonaws.services.ec2.model.Subnet
import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.cats.agent.*
import com.netflix.spinnaker.cats.cache.CacheData
import com.netflix.spinnaker.cats.cache.DefaultCacheData
import com.netflix.spinnaker.cats.provider.ProviderCache
import com.netflix.spinnaker.clouddriver.aws.cache.Keys
import com.netflix.spinnaker.clouddriver.aws.provider.AwsInfrastructureProvider
import com.netflix.spinnaker.clouddriver.aws.security.AmazonClientProvider
import com.netflix.spinnaker.clouddriver.aws.security.NetflixAmazonCredentials
import groovy.util.logging.Slf4j

import static com.netflix.spinnaker.cats.agent.AgentDataType.Authority.AUTHORITATIVE
import static com.netflix.spinnaker.clouddriver.aws.cache.Keys.Namespace.SUBNETS

@Slf4j
class AmazonSubnetCachingAgent implements CachingAgent, AccountAware {

  final AmazonClientProvider amazonClientProvider
  final NetflixAmazonCredentials account
  final String region
  final ObjectMapper amazonObjectMapper

  static final Set<AgentDataType> types = Collections.unmodifiableSet([
    AUTHORITATIVE.forType(SUBNETS.ns)
  ] as Set)

  AmazonSubnetCachingAgent(AmazonClientProvider amazonClientProvider,
                           NetflixAmazonCredentials account,
                           String region,
                           ObjectMapper amazonObjectMapper) {
    this.amazonClientProvider = amazonClientProvider
    this.account = account
    this.region = region
    this.amazonObjectMapper = amazonObjectMapper
  }

  @Override
  String getProviderName() {
    AwsInfrastructureProvider.PROVIDER_NAME
  }

  @Override
  String getAgentType() {
    "${account.name}/${region}/${AmazonSubnetCachingAgent.simpleName}"
  }

  @Override
  String getAccountName() {
    account.name
  }

  @Override
  Collection<AgentDataType> getProvidedDataTypes() {
    return types
  }

  @Override
  CacheResult loadData(ProviderCache providerCache) {
    log.info("Describing items in ${agentType}")
    def ec2 = amazonClientProvider.getAmazonEC2(account, region)
    def subnets = ec2.describeSubnets().subnets

    List<CacheData> data = subnets.collect { Subnet subnet ->
      Map<String, Object> attributes = amazonObjectMapper.convertValue(subnet, AwsInfrastructureProvider.ATTRIBUTES)
      attributes.putIfAbsent("accountId", account.accountId)
      new DefaultCacheData(Keys.getSubnetKey(subnet.subnetId, region, account.name),
        attributes,
        [:])
    }
    log.info("Caching ${data.size()} items in ${agentType}")
    new DefaultCacheResult([(SUBNETS.ns): data])
  }
}
