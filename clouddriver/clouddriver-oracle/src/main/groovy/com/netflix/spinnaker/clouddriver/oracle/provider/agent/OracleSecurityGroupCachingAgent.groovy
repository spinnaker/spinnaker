/*
 * Copyright (c) 2017 Oracle America, Inc.
 *
 * The contents of this file are subject to the Apache License Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * If a copy of the Apache License Version 2.0 was not distributed with this file,
 * You can obtain one at https://www.apache.org/licenses/LICENSE-2.0.html
 */
package com.netflix.spinnaker.clouddriver.oracle.provider.agent

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spectator.api.Registry
import com.netflix.spinnaker.cats.agent.AgentDataType
import com.netflix.spinnaker.cats.agent.CacheResult
import com.netflix.spinnaker.cats.agent.DefaultCacheResult
import com.netflix.spinnaker.cats.cache.CacheData
import com.netflix.spinnaker.cats.cache.DefaultCacheData
import com.netflix.spinnaker.cats.provider.ProviderCache
import com.netflix.spinnaker.clouddriver.cache.OnDemandAgent
import com.netflix.spinnaker.clouddriver.cache.OnDemandType
import com.netflix.spinnaker.clouddriver.cache.OnDemandMetricsSupport
import com.netflix.spinnaker.clouddriver.oracle.OracleCloudProvider
import com.netflix.spinnaker.clouddriver.oracle.cache.Keys
import com.netflix.spinnaker.clouddriver.oracle.security.OracleNamedAccountCredentials
import com.oracle.bmc.core.model.SecurityList
import com.oracle.bmc.core.model.Vcn
import com.oracle.bmc.core.requests.ListSecurityListsRequest
import com.oracle.bmc.core.requests.ListVcnsRequest
import groovy.util.logging.Slf4j

import static com.netflix.spinnaker.cats.agent.AgentDataType.Authority.AUTHORITATIVE
import static com.netflix.spinnaker.clouddriver.oracle.cache.Keys.Namespace.SECURITY_GROUPS

@Slf4j
class OracleSecurityGroupCachingAgent extends AbstractOracleCachingAgent implements OnDemandAgent {

  final Set<AgentDataType> providedDataTypes = [
    AUTHORITATIVE.forType(SECURITY_GROUPS.ns)
  ] as Set

  final String onDemandAgentType = "${agentType}-OnDemand"
  final OnDemandMetricsSupport metricsSupport

  OracleSecurityGroupCachingAgent(String clouddriverUserAgentApplicationName, OracleNamedAccountCredentials credentials, ObjectMapper objectMapper, Registry registry) {
    super(objectMapper, credentials, clouddriverUserAgentApplicationName)
    this.metricsSupport = new OnDemandMetricsSupport(registry, this, "${OracleCloudProvider.ID}:${OnDemandType.SecurityGroup}")
  }

  @Override
  OnDemandAgent.OnDemandResult handle(ProviderCache providerCache, Map<String, ? extends Object> data) {
    if (data.account != credentials.name || data.region != credentials.region) {
      return null
    }

    List<SecurityList> securityLists = metricsSupport.readData {
      loadSecurityLists()
    }

    CacheResult result = metricsSupport.transformData {
      buildCacheResult(securityLists)
    }

    return new OnDemandAgent.OnDemandResult(
      sourceAgentType: getAgentType(),
      authoritativeTypes: [SECURITY_GROUPS.ns],
      cacheResult: result
    )
  }

  @Override
  Collection<Map<String, ?>> pendingOnDemandRequests(ProviderCache providerCache) {
    return []
  }

  @Override
  boolean handles(OnDemandType type, String cloudProvider) {
    type == OnDemandType.SecurityGroup && cloudProvider == OracleCloudProvider.ID
  }

  @Override
  CacheResult loadData(ProviderCache providerCache) {
    List<SecurityList> securityLists = loadSecurityLists()
    return buildCacheResult(securityLists)
  }

  List<SecurityList> loadSecurityLists() {
    def listVcnsRequest = ListVcnsRequest.builder().compartmentId(credentials.compartmentId).build()
    def securityLists = credentials.networkClient.listVcns(listVcnsRequest).items.collect { Vcn vcn ->
      def listSecListsRequest = ListSecurityListsRequest.builder()
        .compartmentId(credentials.compartmentId)
        .vcnId(vcn.id)
        .build()
      credentials.networkClient.listSecurityLists(listSecListsRequest).items
    }.flatten()
    return securityLists as List<SecurityList>
  }

  private CacheResult buildCacheResult(List<SecurityList> securityLists) {
    List<CacheData> data = securityLists.collect { SecurityList sl ->
      if (sl.lifecycleState != SecurityList.LifecycleState.Available) {
        return null
      }
      Map<String, Object> attributes = objectMapper.convertValue(sl, ATTRIBUTES)
      new DefaultCacheData(
        Keys.getSecurityGroupKey(sl.displayName, sl.id, credentials.region, credentials.name),
        attributes,
        [:]
      )
    }
    data.removeAll { it == null }
    def cacheData = [(SECURITY_GROUPS.ns): data]
    log.info("Caching ${data.size()} items in ${agentType}")
    return new DefaultCacheResult(cacheData, [:])
  }
}
