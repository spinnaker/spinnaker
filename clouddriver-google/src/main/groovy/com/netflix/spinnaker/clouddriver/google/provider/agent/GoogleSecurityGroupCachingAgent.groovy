/*
 * Copyright 2015 Google, Inc.
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

package com.netflix.spinnaker.clouddriver.google.provider.agent

import com.fasterxml.jackson.databind.ObjectMapper
import com.google.api.services.compute.model.Firewall
import com.netflix.spectator.api.Registry
import com.netflix.spinnaker.cats.agent.AccountAware
import com.netflix.spinnaker.cats.agent.AgentDataType
import com.netflix.spinnaker.cats.agent.CacheResult
import com.netflix.spinnaker.cats.agent.CachingAgent
import com.netflix.spinnaker.cats.agent.DefaultCacheResult
import com.netflix.spinnaker.cats.cache.CacheData
import com.netflix.spinnaker.cats.cache.DefaultCacheData
import com.netflix.spinnaker.cats.provider.ProviderCache
import com.netflix.spinnaker.clouddriver.cache.OnDemandAgent
import com.netflix.spinnaker.clouddriver.cache.OnDemandMetricsSupport
import com.netflix.spinnaker.clouddriver.google.GoogleCloudProvider
import com.netflix.spinnaker.clouddriver.google.cache.Keys
import com.netflix.spinnaker.clouddriver.google.provider.GoogleInfrastructureProvider
import com.netflix.spinnaker.clouddriver.google.security.GoogleCredentials
import groovy.util.logging.Slf4j

import static com.netflix.spinnaker.cats.agent.AgentDataType.Authority.AUTHORITATIVE

@Slf4j
class GoogleSecurityGroupCachingAgent implements CachingAgent, OnDemandAgent, AccountAware {

  @Deprecated
  private static final String LEGACY_ON_DEMAND_TYPE = 'GoogleSecurityGroup'

  private static final String ON_DEMAND_TYPE = 'SecurityGroup'

  final GoogleCloudProvider googleCloudProvider
  final String accountName
  final GoogleCredentials credentials
  final ObjectMapper objectMapper

  final OnDemandMetricsSupport metricsSupport

  static final Set<AgentDataType> types = Collections.unmodifiableSet([
    AUTHORITATIVE.forType(Keys.Namespace.SECURITY_GROUPS.ns)
  ] as Set)

  GoogleSecurityGroupCachingAgent(GoogleCloudProvider googleCloudProvider,
                                  String accountName,
                                  GoogleCredentials credentials,
                                  ObjectMapper objectMapper,
                                  Registry registry) {
    this.googleCloudProvider = googleCloudProvider
    this.accountName = accountName
    this.credentials = credentials
    this.objectMapper = objectMapper
    this.metricsSupport = new OnDemandMetricsSupport(registry, this, googleCloudProvider.id + ":" + ON_DEMAND_TYPE)
  }

  @Override
  String getProviderName() {
    GoogleInfrastructureProvider.PROVIDER_NAME
  }

  @Override
  String getAgentType() {
    "${accountName}/global/${GoogleSecurityGroupCachingAgent.simpleName}"
  }

  @Override
  String getAccountName() {
    accountName
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
    if (data.account != accountName) {
      return null
    }

    if (data.region != "global") {
      return null
    }

    List<Firewall> firewallList = metricsSupport.readData {
      loadFirewalls()
    }

    CacheResult result = metricsSupport.transformData {
      buildCacheResult(providerCache, firewallList)
    }

    new OnDemandAgent.OnDemandResult(
      sourceAgentType: getAgentType(),
      authoritativeTypes: [Keys.Namespace.SECURITY_GROUPS.ns],
      cacheResult: result
    )
  }

  @Override
  boolean handles(String type) {
    type == LEGACY_ON_DEMAND_TYPE
  }

  @Override
  boolean handles(String type, String cloudProvider) {
    type == ON_DEMAND_TYPE && cloudProvider == googleCloudProvider.id
  }

  @Override
  CacheResult loadData(ProviderCache providerCache) {
    List<Firewall> firewallList = loadFirewalls()

    buildCacheResult(providerCache, firewallList)
  }

  List<Firewall> loadFirewalls() {
    def compute = credentials.compute
    def project = credentials.project

    compute.firewalls().list(project).execute().items
  }

  private CacheResult buildCacheResult(ProviderCache providerCache, List<Firewall> firewallList) {
    log.info("Describing items in ${agentType}")

    List<CacheData> data = firewallList.collect { Firewall firewall ->
      Map<String, Object> attributes = [firewall: firewall]

      new DefaultCacheData(Keys.getSecurityGroupKey(googleCloudProvider, firewall.getName(), firewall.getName(), "global", accountName),
                           attributes,
                           [:])
    }

    log.info("Caching ${data.size()} items in ${agentType}")

    new DefaultCacheResult([(Keys.Namespace.SECURITY_GROUPS.ns): data])
  }
}
