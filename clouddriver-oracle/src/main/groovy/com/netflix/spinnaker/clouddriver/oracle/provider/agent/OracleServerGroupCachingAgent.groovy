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
import com.netflix.spinnaker.cats.agent.AgentDataType
import com.netflix.spinnaker.cats.agent.CacheResult
import com.netflix.spinnaker.cats.agent.DefaultCacheResult
import com.netflix.spinnaker.cats.cache.DefaultCacheData
import com.netflix.spinnaker.cats.provider.ProviderCache
import com.netflix.spinnaker.clouddriver.oracle.cache.Keys
import com.netflix.spinnaker.clouddriver.oracle.model.OracleServerGroup
import com.netflix.spinnaker.clouddriver.oracle.security.OracleNamedAccountCredentials
import com.netflix.spinnaker.clouddriver.oracle.service.servergroup.OracleServerGroupService
import groovy.transform.InheritConstructors
import groovy.util.logging.Slf4j

/**
 * A caching agent for Oracle server groups.
 *
 * The groups are persisted cloud-side by the OracleServerGroupService implementation. In this agent we just read
 * all server groups that we can see given our credentials.
 *
 * This may be a slow operation due to the large number of API calls that the service makes.
 *
 * Created by hhexo on 27/01/2017.
 */
@Slf4j
@InheritConstructors
class OracleServerGroupCachingAgent extends AbstractOracleCachingAgent {

  final Set<AgentDataType> providedDataTypes = [
    AgentDataType.Authority.AUTHORITATIVE.forType(Keys.Namespace.SERVER_GROUPS.ns)
  ]

  private OracleServerGroupService oracleServerGroupService

  OracleServerGroupCachingAgent(String clouddriverUserAgentApplicationName, OracleNamedAccountCredentials credentials, ObjectMapper objectMapper, OracleServerGroupService oracleServerGroupService) {
    super(objectMapper, credentials, clouddriverUserAgentApplicationName)
    this.oracleServerGroupService = oracleServerGroupService
  }

  @Override
  CacheResult loadData(ProviderCache providerCache) {
    List<OracleServerGroup> serverGroups = this.oracleServerGroupService.listAllServerGroups(this.credentials)
    return buildCacheResults(serverGroups)
  }

  CacheResult buildCacheResults(List<OracleServerGroup> serverGroups) {
    log.info("Describing items in $agentType")
    def data = serverGroups.collect { OracleServerGroup sg ->
      // Don't cache credentials, so save them and restore them later
      def creds = sg.credentials
      sg.credentials = null
      Map<String, Object> attributes = objectMapper.convertValue(sg, ATTRIBUTES)
      sg.credentials = creds
      new DefaultCacheData(
        Keys.getServerGroupKey(this.credentials.name, credentials.region, sg.name),
        attributes,
        [:]
      )
    }

    def cacheData = [(Keys.Namespace.SERVER_GROUPS.ns): data]
    log.info("Caching ${data.size()} items in ${agentType}")
    return new DefaultCacheResult(cacheData, [:])
  }
}
