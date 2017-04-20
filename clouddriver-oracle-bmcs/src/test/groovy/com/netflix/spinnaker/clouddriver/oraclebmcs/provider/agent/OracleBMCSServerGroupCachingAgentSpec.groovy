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
import com.netflix.spinnaker.clouddriver.oraclebmcs.model.OracleBMCSServerGroup
import com.netflix.spinnaker.clouddriver.oraclebmcs.security.OracleBMCSNamedAccountCredentials
import com.netflix.spinnaker.clouddriver.oraclebmcs.service.servergroup.OracleBMCSServerGroupService
import com.oracle.bmc.Region
import spock.lang.Shared
import spock.lang.Specification

class OracleBMCSServerGroupCachingAgentSpec extends Specification {

  @Shared
  ObjectMapper objectMapper = new ObjectMapper()

  def "agent has correct agentType"() {
    setup:
    def creds = Mock(OracleBMCSNamedAccountCredentials)
    creds.name >> "foo"
    creds.compartmentId >> "bar"
    creds.region >> Region.US_PHOENIX_1.regionId
    def agent = new OracleBMCSServerGroupCachingAgent("", creds, objectMapper, null)
    def expectedAgentType = "${creds.name}/${creds.region}/${OracleBMCSServerGroupCachingAgent.class.simpleName}"

    when:
    def agentType = agent.getAgentType()

    then:
    agentType == expectedAgentType
  }

  def "agent handles null items in list server group result"() {
    setup:
    def creds = Mock(OracleBMCSNamedAccountCredentials)
    creds.name >> "foo"
    creds.region >> Region.US_PHOENIX_1.regionId
    def sgService = Mock(OracleBMCSServerGroupService)
    sgService.listAllServerGroups(_) >> []
    def agent = new OracleBMCSServerGroupCachingAgent("", creds, objectMapper, sgService)

    when:
    def cacheResult = agent.loadData(null)

    then:
    cacheResult != null
    cacheResult.cacheResults.containsKey(Keys.Namespace.SERVER_GROUPS.ns)
  }

  def "agent creates correct cache result items"() {
    setup:
    def creds = Mock(OracleBMCSNamedAccountCredentials)
    creds.name >> "foo"
    creds.region >> Region.US_PHOENIX_1.regionId
    def sgService = Mock(OracleBMCSServerGroupService)
    sgService.listAllServerGroups(_) >> [new OracleBMCSServerGroup(name: "foo-v001", targetSize: 5)]
    def agent = new OracleBMCSServerGroupCachingAgent("", creds, objectMapper, sgService)

    when:
    def cacheResult = agent.loadData(null)

    then:
    cacheResult != null
    cacheResult.cacheResults.containsKey(Keys.Namespace.SERVER_GROUPS.ns)
    cacheResult.cacheResults.get(Keys.Namespace.SERVER_GROUPS.ns).size() == 1
    cacheResult.cacheResults.get(Keys.Namespace.SERVER_GROUPS.ns).first().id == Keys.getServerGroupKey(creds.name, creds.region, "foo-v001")
    cacheResult.cacheResults.get(Keys.Namespace.SERVER_GROUPS.ns).first().attributes.get("name") == "foo-v001"
    cacheResult.cacheResults.get(Keys.Namespace.SERVER_GROUPS.ns).first().attributes.get("targetSize") == 5
  }

}
