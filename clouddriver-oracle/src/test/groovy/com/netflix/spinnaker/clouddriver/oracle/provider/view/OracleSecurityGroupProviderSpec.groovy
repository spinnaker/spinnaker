/*
 * Copyright (c) 2017, 2018, Oracle Corporation and/or its affiliates. All rights reserved.
 *
 * The contents of this file are subject to the Apache License Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * If a copy of the Apache License Version 2.0 was not distributed with this file,
 * You can obtain one at https://www.apache.org/licenses/LICENSE-2.0.html
 */

package com.netflix.spinnaker.clouddriver.oracle.provider.view

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.ser.impl.SimpleFilterProvider
import com.netflix.spinnaker.cats.cache.DefaultCacheData
import com.netflix.spinnaker.cats.mem.InMemoryCache
import com.netflix.spinnaker.clouddriver.oracle.OracleCloudProvider
import com.netflix.spinnaker.clouddriver.oracle.cache.Keys
import com.oracle.bmc.core.model.IngressSecurityRule
import com.oracle.bmc.core.model.PortRange
import com.oracle.bmc.core.model.SecurityList
import com.oracle.bmc.core.model.TcpOptions
import spock.lang.Specification

@spock.lang.Ignore("pass on local runs, failed on travisCI.")
class OracleSecurityGroupProviderSpec extends Specification {

  ObjectMapper objectMapper = new ObjectMapper().setFilterProvider(new SimpleFilterProvider().setFailOnUnknownId(false))

  def "get all"() {
    setup:
    def cache = new InMemoryCache()
    cache.mergeAll(Keys.Namespace.SECURITY_GROUPS.ns, [
      buildSecGroupCacheData(1, "R1", "A1"),
      buildSecGroupCacheData(2, "R1", "A1"),
      buildSecGroupCacheData(3, "R2", "A1"),
      buildSecGroupCacheData(4, "R2", "A1"),
    ])
    def securityGroupProvider = new OracleSecurityGroupProvider(cache, objectMapper)

    when:
    def results = securityGroupProvider.getAll(true).sort { a, b -> a.name.compareTo(b.name) }

    then:
    results.size() == 4
    results.first().name == "Sec Group 1"
    results.first().accountName == "A1"
    results.first().region == "R1"
    results.first().cloudProvider == OracleCloudProvider.ID
    results.first().inboundRules.size() == 1
  }

  def "get all without rules"() {
    setup:
    def cache = new InMemoryCache()
    cache.mergeAll(Keys.Namespace.SECURITY_GROUPS.ns, [
      buildSecGroupCacheData(1, "R1", "A1"),
      buildSecGroupCacheData(2, "R1", "A1"),
      buildSecGroupCacheData(3, "R2", "A1"),
      buildSecGroupCacheData(4, "R2", "A1"),
    ])
    def securityGroupProvider = new OracleSecurityGroupProvider(cache, objectMapper)

    when:
    def results = securityGroupProvider.getAll(false).sort { a, b -> a.name.compareTo(b.name) }

    then:
    results.size() == 4
    results.collect { it.inboundRules }.flatten() == []
  }

  def "get by region"(def region, def res) {
    setup:
    def cache = new InMemoryCache()
    cache.mergeAll(Keys.Namespace.SECURITY_GROUPS.ns, [
      buildSecGroupCacheData(1, "R1", "A1"),
      buildSecGroupCacheData(2, "R1", "A1"),
      buildSecGroupCacheData(3, "R2", "A1"),
      buildSecGroupCacheData(4, "R2", "A1"),
    ])
    def securityGroupProvider = new OracleSecurityGroupProvider(cache, objectMapper)

    expect:
    securityGroupProvider.getAllByRegion(true, region).collect { it.name } as Set == res as Set

    where:
    region || res
    "R1"   || ["Sec Group 1", "Sec Group 2"]
    "R2"   || ["Sec Group 3", "Sec Group 4"]
  }

