/*
 * Copyright 2015 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.mort.aws.provider.view

import com.amazonaws.services.ec2.model.IpPermission
import com.amazonaws.services.ec2.model.SecurityGroup
import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.cats.cache.Cache
import com.netflix.spinnaker.cats.cache.CacheData
import com.netflix.spinnaker.cats.cache.RelationshipCacheFilter
import com.netflix.spinnaker.mort.aws.cache.Keys
import com.netflix.spinnaker.mort.aws.model.AmazonSecurityGroup
import com.netflix.spinnaker.mort.model.AddressableRange
import com.netflix.spinnaker.mort.model.SecurityGroupProvider
import com.netflix.spinnaker.mort.model.securitygroups.IpRangeRule
import com.netflix.spinnaker.mort.model.securitygroups.Rule
import com.netflix.spinnaker.mort.model.securitygroups.SecurityGroupRule
import groovy.transform.Canonical
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

import static com.netflix.spinnaker.mort.aws.cache.Keys.Namespace.SECURITY_GROUPS

@Component
class AmazonSecurityGroupProvider implements SecurityGroupProvider<AmazonSecurityGroup> {

  final Cache cacheView
  final ObjectMapper objectMapper

  @Autowired
  AmazonSecurityGroupProvider(Cache cacheView, ObjectMapper objectMapper) {
    this.cacheView = cacheView
    this.objectMapper = objectMapper
  }

  @Override
  String getType() {
    return "aws"
  }

  @Override
  Set<AmazonSecurityGroup> getAll() {
    cacheView.getAll(SECURITY_GROUPS.ns, RelationshipCacheFilter.none()).collect(this.&fromCacheData)
  }

  @Override
  Set<AmazonSecurityGroup> getAllByRegion(String region) {
    getAllMatchingKeyPattern(Keys.getSecurityGroupKey('*', '*', region, '*', '*'))
  }

  @Override
  Set<AmazonSecurityGroup> getAllByAccount(String account) {
    getAllMatchingKeyPattern(Keys.getSecurityGroupKey('*', '*', '*', account, '*'))
  }

  @Override
  Set<AmazonSecurityGroup> getAllByAccountAndName(String account, String name) {
    getAllMatchingKeyPattern(Keys.getSecurityGroupKey(name, '*', '*', account, '*'))
  }

  @Override
  Set<AmazonSecurityGroup> getAllByAccountAndRegion(String account, String region) {
    getAllMatchingKeyPattern(Keys.getSecurityGroupKey('*', '*', region, account, '*'))
  }

  @Override
  AmazonSecurityGroup get(String account, String region, String name, String vpcId) {
    getAllMatchingKeyPattern(Keys.getSecurityGroupKey(name, '*', region, account, vpcId))[0]
  }

  Set<AmazonSecurityGroup> getAllMatchingKeyPattern(String pattern) {
    loadResults(cacheView.filterIdentifiers(SECURITY_GROUPS.ns, pattern))
  }

  Set<AmazonSecurityGroup> loadResults(Collection<String> identifiers) {
    cacheView.getAll(SECURITY_GROUPS.ns, identifiers, RelationshipCacheFilter.none()).collect(this.&fromCacheData)
  }

  AmazonSecurityGroup fromCacheData(CacheData cacheData) {
    SecurityGroup securityGroup = objectMapper.convertValue(cacheData.attributes, SecurityGroup)
    def parts = Keys.parse(cacheData.id)
    return convertToAmazonSecurityGroup(securityGroup, parts.account, parts.region)
  }


  private AmazonSecurityGroup convertToAmazonSecurityGroup(SecurityGroup securityGroup, String account, String region) {
    Map<GroupAndProtocol, Map> rules = [:]
    Map<String, Map> ipRangeRules = [:]

    securityGroup.ipPermissions.each { permission ->
      addIpRangeRules(permission, ipRangeRules)
      addSecurityGroupRules(permission, rules, account, region)
    }

    List<Rule> inboundRules = []
    inboundRules.addAll buildSecurityGroupRules(rules)
    inboundRules.addAll buildIpRangeRules(ipRangeRules)

    new AmazonSecurityGroup(
      id: securityGroup.groupId,
      name: securityGroup.groupName,
      vpcId: securityGroup.vpcId,
      description: securityGroup.description,
      accountName: account,
      region: region,
      inboundRules: inboundRules
    )
  }

  private List<IpRangeRule> buildIpRangeRules(LinkedHashMap<String, Map> ipRangeRules) {
    List<IpRangeRule> rangeRules = ipRangeRules.values().collect { rule ->
      new IpRangeRule(
        range: rule.range,
        protocol: rule.protocol,
        portRanges: rule.portRanges
      )

    }.sort()
    rangeRules
  }

  private List<SecurityGroupRule> buildSecurityGroupRules(LinkedHashMap<GroupAndProtocol, Map> rules) {
    List<SecurityGroupRule> securityGroupRules = rules.values().collect { rule ->
      new SecurityGroupRule(
        securityGroup: rule.securityGroup,
        portRanges: rule.portRanges,
        protocol: rule.protocol
      )
    }.sort()
    securityGroupRules
  }

  private void addSecurityGroupRules(IpPermission permission, Map<GroupAndProtocol, Map> rules, String account, String region) {
    permission.userIdGroupPairs.each { sg ->
      def groupAndProtocol = new GroupAndProtocol(sg.groupId, permission.ipProtocol)
      if (!rules.containsKey(groupAndProtocol)) {
        rules.put(groupAndProtocol, [
          protocol     : permission.ipProtocol,
          securityGroup:
            new AmazonSecurityGroup(
              id: sg.groupId,
              name: sg.groupName,
              accountName: account,
              region: region
            ),
          portRanges   : [] as SortedSet
        ])
      }
      rules.get(groupAndProtocol).portRanges += new Rule.PortRange(startPort: permission.fromPort, endPort: permission.toPort)
    }
  }

  private void addIpRangeRules(IpPermission permission, Map<String, Map> rules) {
    permission.ipRanges.each { ipRange ->
      if (!rules.containsKey(ipRange)) {
        def rangeParts = ipRange.split('/')
        rules.put(ipRange, [
          range     : new AddressableRange(ip: rangeParts[0], cidr: "/${rangeParts[1]}"),
          protocol  : permission.ipProtocol,
          portRanges: [] as SortedSet
        ])
      }
      rules.get(ipRange).portRanges += new Rule.PortRange(startPort: permission.fromPort, endPort: permission.toPort)
    }
  }

  @Canonical
  static class GroupAndProtocol {
    String groupId, protocol
  }
}
