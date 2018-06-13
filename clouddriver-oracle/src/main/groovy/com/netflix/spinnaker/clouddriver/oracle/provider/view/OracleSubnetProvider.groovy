/*
 * Copyright (c) 2017 Oracle America, Inc.
 *
 * The contents of this file are subject to the Apache License Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * If a copy of the Apache License Version 2.0 was not distributed with this file,
 * You can obtain one at https://www.apache.org/licenses/LICENSE-2.0.html
 */
package com.netflix.spinnaker.clouddriver.oracle.provider.view

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.cats.cache.Cache
import com.netflix.spinnaker.cats.cache.CacheData
import com.netflix.spinnaker.cats.cache.RelationshipCacheFilter
import com.netflix.spinnaker.clouddriver.model.SubnetProvider
import com.netflix.spinnaker.clouddriver.oracle.OracleCloudProvider
import com.netflix.spinnaker.clouddriver.oracle.cache.Keys
import com.netflix.spinnaker.clouddriver.oracle.model.OracleSubnet
import com.oracle.bmc.core.model.Subnet
import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

import static com.netflix.spinnaker.clouddriver.oracle.cache.Keys.Namespace.SUBNETS

@Slf4j
@Component
class OracleSubnetProvider implements SubnetProvider<OracleSubnet> {

  final Cache cacheView
  final ObjectMapper objectMapper
  final String cloudProvider = OracleCloudProvider.ID

  @Autowired
  OracleSubnetProvider(Cache cacheView, ObjectMapper objectMapper) {
    this.cacheView = cacheView
    this.objectMapper = objectMapper
  }

  @Override
  Set<OracleSubnet> getAll() {
    getAllMatchingKeyPattern(Keys.getSubnetKey('*', '*', '*'))
  }

  Set<OracleSubnet> getAllMatchingKeyPattern(String pattern) {
    loadResults(cacheView.filterIdentifiers(SUBNETS.ns, pattern))
  }

  Set<OracleSubnet> loadResults(Collection<String> identifiers) {
    def data = cacheView.getAll(SUBNETS.ns, identifiers, RelationshipCacheFilter.none())
    def transformed = data.collect(this.&fromCacheData)
    return transformed
  }

  OracleSubnet fromCacheData(CacheData cacheData) {
    Subnet subnet = objectMapper.convertValue(cacheData.attributes, Subnet)
    Map<String, String> parts = Keys.parse(cacheData.id)

    return new OracleSubnet(
      type: this.cloudProvider,
      id: subnet.id,
      name: subnet.displayName,
      vcnId: subnet.vcnId,
      availabilityDomain: subnet.availabilityDomain,
      securityListIds: subnet.securityListIds,
      account: parts.account,
      region: parts.region
    )
  }
}
