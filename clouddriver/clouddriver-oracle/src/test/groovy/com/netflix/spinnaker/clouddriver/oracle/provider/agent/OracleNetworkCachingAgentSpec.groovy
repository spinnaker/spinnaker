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
import com.netflix.spinnaker.clouddriver.oracle.cache.Keys
import com.netflix.spinnaker.clouddriver.oracle.security.OracleNamedAccountCredentials
import com.oracle.bmc.Region
import com.oracle.bmc.core.VirtualNetworkClient
import com.oracle.bmc.core.model.Vcn
import com.oracle.bmc.core.responses.ListVcnsResponse
import spock.lang.Shared
import spock.lang.Specification

class OracleNetworkCachingAgentSpec extends Specification {

  @Shared
  ObjectMapper objectMapper = new ObjectMapper()

  def "agent has correct agentType"() {
    setup:
    def creds = Mock(OracleNamedAccountCredentials)
    creds.name = "foo"
    creds.compartmentId = "bar"
    creds.region = Region.US_PHOENIX_1.regionId
    def agent = new OracleNetworkCachingAgent("", creds, objectMapper)
    def expectedAgentType = "${creds.name}/${creds.region}/${OracleNetworkCachingAgent.class.simpleName}"

    when:
    def agentType = agent.getAgentType()

    then:
    agentType == expectedAgentType
  }

  def "agent handles null items in listNetworks rsp"() {
    setup:
    def creds = Mock(OracleNamedAccountCredentials)
    def networkClient = Mock(VirtualNetworkClient)
    networkClient.listVcns(_) >> ListVcnsResponse.builder().build()
    creds.networkClient >> networkClient
    def agent = new OracleNetworkCachingAgent("", creds, objectMapper)

    when:
    def cacheResult = agent.loadData(null)

    then:
    cacheResult != null
    cacheResult.cacheResults.containsKey(Keys.Namespace.NETWORKS.ns)
  }

  Vcn newVcn(String displayName, String id, Vcn.LifecycleState lifecycleState) {
    return Vcn.builder().displayName(displayName).id(id).lifecycleState(lifecycleState).build()
  }
  
  def "agent creates correct cache result item, filtering out unavailable vcns"() {
    setup:
    def creds = Mock(OracleNamedAccountCredentials)
    creds.name = "foo"
    creds.region = Region.US_PHOENIX_1.regionId
    def networkClient = Mock(VirtualNetworkClient)
    def vcnId = "ocid.vcn.123"
    def vcnDisplayName = "My Network"
    def vcn = newVcn(vcnDisplayName, vcnId, Vcn.LifecycleState.Available)
    def vcns = [
      vcn,
      newVcn("Another network", "ocid.vcn.321", Vcn.LifecycleState.Terminated),
      newVcn("Yet Another network", "ocid.vcn.531", Vcn.LifecycleState.Terminating),
      newVcn("Coming soon network", "ocid.vcn.878", Vcn.LifecycleState.Provisioning)
    ]

    networkClient.listVcns(_) >> ListVcnsResponse.builder().items(vcns).build()
    creds.networkClient >> networkClient
    def agent = new OracleNetworkCachingAgent("", creds, objectMapper)

    when:
    def cacheResult = agent.loadData(null)

    then:
    cacheResult != null
    cacheResult.cacheResults.containsKey(Keys.Namespace.NETWORKS.ns)
    cacheResult.cacheResults.get(Keys.Namespace.NETWORKS.ns).size() == 1
    cacheResult.cacheResults.get(Keys.Namespace.NETWORKS.ns).first().id == Keys.getNetworkKey(vcn.displayName, vcn.id, creds.region, creds.name)
    cacheResult.cacheResults.get(Keys.Namespace.NETWORKS.ns).first().attributes.get("id") == vcnId
    cacheResult.cacheResults.get(Keys.Namespace.NETWORKS.ns).first().attributes.get("displayName") == vcnDisplayName
  }


}
