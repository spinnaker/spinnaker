/*
 * Copyright (c) 2017 Oracle America, Inc.
 *
 * The contents of this file are subject to the Apache License Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * If a copy of the Apache License Version 2.0 was not distributed with this file,
 * You can obtain one at https://www.apache.org/licenses/LICENSE-2.0.html
 */
package com.netflix.spinnaker.clouddriver.oraclebmcs.provider.view

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.cats.cache.Cache
import com.netflix.spinnaker.cats.cache.CacheData
import com.netflix.spinnaker.cats.cache.RelationshipCacheFilter
import com.netflix.spinnaker.clouddriver.oraclebmcs.OracleBMCSCloudProvider
import com.netflix.spinnaker.clouddriver.oraclebmcs.cache.Keys
import com.netflix.spinnaker.clouddriver.oraclebmcs.model.OracleBMCSImage
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

import static com.netflix.spinnaker.clouddriver.oraclebmcs.cache.Keys.Namespace.IMAGES

@Component
class OracleBMCSImageProvider {

  private final Cache cacheView
  final ObjectMapper objectMapper

  final String cloudProvider = OracleBMCSCloudProvider.ID

  @Autowired
  OracleBMCSImageProvider(Cache cacheView, ObjectMapper objectMapper) {
    this.cacheView = cacheView
    this.objectMapper = objectMapper
  }

  Set<OracleBMCSImage> getAll() {
    getAllMatchingKeyPattern(Keys.getImageKey('*', '*', '*'))
  }

  Set<OracleBMCSImage> getByAccountAndRegion(String account, region) {
    getAllMatchingKeyPattern(Keys.getImageKey(account, region, '*'))
  }

  Set<OracleBMCSImage> getAllMatchingKeyPattern(String pattern) {
    loadResults(cacheView.filterIdentifiers(IMAGES.ns, pattern))
  }

  Set<OracleBMCSImage> loadResults(Collection<String> identifiers) {
    def data = cacheView.getAll(IMAGES.ns, identifiers, RelationshipCacheFilter.none())
    def transformed = data.collect(this.&fromCacheData)
    return transformed
  }

  OracleBMCSImage fromCacheData(CacheData cacheData) {
    Map<String, String> parts = Keys.parse(cacheData.id)

    // The cache data is a composite of Image and a list of Shapes, so we don't use an object mapper.
    // We just extract the data we want from the attributes.
    return new OracleBMCSImage(
      cloudProvider: this.cloudProvider,
      id: cacheData.attributes.id,
      name: cacheData.attributes.displayName,
      account: parts.account,
      region: parts.region,
      compatibleShapes: cacheData.attributes.compatibleShapes
    )
  }
}
