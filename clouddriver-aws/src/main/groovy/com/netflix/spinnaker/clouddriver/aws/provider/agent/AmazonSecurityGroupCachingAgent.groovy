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
import com.netflix.spinnaker.clouddriver.aws.security.EddaTimeoutConfig
import com.netflix.spinnaker.clouddriver.aws.security.NetflixAmazonCredentials
import com.netflix.spinnaker.clouddriver.cache.OnDemandAgent
import com.netflix.spinnaker.clouddriver.cache.OnDemandMetricsSupport
import com.netflix.spinnaker.clouddriver.aws.cache.Keys
import com.netflix.spinnaker.clouddriver.aws.provider.AwsInfrastructureProvider
import com.netflix.spinnaker.clouddriver.cache.OnDemandType
import groovy.util.logging.Slf4j

import static com.netflix.spinnaker.cats.agent.AgentDataType.Authority.AUTHORITATIVE
import static com.netflix.spinnaker.clouddriver.aws.cache.Keys.Namespace.ON_DEMAND
import static com.netflix.spinnaker.clouddriver.aws.cache.Keys.Namespace.SECURITY_GROUPS

@Slf4j
class AmazonSecurityGroupCachingAgent implements CachingAgent, OnDemandAgent, AccountAware {

  final AmazonClientProvider amazonClientProvider
  final NetflixAmazonCredentials account
  final String region
  final ObjectMapper objectMapper
  final Registry registry
  final EddaTimeoutConfig eddaTimeoutConfig

  final OnDemandMetricsSupport metricsSupport
  final String lastModifiedKey

  static final Set<AgentDataType> types = Collections.unmodifiableSet([
    AUTHORITATIVE.forType(SECURITY_GROUPS.ns)
  ] as Set)

  AmazonSecurityGroupCachingAgent(AmazonClientProvider amazonClientProvider,
                                  NetflixAmazonCredentials account,
                                  String region,
                                  ObjectMapper objectMapper,
                                  Registry registry,
                                  EddaTimeoutConfig eddaTimeoutConfig) {
    this.amazonClientProvider = amazonClientProvider
    this.account = account
    this.region = region
    this.objectMapper = objectMapper
    this.registry = registry
    this.eddaTimeoutConfig = eddaTimeoutConfig
    this.metricsSupport = new OnDemandMetricsSupport(registry, this, "${AmazonCloudProvider.ID}:${OnDemandType.SecurityGroup}")
    this.lastModifiedKey = Keys.getSecurityGroupKey('LAST_MODIFIED', 'LAST_MODIFIED', region, account.name, null)
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

    Long startTime = null
    def securityGroups = metricsSupport.readData {
      def ec2 = amazonClientProvider.getAmazonEC2(account, region, true)
      if (account.eddaEnabled && !eddaTimeoutConfig.disabledRegions.contains(region)) {
        startTime = System.currentTimeMillis()
      }
      return getSecurityGroups(ec2)
    }

    CacheResult result = metricsSupport.transformData { buildCacheResult(providerCache, securityGroups, [:], startTime) }

    new OnDemandAgent.OnDemandResult(sourceAgentType: getAgentType(), authoritativeTypes: [SECURITY_GROUPS.ns], cacheResult: result)
  }

  @Override
  boolean handles(OnDemandType type, String cloudProvider) {
    type == OnDemandType.SecurityGroup && cloudProvider == AmazonCloudProvider.ID
  }

  @Override
  CacheResult loadData(ProviderCache providerCache) {
    def ec2 = amazonClientProvider.getAmazonEC2(account, region)
    List<SecurityGroup> securityGroups = getSecurityGroups(ec2)
    def evictions = [:]
    if (account.eddaEnabled && !eddaTimeoutConfig.disabledRegions.contains(region)) {
      Long startTime = amazonClientProvider.lastModified
      if (startTime) {
        def lastModifiedRecord = providerCache.get(ON_DEMAND.ns, lastModifiedKey)
        if (lastModifiedRecord) {
          long lastModifiedTime = Long.parseLong(lastModifiedRecord.attributes?.lastModified?.toString() ?: '0')
          if (lastModifiedTime > startTime) {
            def sgIds = providerCache.filterIdentifiers(SECURITY_GROUPS.ns, Keys.getSecurityGroupKey('*', '*', region, account.name, '*'))
            return new DefaultCacheResult([(SECURITY_GROUPS.ns): providerCache.getAll(SECURITY_GROUPS.ns, sgIds)])
          }
        }
      } else if (securityGroups) {
        log.warn("${agentType} did not receive lastModified value in response metadata")
      }
      evictions[ON_DEMAND.ns] = [lastModifiedKey]
    }

    buildCacheResult(providerCache, securityGroups, evictions, null)
  }

  @Override
  Optional<Map<String, String>> getCacheKeyPatterns() {
    return Optional.of(
      Collections.singletonMap(
        SECURITY_GROUPS.ns, Keys.getSecurityGroupKey('*', '*', region, account.name, '*')
      )
    )
  }

  @Override
  Collection<Map<String, ?>> pendingOnDemandRequests(ProviderCache providerCache) {
    return []
  }

  private List<SecurityGroup> getSecurityGroups(AmazonEC2 amazonEC2) {
    log.info("Describing items in ${agentType}")
    amazonEC2.describeSecurityGroups().securityGroups
  }

  private CacheResult buildCacheResult(ProviderCache providerCache, List<SecurityGroup> securityGroups, Map<String, List<String>> evictions, Long lastModified) {
    List<CacheData> data = securityGroups.collect { SecurityGroup securityGroup ->
      Map<String, Object> attributes = objectMapper.convertValue(securityGroup, AwsInfrastructureProvider.ATTRIBUTES)
      new DefaultCacheData(Keys.getSecurityGroupKey(securityGroup.groupName, securityGroup.groupId, region, account.name, securityGroup.vpcId),
        attributes,
        [:])
    }
    def cacheData = [(SECURITY_GROUPS.ns): data]
    if (lastModified) {
      cacheData[ON_DEMAND.ns] = [new DefaultCacheData(lastModifiedKey, [lastModified: Long.toString(lastModified)], [:])]
    }
    log.info("Caching ${data.size()} items in ${agentType}")
    new DefaultCacheResult(cacheData, evictions)
  }
}
