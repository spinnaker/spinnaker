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

import com.amazonaws.services.ec2.AmazonEC2
import com.amazonaws.services.ec2.model.SecurityGroup
import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spectator.api.Registry
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
import com.netflix.spinnaker.clouddriver.cache.OnDemandAgent
import com.netflix.spinnaker.clouddriver.cache.OnDemandMetricsSupport
import com.netflix.spinnaker.clouddriver.aws.cache.Keys
import com.netflix.spinnaker.clouddriver.aws.provider.AwsInfrastructureProvider
import groovy.util.logging.Slf4j

import static com.netflix.spinnaker.cats.agent.AgentDataType.Authority.AUTHORITATIVE
import static com.netflix.spinnaker.clouddriver.aws.cache.Keys.Namespace.SECURITY_GROUPS

@Slf4j
class AmazonSecurityGroupCachingAgent implements CachingAgent, OnDemandAgent, AccountAware {

  final AmazonClientProvider amazonClientProvider
  final NetflixAmazonCredentials account
  final String region
  final ObjectMapper objectMapper
  final Registry registry

  final OnDemandMetricsSupport metricsSupport

  static final Set<AgentDataType> types = Collections.unmodifiableSet([
    AUTHORITATIVE.forType(SECURITY_GROUPS.ns)
  ] as Set)

  AmazonSecurityGroupCachingAgent(AmazonClientProvider amazonClientProvider,
                                  NetflixAmazonCredentials account,
                                  String region,
                                  ObjectMapper objectMapper,
                                  Registry registry) {
    this.amazonClientProvider = amazonClientProvider
    this.account = account
    this.region = region
    this.objectMapper = objectMapper
    this.registry = registry
    this.metricsSupport = new OnDemandMetricsSupport(registry, this, "${AmazonCloudProvider.AWS}:${OnDemandAgent.OnDemandType.SecurityGroup}")
  }

  @Override
  String getProviderName() {
    AwsInfrastructureProvider.PROVIDER_NAME
  }

  @Override
  String getAgentType() {
    "${account.name}/${region}/${AmazonSecurityGroupCachingAgent.simpleName}"
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
  String getOnDemandAgentType() {
    getAgentType()
  }

  @Override
  OnDemandAgent.OnDemandResult handle(ProviderCache providerCache, Map<String, ? extends Object> data) {
    if (account.name != data.account) {
      return null
    }

    if (region != data.region) {
      return null
    }

    def securityGroups = metricsSupport.readData {
      def ec2 = amazonClientProvider.getAmazonEC2(account, region, true)
      return getSecurityGroups(ec2)
    }

    CacheResult result = metricsSupport.transformData { buildCacheResult(providerCache, securityGroups) }

    new OnDemandAgent.OnDemandResult(sourceAgentType: getAgentType(), authoritativeTypes: [SECURITY_GROUPS.ns], cacheResult: result)
  }

  @Override
  boolean handles(OnDemandAgent.OnDemandType type, String cloudProvider) {
    type == OnDemandAgent.OnDemandType.SecurityGroup && cloudProvider == AmazonCloudProvider.AWS
  }

  @Override
  CacheResult loadData(ProviderCache providerCache) {
    def ec2 = amazonClientProvider.getAmazonEC2(account, region)
    buildCacheResult(providerCache, getSecurityGroups(ec2))
  }

  @Override
  Collection<Map> pendingOnDemandRequests(ProviderCache providerCache) {
    return []
  }

  private List<SecurityGroup> getSecurityGroups(AmazonEC2 amazonEC2) {
    log.info("Describing items in ${agentType}")
    amazonEC2.describeSecurityGroups().securityGroups
  }

  private CacheResult buildCacheResult(ProviderCache providerCache, List<SecurityGroup> securityGroups) {
    List<CacheData> data = securityGroups.collect { SecurityGroup securityGroup ->
      Map<String, Object> attributes = objectMapper.convertValue(securityGroup, AwsInfrastructureProvider.ATTRIBUTES)
      new DefaultCacheData(Keys.getSecurityGroupKey(securityGroup.groupName, securityGroup.groupId, region, account.name, securityGroup.vpcId),
        attributes,
        [:])
    }
    log.info("Caching ${data.size()} items in ${agentType}")
    new DefaultCacheResult([(SECURITY_GROUPS.ns): data])
  }
}
