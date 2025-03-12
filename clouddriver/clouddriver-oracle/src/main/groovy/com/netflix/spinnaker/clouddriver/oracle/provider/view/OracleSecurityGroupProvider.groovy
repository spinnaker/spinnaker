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
import com.netflix.spinnaker.clouddriver.model.SecurityGroupProvider
import com.netflix.spinnaker.clouddriver.model.securitygroups.Rule
import com.netflix.spinnaker.clouddriver.model.securitygroups.SecurityGroupRule
import com.netflix.spinnaker.clouddriver.oracle.OracleCloudProvider
import com.netflix.spinnaker.clouddriver.oracle.cache.Keys
import com.netflix.spinnaker.clouddriver.oracle.model.OracleSecurityGroup
import com.oracle.bmc.core.model.SecurityList
import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

@Slf4j
@Component
class OracleSecurityGroupProvider implements SecurityGroupProvider<OracleSecurityGroup> {
  final String cloudProvider = OracleCloudProvider.ID
  private final Cache cacheView
  private final ObjectMapper objectMapper

  @Autowired
  OracleSecurityGroupProvider(Cache cacheView, ObjectMapper objectMapper) {
    this.cacheView = cacheView
    this.objectMapper = objectMapper
  }

  @Override
  Set<OracleSecurityGroup> getAll(boolean includeRules) {
    getAllMatchingKeyPattern(Keys.getSecurityGroupKey('*', '*', '*', '*'), includeRules)
  }

  @Override
  Set<OracleSecurityGroup> getAllByRegion(boolean includeRules, String region) {
    getAllMatchingKeyPattern(Keys.getSecurityGroupKey('*', '*', region, '*'), includeRules)
  }

  @Override
  Set<OracleSecurityGroup> getAllByAccount(boolean includeRules, String account) {
    getAllMatchingKeyPattern(Keys.getSecurityGroupKey('*', '*', '*', account), includeRules)
  }

  @Override
  Set<OracleSecurityGroup> getAllByAccountAndName(boolean includeRules, String account, String name) {
    getAllMatchingKeyPattern(Keys.getSecurityGroupKey(name, '*', '*', account), includeRules)
  }

  @Override
  Set<OracleSecurityGroup> getAllByAccountAndRegion(boolean includeRules, String account, String region) {
    getAllMatchingKeyPattern(Keys.getSecurityGroupKey('*', '*', region, account), includeRules)
  }

  @Override
  OracleSecurityGroup get(String account, String region, String name, String vpcId) {
    // We ignore vpcId here.
    getAllMatchingKeyPattern(Keys.getSecurityGroupKey(name, '*', region, account), true)[0]
  }

  @Override
  OracleSecurityGroup getById(String account, String region, String id, String vpcId) {
    // We ignore vpcId here.
    getAllMatchingKeyPattern(Keys.getSecurityGroupKey('*', id, region, account), true)[0]
  }

  Set<OracleSecurityGroup> getAllMatchingKeyPattern(String pattern, boolean includeRules) {
    def identifiers = cacheView.filterIdentifiers(Keys.Namespace.SECURITY_GROUPS.ns, pattern)
    return loadResults(includeRules, identifiers)
  }

  Set<OracleSecurityGroup> loadResults(boolean includeRules, Collection<String> identifiers) {
    def transform = this.&fromCacheData.curry(includeRules)
    def data = cacheView.getAll(Keys.Namespace.SECURITY_GROUPS.ns, identifiers, RelationshipCacheFilter.none())
    def transformed = data.collect(transform)
    return transformed as Set<OracleSecurityGroup>
  }

  OracleSecurityGroup fromCacheData(boolean includeRules, CacheData cacheData) {
    SecurityList secList = objectMapper.convertValue(cacheData.attributes, SecurityList)
    Map<String, String> parts = Keys.parse(cacheData.id)

    def ruleTransformer = {
      // TODO: Support UDP/ICMP
      def ranges = []
      if (it?.tcpOptions?.destinationPortRange) {
        ranges << new Rule.PortRange(startPort: it.tcpOptions.destinationPortRange.min, endPort: it.tcpOptions.destinationPortRange.max)
      } else {
        return null
      }
      return new SecurityGroupRule(protocol: "TCP", portRanges: new TreeSet<Rule.PortRange>(ranges))
    }

    def inRules = includeRules ? secList.ingressSecurityRules.collect(ruleTransformer) : []
    inRules.removeAll { it == null }
    def outRules = includeRules ? secList.egressSecurityRules.collect(ruleTransformer) : []
    outRules.removeAll { it == null }

    return new OracleSecurityGroup(
      type: OracleCloudProvider.ID,
      cloudProvider: OracleCloudProvider.ID,
      id: secList.id,
      name: secList.displayName,
      description: secList.displayName,
      accountName: parts.account,
      region: parts.region,
      network: secList.vcnId,
      inboundRules: inRules as Set<SecurityGroupRule>,
      outboundRules: outRules as Set<SecurityGroupRule>
    )
  }

}