  def "get by account"(def account, def res) {
    setup:
    def cache = new InMemoryCache()
    cache.mergeAll(Keys.Namespace.SECURITY_GROUPS.ns, [
      buildSecGroupCacheData(1, "R1", "A1"),
      buildSecGroupCacheData(2, "R1", "A2"),
      buildSecGroupCacheData(3, "R2", "A1"),
      buildSecGroupCacheData(4, "R2", "A2"),
    ])
    def securityGroupProvider = new OracleSecurityGroupProvider(cache, objectMapper)

    expect:
    securityGroupProvider.getAllByAccount(true, account).collect { it.name } as Set == res as Set

    where:
    account || res
    "A1"    || ["Sec Group 1", "Sec Group 3"]
    "A2"    || ["Sec Group 2", "Sec Group 4"]
  }

  def "get by account and name"() {
    setup:
    def cache = new InMemoryCache()
    cache.mergeAll(Keys.Namespace.SECURITY_GROUPS.ns, [
      buildSecGroupCacheData(1, "R1", "A1"),
      buildSecGroupCacheData(2, "R2", "A2"),
      buildSecGroupCacheData(1, "R3", "A1"),
      buildSecGroupCacheData(2, "R4", "A2"),
    ])
    def securityGroupProvider = new OracleSecurityGroupProvider(cache, objectMapper)

    when:
    def results = securityGroupProvider.getAllByAccountAndName(true, "A1", "Sec Group 1")

    then:
    results.size() == 2
    results.find { it.region == "R1" } != null
    results.find { it.region == "R3" } != null
  }

  def "get by region and account"(def region, def account, def res) {
    setup:
    def cache = new InMemoryCache()
    cache.mergeAll(Keys.Namespace.SECURITY_GROUPS.ns, [
      buildSecGroupCacheData(1, "R1", "A1"),
      buildSecGroupCacheData(2, "R1", "A2"),
      buildSecGroupCacheData(3, "R1", "A1"),
      buildSecGroupCacheData(4, "R2", "A1"),
      buildSecGroupCacheData(5, "R2", "A2"),
    ])
    def securityGroupProvider = new OracleSecurityGroupProvider(cache, objectMapper)

    expect:
    securityGroupProvider.getAllByAccountAndRegion(true, account, region).collect { it.name } as Set == res as Set

    where:
    region | account || res
    "R1"   | "A1"    || ["Sec Group 1", "Sec Group 3"]
    "R2"   | "A1"    || ["Sec Group 4"]
  }

  def "get single"() {
    setup:
    def cache = new InMemoryCache()
    cache.mergeAll(Keys.Namespace.SECURITY_GROUPS.ns, [
      buildSecGroupCacheData(1, "R1", "A1"),
      buildSecGroupCacheData(2, "R2", "A2"),
      buildSecGroupCacheData(1, "R3", "A1"),
    ])
    def securityGroupProvider = new OracleSecurityGroupProvider(cache, objectMapper)

    when:
    def result = securityGroupProvider.get("A1", "R3", "Sec Group 1", "ocid.vcn.123")

    then:
    result != null
    result.name == "Sec Group 1"
  }

  def buildSecGroupCacheData(def num, def region, def account) {
    def name = "Sec Group $num"
    def ocid = "ocid.seclist.$num"
    def sl = SecurityList.builder().id(ocid).displayName(name).vcnId("ocid.vcn.123").ingressSecurityRules(
      [IngressSecurityRule.builder().protocol("tcp").tcpOptions(
        TcpOptions.builder().destinationPortRange(
          PortRange.builder().min(80).max(80).build()
        ).build()
      ).build()]
    )
    Map<String, Object> attributes = objectMapper.convertValue(sl, new TypeReference<Map<String, Object>>() {})

    return new DefaultCacheData(
      Keys.getSecurityGroupKey(name, ocid, region, account),
      attributes,
      [:]
    )
  }
}
