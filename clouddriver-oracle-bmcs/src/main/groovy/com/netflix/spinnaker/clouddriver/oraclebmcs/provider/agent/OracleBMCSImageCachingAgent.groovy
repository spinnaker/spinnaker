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
import com.netflix.spinnaker.cats.cache.CacheData
import com.netflix.spinnaker.cats.cache.DefaultCacheData
import com.netflix.spinnaker.cats.provider.ProviderCache
import com.netflix.spinnaker.clouddriver.oraclebmcs.cache.Keys
import com.netflix.spinnaker.clouddriver.oraclebmcs.security.OracleBMCSNamedAccountCredentials
import com.oracle.bmc.core.model.Image
import com.oracle.bmc.core.model.Shape
import com.oracle.bmc.core.requests.ListImagesRequest
import com.oracle.bmc.core.requests.ListShapesRequest
import groovy.util.logging.Slf4j

import static com.netflix.spinnaker.clouddriver.oraclebmcs.cache.Keys.Namespace.IMAGES
import static com.netflix.spinnaker.clouddriver.oraclebmcs.cache.Keys.getImageKey

@Slf4j
class OracleBMCSImageCachingAgent extends AbstractOracleBMCSCachingAgent {

  final Set<AgentDataType> providedDataTypes = [
    AgentDataType.Authority.AUTHORITATIVE.forType(Keys.Namespace.IMAGES.ns)
  ] as Set

  OracleBMCSImageCachingAgent(String clouddriverUserAgentApplicationName,
                              OracleBMCSNamedAccountCredentials credentials,
                              ObjectMapper objectMapper) {
    super(objectMapper, credentials, clouddriverUserAgentApplicationName)
  }

  @Override
  CacheResult loadData(ProviderCache providerCache) {
    List<Image> imageList = loadImages()
    return buildCacheResult(imageList)
  }

  List<Image> loadImages() {
    def response = credentials.computeClient.listImages(ListImagesRequest.builder()
      .compartmentId(credentials.compartmentId)
      .build())
    return response.items
  }

  private CacheResult buildCacheResult(List<Image> imageList) {
    log.info("Describing items in ${agentType}")

    List<CacheData> data = imageList.collect { Image image ->
      if (image.lifecycleState != Image.LifecycleState.Available) {
        return null
      }
      Map<String, Object> attributes = objectMapper.convertValue(image, ATTRIBUTES)

      // Add Shapes to the image data
      def shapesResponse = credentials.computeClient.listShapes(ListShapesRequest.builder()
        .compartmentId(credentials.compartmentId)
        .imageId(image.id)
        .build())

      // Shapes are per-AD so we get multiple copies of the compatible shapes
      List<Shape> unique = shapesResponse?.items?.unique { it.shape }
      attributes.put("compatibleShapes", unique.collect { it.shape })

      new DefaultCacheData(
        Keys.getImageKey(credentials.name, credentials.region, image.id),
        attributes,
        [:]
      )
    }
    data.removeAll { it == null }
    def cacheData = [(Keys.Namespace.IMAGES.ns): data]
    log.info("Caching ${data.size()} items in ${agentType}")
    return new DefaultCacheResult(cacheData, [:])
  }
}
