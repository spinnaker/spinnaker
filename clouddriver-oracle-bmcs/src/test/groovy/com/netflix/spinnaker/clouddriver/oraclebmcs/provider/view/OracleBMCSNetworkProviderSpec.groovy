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
import com.netflix.spinnaker.clouddriver.oraclebmcs.cache.Keys
import spock.lang.Specification

class OracleBMCSNetworkProviderSpec extends Specification {

  def "get all networks from cache"() {
    setup:
    def cache = Mock(Cache)
    def networkProvider = new OracleBMCSNetworkProvider(cache, new ObjectMapper())
    def identifiers = Mock(Collection)
    def attributes = ["displayName": "My Vcn", "id": "ocid.vcn.123"]
    def mockData = Mock(CacheData)
    Collection<CacheData> cacheData = [mockData]
    def id = "${OracleBMCSCloudProvider.ID}:${Keys.Namespace.NETWORKS}:My Vcn:ocid.vcn.123:us-phoenix-1:DEFAULT"

    when:
    def results = networkProvider.getAll()

    then:
    1 * cache.filterIdentifiers(Keys.Namespace.NETWORKS.ns, "${OracleBMCSCloudProvider.ID}:$Keys.Namespace.NETWORKS:*:*:*:*") >> identifiers
    1 * cache.getAll(Keys.Namespace.NETWORKS.ns, identifiers, _) >> cacheData
    1 * mockData.attributes >> attributes
    1 * mockData.id >> id
    results?.first()?.name == attributes["displayName"]
    results?.first()?.id == attributes["id"]
    results?.first()?.region == "us-phoenix-1"
    results?.first()?.account == "DEFAULT"
    noExceptionThrown()
  }
}
