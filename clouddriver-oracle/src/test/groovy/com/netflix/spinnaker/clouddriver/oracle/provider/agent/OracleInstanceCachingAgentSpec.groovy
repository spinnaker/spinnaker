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
import com.oracle.bmc.core.ComputeClient
import com.oracle.bmc.core.model.Instance
import com.oracle.bmc.core.responses.ListInstancesResponse
import spock.lang.Shared
import spock.lang.Specification

import static com.netflix.spinnaker.clouddriver.oracle.cache.Keys.Namespace.INSTANCES

class OracleInstanceCachingAgentSpec extends Specification {

  @Shared
  ObjectMapper objectMapper = new ObjectMapper()

  def "agent has correct agentType"() {
    setup:
    def creds = Mock(OracleNamedAccountCredentials)
    creds.name = "foo"
    creds.compartmentId = "bar"
    creds.region = Region.US_PHOENIX_1.regionId
    def agent = new OracleInstanceCachingAgent("", creds, objectMapper)
    def expectedAgentType = "${creds.name}/${creds.region}/${OracleInstanceCachingAgent.class.simpleName}"

    when:
    def agentType = agent.getAgentType()

    then:
    agentType == expectedAgentType
  }

  def "agent handles null items in listNetworks rsp"() {
    setup:
    def creds = Mock(OracleNamedAccountCredentials)
    def computeClient = Mock(ComputeClient)
    computeClient.listInstances(_) >> ListInstancesResponse.builder().build()
    creds.computeClient >> computeClient
    def agent = new OracleInstanceCachingAgent("", creds, objectMapper)

    when:
    def cacheResult = agent.loadData(null)

    then:
    cacheResult != null
    cacheResult.cacheResults.containsKey(INSTANCES.ns)
  }

  Instance newInstance(
    String availabilityDomain, String displayName, String id, String imageId, 
    Instance.LifecycleState lifecycleState, String region, String shape) {
    return Instance.builder().availabilityDomain(availabilityDomain)
      .displayName(displayName).id(id).imageId(imageId)
      .lifecycleState(lifecycleState).region(region).shape(shape).build()
  }
  
  def "agent creates correct cache result items"() {
    setup:
    def creds = Mock(OracleNamedAccountCredentials)
    creds.name = "foo"
    creds.region = Region.US_PHOENIX_1.regionId
    def computeClient = Mock(ComputeClient)

    def instances = [
      newInstance("AD1", "Instance 1", "ocid.instance.1", "ocid.image.1", Instance.LifecycleState.Running, creds.region, "small"),
      newInstance("AD1", "Instance 2", "ocid.instance.2", "ocid.image.1", Instance.LifecycleState.Starting, creds.region, "small"),
      newInstance("AD1", "Instance 3", "ocid.instance.3", "ocid.image.1", Instance.LifecycleState.Terminated, creds.region, "small"),
    ]

    computeClient.listInstances(_) >> ListInstancesResponse.builder().items(instances).build()
    creds.computeClient >> computeClient
    def agent = new OracleInstanceCachingAgent("", creds, objectMapper)

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
