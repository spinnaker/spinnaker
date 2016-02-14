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

import com.amazonaws.services.ec2.model.Address
import com.netflix.spinnaker.cats.agent.AccountAware
import com.netflix.spinnaker.cats.agent.AgentDataType
import com.netflix.spinnaker.cats.agent.CacheResult
import com.netflix.spinnaker.cats.agent.CachingAgent
import com.netflix.spinnaker.cats.agent.DefaultCacheResult
import com.netflix.spinnaker.cats.cache.CacheData
import com.netflix.spinnaker.cats.cache.DefaultCacheData
import com.netflix.spinnaker.cats.provider.ProviderCache
import com.netflix.spinnaker.clouddriver.aws.AmazonCloudProvider
import com.netflix.spinnaker.clouddriver.aws.security.AmazonClientProvider
import com.netflix.spinnaker.clouddriver.aws.security.NetflixAmazonCredentials
import com.netflix.spinnaker.clouddriver.aws.cache.Keys
import com.netflix.spinnaker.clouddriver.aws.provider.AwsInfrastructureProvider

import static com.netflix.spinnaker.cats.agent.AgentDataType.Authority.AUTHORITATIVE
import static com.netflix.spinnaker.clouddriver.aws.cache.Keys.Namespace.ELASTIC_IPS

import groovy.util.logging.Slf4j

@Slf4j
class AmazonElasticIpCachingAgent implements CachingAgent, AccountAware {

  final AmazonCloudProvider amazonCloudProvider
  final AmazonClientProvider amazonClientProvider
  final NetflixAmazonCredentials account
  final String region

  static final Set<AgentDataType> types = Collections.unmodifiableSet([
    AUTHORITATIVE.forType(ELASTIC_IPS.ns)
  ] as Set)

  AmazonElasticIpCachingAgent(AmazonCloudProvider amazonCloudProvider,
                              AmazonClientProvider amazonClientProvider,
                              NetflixAmazonCredentials account,
                              String region) {
    this.amazonCloudProvider = amazonCloudProvider
    this.amazonClientProvider = amazonClientProvider
    this.account = account
    this.region = region
  }

  @Override
  String getProviderName() {
    AwsInfrastructureProvider.PROVIDER_NAME
  }

  @Override
  String getAgentType() {
    "${account.name}/${region}/${AmazonElasticIpCachingAgent.simpleName}"
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
    def eips = ec2.describeAddresses().addresses

    List<CacheData> data = eips.collect { Address address ->
      new DefaultCacheData(Keys.getElasticIpKey(amazonCloudProvider, address.publicIp, region, account.name), [
        address     : address.publicIp,
        domain      : address.domain,
        attachedToId: address.instanceId,
        accountName : account.name,
        region      : region
      ], [:])
    }
    log.info("Caching ${data.size()} items in ${agentType}")
    new DefaultCacheResult([(ELASTIC_IPS.ns): data])
  }
}
