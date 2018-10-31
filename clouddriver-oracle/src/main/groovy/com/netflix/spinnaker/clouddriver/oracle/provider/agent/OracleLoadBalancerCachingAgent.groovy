/*
 * Copyright (c) 2017, 2018, Oracle Corporation and/or its affiliates. All rights reserved.
 *
 * The contents of this file are subject to the Apache License Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * If a copy of the Apache License Version 2.0 was not distributed with this file,
 * You can obtain one at https://www.apache.org/licenses/LICENSE-2.0.html
 */
package com.netflix.spinnaker.clouddriver.oracle.provider.agent

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.cats.agent.AgentDataType
import com.netflix.spinnaker.cats.agent.CacheResult
import com.netflix.spinnaker.cats.agent.DefaultCacheResult
import com.netflix.spinnaker.cats.cache.CacheData
import com.netflix.spinnaker.cats.cache.DefaultCacheData
import com.netflix.spinnaker.cats.provider.ProviderCache
import com.netflix.spinnaker.clouddriver.oracle.cache.Keys
import com.netflix.spinnaker.clouddriver.oracle.security.OracleNamedAccountCredentials
import com.oracle.bmc.loadbalancer.model.LoadBalancer
import com.oracle.bmc.loadbalancer.requests.ListLoadBalancersRequest
import groovy.util.logging.Slf4j

@Slf4j
class OracleLoadBalancerCachingAgent extends AbstractOracleCachingAgent {

  final Set<AgentDataType> providedDataTypes = [
    AgentDataType.Authority.AUTHORITATIVE.forType(Keys.Namespace.LOADBALANCERS.ns)
  ] as Set

  OracleLoadBalancerCachingAgent(String clouddriverUserAgentApplicationName,
                                     OracleNamedAccountCredentials credentials,
                                     ObjectMapper objectMapper) {
    super(objectMapper, credentials, clouddriverUserAgentApplicationName)
  }

  @Override
  CacheResult loadData(ProviderCache providerCache) {
    List<LoadBalancer> loadBalancers = loadLoadBalancers()
    return buildCacheResult(loadBalancers)
  }

  List<LoadBalancer> loadLoadBalancers() {
    def response = credentials.loadBalancerClient.listLoadBalancers(ListLoadBalancersRequest.builder()
      .compartmentId(credentials.compartmentId)
      .build())
    return response.items
  }

  private CacheResult buildCacheResult(List<LoadBalancer> loadBalancers) {
    log.info("Describing items in $agentType")

    List<CacheData> data = loadBalancers.collect { LoadBalancer lb ->
      if (lb.lifecycleState != LoadBalancer.LifecycleState.Active) {
        return null
      }
      Map<String, Object> attributes = objectMapper.convertValue(lb, ATTRIBUTES)
      new DefaultCacheData(
        Keys.getLoadBalancerKey(lb.displayName, lb.id, credentials.region, credentials.name),
        attributes,
        [:]
      )
    }
    data.removeAll { it == null }
    def cacheData = [(Keys.Namespace.LOADBALANCERS.ns): data]
    log.info("Caching ${data.size()} items in $agentType")
    return new DefaultCacheResult(cacheData, [:])
  }

}
