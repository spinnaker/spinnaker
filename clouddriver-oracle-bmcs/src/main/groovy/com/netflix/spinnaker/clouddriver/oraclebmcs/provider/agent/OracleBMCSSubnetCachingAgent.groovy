/*
 * Copyright (c) 2017 Oracle America, Inc.
 *
 * The contents of this file are subject to the Apache License Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * If a copy of the Apache License Version 2.0 was not distributed with this file,
 * You can obtain one at https://www.apache.org/licenses/LICENSE-2.0.html
 */
package com.netflix.spinnaker.clouddriver.oraclebmcs.provider.agent

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.cats.agent.AgentDataType
import com.netflix.spinnaker.cats.agent.CacheResult
import com.netflix.spinnaker.cats.agent.DefaultCacheResult
import com.netflix.spinnaker.cats.cache.CacheData
import com.netflix.spinnaker.cats.cache.DefaultCacheData
import com.netflix.spinnaker.cats.provider.ProviderCache
import com.netflix.spinnaker.clouddriver.oraclebmcs.cache.Keys
import com.netflix.spinnaker.clouddriver.oraclebmcs.security.OracleBMCSNamedAccountCredentials
import com.oracle.bmc.core.model.Subnet
import com.oracle.bmc.core.model.Vcn
import com.oracle.bmc.core.requests.ListSubnetsRequest
import com.oracle.bmc.core.requests.ListVcnsRequest
import groovy.util.logging.Slf4j

import static com.netflix.spinnaker.clouddriver.oraclebmcs.cache.Keys.Namespace.SUBNETS

@Slf4j
class OracleBMCSSubnetCachingAgent extends AbstractOracleBMCSCachingAgent {

  final Set<AgentDataType> providedDataTypes = [
    AgentDataType.Authority.AUTHORITATIVE.forType(SUBNETS.ns)
  ] as Set

  OracleBMCSSubnetCachingAgent(String clouddriverUserAgentApplicationName,
                               OracleBMCSNamedAccountCredentials credentials,
                               ObjectMapper objectMapper) {
    super(objectMapper, credentials, clouddriverUserAgentApplicationName)
  }

  @Override
  CacheResult loadData(ProviderCache providerCache) {
    List<Subnet> subnets = loadSubnets()
    return buildCacheResult(subnets)
  }

  List<Subnet> loadSubnets() {
    def listVcnsRequest = ListVcnsRequest.builder().compartmentId(credentials.compartmentId).build()
    return credentials.networkClient.listVcns(listVcnsRequest).items.collect { Vcn vcn ->
      def listSubnetsRequest = ListSubnetsRequest.builder().compartmentId(credentials.compartmentId).vcnId(vcn.id).build()
      credentials.networkClient.listSubnets(listSubnetsRequest).items
    }.flatten() as List<Subnet>
  }

  private CacheResult buildCacheResult(List<Subnet> subnets) {
    log.info("Describing items in ${agentType}")

    List<CacheData> data = subnets.collect { Subnet subnet ->
      if (subnet.lifecycleState != Subnet.LifecycleState.Available) {
        return null
      }
      Map<String, Object> attributes = objectMapper.convertValue(subnet, ATTRIBUTES)
      def key = Keys.getSubnetKey(subnet.id, credentials.region, credentials.name)
      new DefaultCacheData(
        key,
        attributes,
        [:]
      )
    }
    data.removeAll { it == null }
    def cacheData = [(SUBNETS.ns): data]
    log.info("Caching ${data.size()} items in ${agentType}")
    return new DefaultCacheResult(cacheData, [:])
  }
}
