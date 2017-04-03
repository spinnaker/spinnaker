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

import static com.netflix.spinnaker.clouddriver.oraclebmcs.cache.Keys.Namespace.SUBNETS

class OracleBMCSSubnetProviderSpec extends Specification {

  def "get all subnets from cache"() {
    setup:
    def cache = Mock(Cache)
    def subnetProvider = new OracleBMCSSubnetProvider(cache, new ObjectMapper())
    def identifiers = Mock(Collection)
    def attributes = ["displayName": "My Subnet", "id": "ocid.subnet.123", "availabilityDomain": "AD1", "securityListIds": ["ocid.seclist.123"]]
    def mockData = Mock(CacheData)
    Collection<CacheData> cacheData = [mockData]
    def id = "${OracleBMCSCloudProvider.ID}:${SUBNETS}:ocid.subnet.123:us-phoenix-1:DEFAULT"

    when:
    def results = subnetProvider.getAll()

    then:
    1 * cache.filterIdentifiers(SUBNETS.ns, "${OracleBMCSCloudProvider.ID}:$SUBNETS:*:*:*") >> identifiers
    1 * cache.getAll(SUBNETS.ns, identifiers, _) >> cacheData
    1 * mockData.attributes >> attributes
    1 * mockData.id >> id
    results?.first()?.name == attributes["displayName"]
    results?.first()?.id == attributes["id"]
    results?.first()?.region == "us-phoenix-1"
    results?.first()?.account == "DEFAULT"
    results?.first()?.availabilityDomain == "AD1"
    results?.first()?.securityListIds == ["ocid.seclist.123"]
    noExceptionThrown()
  }
}
