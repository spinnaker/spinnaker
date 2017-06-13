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
import com.netflix.spinnaker.clouddriver.oraclebmcs.cache.Keys
import com.netflix.spinnaker.clouddriver.oraclebmcs.security.OracleBMCSNamedAccountCredentials
import com.oracle.bmc.Region
import com.oracle.bmc.core.VirtualNetworkClient
import com.oracle.bmc.core.model.Subnet
import com.oracle.bmc.core.model.Vcn
import com.oracle.bmc.core.responses.ListSubnetsResponse
import com.oracle.bmc.core.responses.ListVcnsResponse
import spock.lang.Shared
import spock.lang.Specification

import static com.netflix.spinnaker.clouddriver.oraclebmcs.cache.Keys.Namespace.SUBNETS

class OracleBMCSSubnetCachingAgentSpec extends Specification {

  @Shared
  ObjectMapper objectMapper = new ObjectMapper()

  def "agent has correct agentType"() {
    setup:
    def creds = Mock(OracleBMCSNamedAccountCredentials)
    creds.name = "foo"
    creds.compartmentId = "bar"
    creds.region = Region.US_PHOENIX_1.regionId
    def agent = new OracleBMCSSubnetCachingAgent("", creds, objectMapper)
    def expectedAgentType = "${creds.name}/${creds.region}/${OracleBMCSSubnetCachingAgent.class.simpleName}"

    when:
    def agentType = agent.getAgentType()

    then:
    agentType == expectedAgentType
  }

  def "agent handles null items in listSubnets rsp"() {
    setup:
    def creds = Mock(OracleBMCSNamedAccountCredentials)
    def networkClient = Mock(VirtualNetworkClient)
    networkClient.listVcns(_) >> ListVcnsResponse.builder().build()
    creds.networkClient >> networkClient
    def agent = new OracleBMCSSubnetCachingAgent("", creds, objectMapper)

    when:
    def cacheResult = agent.loadData(null)

    then:
    cacheResult != null
    cacheResult.cacheResults.containsKey(SUBNETS.ns)
  }

  def "agent creates correct cache result item, filtering out unavailable subnets"() {
    setup:
    def creds = Mock(OracleBMCSNamedAccountCredentials)
    creds.name = "foo"
    creds.region = Region.US_PHOENIX_1.regionId
    def networkClient = Mock(VirtualNetworkClient)
    def vcnId = "ocid.vcn.123"
    def vcn = new Vcn(null, null, null, null, null, "My Network", null, vcnId, Vcn.LifecycleState.Available, null, null)
    def subnet = new Subnet("AD1", null, null, null, "My Subnet", null, "ocid.subnet.123", Subnet.LifecycleState.Available, null, null, null, null, vcnId, null, null)
    def subnets = [
      subnet,
      new Subnet("AD1", null, null, null, "My Subnet 2", null, "ocid.subnet.234", Subnet.LifecycleState.Terminated, null, null, null, null, vcnId, null, null),
      new Subnet("AD1", null, null, null, "My Subnet 3", null, "ocid.subnet.567", Subnet.LifecycleState.Provisioning, null, null, null, null, vcnId, null, null)
    ]

    networkClient.listVcns(_) >> ListVcnsResponse.builder().items([vcn]).build()
    networkClient.listSubnets(_) >> ListSubnetsResponse.builder().items(subnets).build()
    creds.networkClient >> networkClient
    def agent = new OracleBMCSSubnetCachingAgent("", creds, objectMapper)

    when:
    def cacheResult = agent.loadData(null)

    then:
    cacheResult != null
    cacheResult.cacheResults.containsKey(SUBNETS.ns)
    cacheResult.cacheResults.get(SUBNETS.ns).size() == 1
    cacheResult.cacheResults.get(SUBNETS.ns).first().id == Keys.getSubnetKey(subnet.id, creds.region, creds.name)
    cacheResult.cacheResults.get(SUBNETS.ns).first().attributes.get("id") == subnet.id
    cacheResult.cacheResults.get(SUBNETS.ns).first().attributes.get("displayName") == subnet.displayName
  }


}
