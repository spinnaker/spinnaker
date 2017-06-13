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
import com.netflix.spectator.api.Registry
import com.netflix.spinnaker.clouddriver.oraclebmcs.cache.Keys
import com.netflix.spinnaker.clouddriver.oraclebmcs.security.OracleBMCSNamedAccountCredentials
import com.oracle.bmc.Region
import com.oracle.bmc.core.VirtualNetworkClient
import com.oracle.bmc.core.model.SecurityList
import com.oracle.bmc.core.model.Vcn
import com.oracle.bmc.core.responses.ListSecurityListsResponse
import com.oracle.bmc.core.responses.ListVcnsResponse
import spock.lang.Shared
import spock.lang.Specification

class OracleBMCSSecurityGroupCachingAgentSpec extends Specification {

  @Shared
  ObjectMapper objectMapper = new ObjectMapper()

  def "agent has correct agentType"() {
    setup:
    def creds = Mock(OracleBMCSNamedAccountCredentials)
    creds.name = "foo"
    creds.compartmentId = "bar"
    creds.region = Region.US_PHOENIX_1.regionId
    def registry = Mock(Registry)
    def agent = new OracleBMCSSecurityGroupCachingAgent("", creds, objectMapper, registry)
    def expectedAgentType = "${creds.name}/${creds.region}/${OracleBMCSSecurityGroupCachingAgent.class.simpleName}"

    when:
    def agentType = agent.getAgentType()

    then:
    agentType == expectedAgentType
  }

  def "agent handles null items in list rsp"() {
    setup:
    def creds = Mock(OracleBMCSNamedAccountCredentials)
    def registry = Mock(Registry)
    def networkClient = Mock(VirtualNetworkClient)
    networkClient.listVcns(_) >> ListVcnsResponse.builder().build()
    creds.networkClient >> networkClient
    def agent = new OracleBMCSSecurityGroupCachingAgent("", creds, objectMapper, registry)

    when:
    def cacheResult = agent.loadData(null)

    then:
    cacheResult != null
    cacheResult.cacheResults.containsKey(Keys.Namespace.SECURITY_GROUPS.ns)
  }

  def "agent creates correct cache result items"() {
    setup:
    def creds = Mock(OracleBMCSNamedAccountCredentials)
    creds.name = "foo"
    creds.region = Region.US_PHOENIX_1.regionId
    def registry = Mock(Registry)
    def networkClient = Mock(VirtualNetworkClient)
    def vcnId = "ocid.vcn.123"
    def vcns = [
      new Vcn(null, null, null, null, null, "My Network", null, vcnId, Vcn.LifecycleState.Available, null, null)
    ]
    def secLists = [
      new SecurityList(null, "My Frontend SecList", null, "ocid.seclist.123", null, SecurityList.LifecycleState.Available, null, vcnId),
      new SecurityList(null, "My Backend SecList", null, "ocid.seclist.234", null, SecurityList.LifecycleState.Available, null, vcnId)
    ]
    networkClient.listVcns(_) >> ListVcnsResponse.builder().items(vcns).build()
    networkClient.listSecurityLists(_) >> ListSecurityListsResponse.builder().items(secLists).build()
    creds.networkClient >> networkClient
    def agent = new OracleBMCSSecurityGroupCachingAgent("", creds, objectMapper, registry)

    when:
    def cacheResult = agent.loadData(null)

    then:
    cacheResult != null
    cacheResult.cacheResults.containsKey(Keys.Namespace.SECURITY_GROUPS.ns)
    cacheResult.cacheResults.get(Keys.Namespace.SECURITY_GROUPS.ns).size() == 2
    cacheResult.cacheResults.get(Keys.Namespace.SECURITY_GROUPS.ns).first().id == Keys.getSecurityGroupKey("My Frontend SecList", "ocid.seclist.123", creds.region, creds.accountId)
    cacheResult.cacheResults.get(Keys.Namespace.SECURITY_GROUPS.ns).first().attributes.get("id") == "ocid.seclist.123"
    cacheResult.cacheResults.get(Keys.Namespace.SECURITY_GROUPS.ns).first().attributes.get("displayName") == "My Frontend SecList"
  }


}
