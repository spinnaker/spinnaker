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
import com.oracle.bmc.core.model.Image
import com.oracle.bmc.core.model.Shape
import com.oracle.bmc.core.responses.ListImagesResponse
import com.oracle.bmc.core.responses.ListShapesResponse
import spock.lang.Shared
import spock.lang.Specification

import static com.netflix.spinnaker.clouddriver.oraclebmcs.cache.Keys.Namespace.IMAGES

class OracleBMCSImageCachingAgentSpec extends Specification {

  @Shared
  ObjectMapper objectMapper = new ObjectMapper()

  def "agent has correct agentType"() {
    setup:
    def creds = Mock(OracleBMCSNamedAccountCredentials)
    creds.name = "foo"
    creds.compartmentId = "bar"
    creds.region = Region.US_PHOENIX_1.regionId
    def agent = new OracleBMCSImageCachingAgent("", creds, objectMapper)
    def expectedAgentType = "${creds.name}/${creds.region}/${OracleBMCSImageCachingAgent.class.simpleName}"

    when:
    def agentType = agent.getAgentType()

    then:
    agentType == expectedAgentType
  }

  def "agent handles null items in listImages rsp"() {
    setup:
    def creds = Mock(OracleBMCSNamedAccountCredentials)
    def computeClient = Mock(ComputeClient)
    computeClient.listImages(_) >> ListImagesResponse.builder().build()
    creds.computeClient >> computeClient
    def agent = new OracleBMCSImageCachingAgent("", creds, objectMapper)

    when:
    def cacheResult = agent.loadData(null)

    then:
    cacheResult != null
    cacheResult.cacheResults.containsKey(IMAGES.ns)
  }

  def "agent creates correct cache result item, filtering out unavailable images and adding shapes"() {
    setup:
    def creds = Mock(OracleBMCSNamedAccountCredentials)
    creds.name = "foo"
    creds.region = Region.US_PHOENIX_1.regionId
    def computeClient = Mock(ComputeClient)
    def image = new Image(null, null, null, "My Image", "ocid.image.123", Image.LifecycleState.Available, null, null, null)
    def images = [
      image,
      new Image(null, null, null, "My New Image", "ocid.image.234", Image.LifecycleState.Provisioning, null, null, null),
      new Image(null, null, null, "My Disabled Image", "ocid.image.345", Image.LifecycleState.Disabled, null, null, null)
    ]
    def shapes = [Shape.builder().shape("small").build()]

    computeClient.listImages(_) >> ListImagesResponse.builder().items(images).build()
    computeClient.listShapes(_) >> ListShapesResponse.builder().items(shapes).build()
    creds.computeClient >> computeClient
    def agent = new OracleBMCSImageCachingAgent("", creds, objectMapper)

    when:
    def cacheResult = agent.loadData(null)

    then:
    cacheResult != null
    cacheResult.cacheResults.containsKey(IMAGES.ns)
    cacheResult.cacheResults.get(IMAGES.ns).size() == 1
    cacheResult.cacheResults.get(IMAGES.ns).first().id == Keys.getImageKey(creds.name, creds.region, image.id)
    cacheResult.cacheResults.get(IMAGES.ns).first().attributes.get("id") == image.id
    cacheResult.cacheResults.get(IMAGES.ns).first().attributes.get("displayName") == image.displayName
    cacheResult.cacheResults.get(IMAGES.ns).first().attributes.get("compatibleShapes") == ["small"]

  }


}
