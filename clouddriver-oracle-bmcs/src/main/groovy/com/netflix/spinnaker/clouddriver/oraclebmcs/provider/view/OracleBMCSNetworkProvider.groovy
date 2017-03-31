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
import com.netflix.spinnaker.clouddriver.model.NetworkProvider
import com.netflix.spinnaker.clouddriver.oraclebmcs.OracleBMCSCloudProvider
import com.netflix.spinnaker.clouddriver.oraclebmcs.cache.Keys
import com.netflix.spinnaker.clouddriver.oraclebmcs.model.OracleBMCSNetwork
import com.oracle.bmc.core.model.Vcn
import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

@Slf4j
@Component
class OracleBMCSNetworkProvider implements NetworkProvider<OracleBMCSNetwork> {

  final Cache cacheView
  final ObjectMapper objectMapper
  final String cloudProvider = OracleBMCSCloudProvider.ID

  @Autowired
  OracleBMCSNetworkProvider(Cache cacheView, ObjectMapper objectMapper) {
    this.cacheView = cacheView
    this.objectMapper = objectMapper
  }

  @Override
  Set<OracleBMCSNetwork> getAll() {
    getAllMatchingKeyPattern(Keys.getNetworkKey('*', '*', '*', '*'))
  }

  Set<OracleBMCSNetwork> getAllMatchingKeyPattern(String pattern) {
    loadResults(cacheView.filterIdentifiers(Keys.Namespace.NETWORKS.ns, pattern))
  }

  Set<OracleBMCSNetwork> loadResults(Collection<String> identifiers) {
    def data = cacheView.getAll(Keys.Namespace.NETWORKS.ns, identifiers, RelationshipCacheFilter.none())
    def transformed = data.collect(this.&fromCacheData)

    return transformed
  }

  OracleBMCSNetwork fromCacheData(CacheData cacheData) {
    Vcn vcn = objectMapper.convertValue(cacheData.attributes, Vcn)
    Map<String, String> parts = Keys.parse(cacheData.id)

    return new OracleBMCSNetwork(
      cloudProvider: this.cloudProvider,
      id: vcn.id,
      name: vcn.displayName,
      account: parts.account,
      region: parts.region
    )
  }
}
