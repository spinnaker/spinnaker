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
import com.netflix.spinnaker.cats.cache.DefaultCacheData
import com.netflix.spinnaker.cats.provider.ProviderCache
import com.netflix.spinnaker.clouddriver.oraclebmcs.cache.Keys
import com.netflix.spinnaker.clouddriver.oraclebmcs.security.OracleBMCSNamedAccountCredentials
import com.oracle.bmc.core.model.Instance
import com.oracle.bmc.core.requests.ListInstancesRequest
import com.oracle.bmc.core.responses.ListInstancesResponse
import groovy.transform.InheritConstructors
import groovy.util.logging.Slf4j

import static com.netflix.spinnaker.cats.agent.AgentDataType.Authority.*
import static com.netflix.spinnaker.clouddriver.oraclebmcs.cache.Keys.Namespace.INSTANCES

@Slf4j
@InheritConstructors
class OracleBMCSInstanceCachingAgent extends AbstractOracleBMCSCachingAgent {

  final Set<AgentDataType> providedDataTypes = [
    AUTHORITATIVE.forType(INSTANCES.ns)
  ]

  OracleBMCSInstanceCachingAgent(String clouddriverUserAgentApplicationName, OracleBMCSNamedAccountCredentials credentials, ObjectMapper objectMapper) {
    super(objectMapper, credentials, clouddriverUserAgentApplicationName)
  }

  @Override
  CacheResult loadData(ProviderCache providerCache) {
    List<Instance> instances = getInstances()
    return buildCacheResults(instances)
  }

  List<Instance> getInstances() {
    def instances = []
    def nextPage = null

    while (true) {
      ListInstancesResponse response = credentials.computeClient.listInstances(ListInstancesRequest.builder()
        .compartmentId(credentials.compartmentId)
        .page(nextPage)
        .build())

      instances += response.items.findAll { it.lifecycleState != Instance.LifecycleState.Terminated }
      nextPage = response.opcNextPage

      if (!response.opcNextPage) {
        break
      }
    }

    return instances
  }

  CacheResult buildCacheResults(List<Instance> instances) {
    log.info("Describing items in $agentType")
    def data = instances.collect { Instance instance ->
      def cacheKey = Keys.getInstanceKey(credentials.name, credentials.region, instance.displayName, instance.id)
      Map<String, Object> attributes = objectMapper.convertValue(instance, ATTRIBUTES)
      new DefaultCacheData(
        cacheKey,
        attributes,
        [:]
      )
    }

    def cacheData = [(INSTANCES.ns): data]
    log.info("Caching ${data.size()} items in ${agentType}")
    return new DefaultCacheResult(cacheData, [:])
  }
}
