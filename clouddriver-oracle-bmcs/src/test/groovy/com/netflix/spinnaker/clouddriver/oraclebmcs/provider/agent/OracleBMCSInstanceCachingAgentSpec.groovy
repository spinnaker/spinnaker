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
import com.oracle.bmc.core.ComputeClient
import com.oracle.bmc.core.model.Instance
import com.oracle.bmc.core.responses.ListInstancesResponse
import spock.lang.Shared
import spock.lang.Specification

import static com.netflix.spinnaker.clouddriver.oraclebmcs.cache.Keys.Namespace.INSTANCES

class OracleBMCSInstanceCachingAgentSpec extends Specification {

  @Shared
  ObjectMapper objectMapper = new ObjectMapper()

  def "agent has correct agentType"() {
    setup:
    def creds = Mock(OracleBMCSNamedAccountCredentials)
    creds.name = "foo"
    creds.compartmentId = "bar"
    creds.region = Region.US_PHOENIX_1.regionId
    def agent = new OracleBMCSInstanceCachingAgent("", creds, objectMapper)
    def expectedAgentType = "${creds.name}/${creds.region}/${OracleBMCSInstanceCachingAgent.class.simpleName}"

    when:
    def agentType = agent.getAgentType()

    then:
    agentType == expectedAgentType
  }

  def "agent handles null items in listNetworks rsp"() {
    setup:
    def creds = Mock(OracleBMCSNamedAccountCredentials)
    def computeClient = Mock(ComputeClient)
    computeClient.listInstances(_) >> ListInstancesResponse.builder().build()
    creds.computeClient >> computeClient
    def agent = new OracleBMCSInstanceCachingAgent("", creds, objectMapper)

    when:
    def cacheResult = agent.loadData(null)

    then:
    cacheResult != null
    cacheResult.cacheResults.containsKey(INSTANCES.ns)
  }

  def "agent creates correct cache result items"() {
    setup:
    def creds = Mock(OracleBMCSNamedAccountCredentials)
    creds.name = "foo"
    creds.region = Region.US_PHOENIX_1.regionId
    def computeClient = Mock(ComputeClient)

    def instances = [
      new Instance("AD1", null, "Instance 1", "ocid.instance.1", "ocid.image.1", null, Instance.LifecycleState.Running, null, creds.region, "small", null),
      new Instance("AD1", null, "Instance 2", "ocid.instance.2", "ocid.image.1", null, Instance.LifecycleState.Starting, null, creds.region, "small", null),
      new Instance("AD1", null, "Instance 3", "ocid.instance.3", "ocid.image.1", null, Instance.LifecycleState.Terminated, null, creds.region, "small", null),
    ]

    computeClient.listInstances(_) >> ListInstancesResponse.builder().items(instances).build()
    creds.computeClient >> computeClient
    def agent = new OracleBMCSInstanceCachingAgent("", creds, objectMapper)

    when:
    def cacheResult = agent.loadData(null)

    then:
    cacheResult != null
    cacheResult.cacheResults.containsKey(INSTANCES.ns)
    cacheResult.cacheResults.get(INSTANCES.ns).size() == 2
    cacheResult.cacheResults.get(INSTANCES.ns).first().id == Keys.getInstanceKey(creds.name, creds.region, "Instance 1", "ocid.instance.1")
    cacheResult.cacheResults.get(INSTANCES.ns).first().attributes.get("id") == "ocid.instance.1"
    cacheResult.cacheResults.get(INSTANCES.ns).first().attributes.get("displayName") == "Instance 1"
  }

}
