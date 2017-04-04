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
import com.netflix.spinnaker.clouddriver.oraclebmcs.OracleBMCSCloudProvider
import spock.lang.Specification

import static com.netflix.spinnaker.clouddriver.oraclebmcs.cache.Keys.Namespace.IMAGES

class OracleBMCSImageProviderSpec extends Specification {

  def "get all images from cache"() {
    setup:
    def cache = Mock(Cache)
    def imageProvider = new OracleBMCSImageProvider(cache, new ObjectMapper())
    def identifiers = Mock(Collection)
    def attributes = ["displayName": "My Image", "id": "ocid.image.123", "compatibleShapes": ["small"]]
    def mockData = Mock(CacheData)
    Collection<CacheData> cacheData = [mockData]
    def id = "${OracleBMCSCloudProvider.ID}:${IMAGES}:DEFAULT:us-phoenix-1:ocid.image.123"

    when:
    def results = imageProvider.getAll()

    then:
    1 * cache.filterIdentifiers(IMAGES.ns, "${OracleBMCSCloudProvider.ID}:$IMAGES:*:*:*") >> identifiers
    1 * cache.getAll(IMAGES.ns, identifiers, _) >> cacheData
    1 * mockData.id >> id
    mockData.attributes >> attributes
    results?.first()?.name == attributes["displayName"]
    results?.first()?.id == attributes["id"]
    results?.first()?.region == "us-phoenix-1"
    results?.first()?.account == "DEFAULT"
    results?.first()?.compatibleShapes == ["small"]
    noExceptionThrown()
  }
}
