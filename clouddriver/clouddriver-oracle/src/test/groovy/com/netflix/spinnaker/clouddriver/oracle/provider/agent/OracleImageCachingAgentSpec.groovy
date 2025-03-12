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
import com.oracle.bmc.core.model.Image
import com.oracle.bmc.core.model.Shape
import com.oracle.bmc.core.responses.ListImagesResponse
import com.oracle.bmc.core.responses.ListShapesResponse
import spock.lang.Shared
import spock.lang.Specification

import static com.netflix.spinnaker.clouddriver.oracle.cache.Keys.Namespace.IMAGES

class OracleImageCachingAgentSpec extends Specification {

  @Shared
  ObjectMapper objectMapper = new ObjectMapper()

  def "agent has correct agentType"() {
    setup:
    def creds = Mock(OracleNamedAccountCredentials)
    creds.name = "foo"
    creds.compartmentId = "bar"
    creds.region = Region.US_PHOENIX_1.regionId
    def agent = new OracleImageCachingAgent("", creds, objectMapper)
    def expectedAgentType = "${creds.name}/${creds.region}/${OracleImageCachingAgent.class.simpleName}"

    when:
    def agentType = agent.getAgentType()

    then:
    agentType == expectedAgentType
  }

  def "agent handles null items in listImages rsp"() {
    setup:
    def creds = Mock(OracleNamedAccountCredentials)
    def computeClient = Mock(ComputeClient)
    computeClient.listImages(_) >> ListImagesResponse.builder().build()
    creds.computeClient >> computeClient
    def agent = new OracleImageCachingAgent("", creds, objectMapper)

    when:
    def cacheResult = agent.loadData(null)

    then:
    cacheResult != null
    cacheResult.cacheResults.containsKey(IMAGES.ns)
  }
  
  Image newImage(String name, String id, Image.LifecycleState lifecycleState) {
    return Image.builder().displayName(name).id(id).lifecycleState(lifecycleState).build()
  }

  def "agent creates correct cache result item, filtering out unavailable images and adding shapes"() {
    setup:
    def creds = Mock(OracleNamedAccountCredentials)
    creds.name = "foo"
    creds.region = Region.US_PHOENIX_1.regionId
    def computeClient = Mock(ComputeClient)
    def image = newImage("My Image", "ocid.image.123", Image.LifecycleState.Available)
    def images = [
      image,
      newImage("My New Image", "ocid.image.234", Image.LifecycleState.Provisioning),
      newImage("My Disabled Image", "ocid.image.345", Image.LifecycleState.Disabled)
    ]
    def shapes = [Shape.builder().shape("small").build()]

    computeClient.listImages(_) >> ListImagesResponse.builder().items(images).build()
    computeClient.listShapes(_) >> ListShapesResponse.builder().items(shapes).build()
    creds.computeClient >> computeClient
    def agent = new OracleImageCachingAgent("", creds, objectMapper)

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
