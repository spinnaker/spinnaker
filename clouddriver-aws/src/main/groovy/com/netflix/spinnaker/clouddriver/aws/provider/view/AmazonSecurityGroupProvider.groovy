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

package com.netflix.spinnaker.clouddriver.aws.provider.view

import com.amazonaws.services.ec2.model.IpPermission
import com.amazonaws.services.ec2.model.SecurityGroup
import com.fasterxml.jackson.databind.ObjectMapper
import com.google.common.collect.ImmutableSet
import com.netflix.spinnaker.cats.cache.Cache
import com.netflix.spinnaker.cats.cache.CacheData
import com.netflix.spinnaker.cats.cache.RelationshipCacheFilter
import com.netflix.spinnaker.clouddriver.aws.AmazonCloudProvider
import com.netflix.spinnaker.clouddriver.aws.security.AmazonCredentials
import com.netflix.spinnaker.clouddriver.aws.security.NetflixAmazonCredentials
import com.netflix.spinnaker.clouddriver.model.AddressableRange
import com.netflix.spinnaker.clouddriver.model.SecurityGroupProvider
import com.netflix.spinnaker.clouddriver.model.securitygroups.IpRangeRule
import com.netflix.spinnaker.clouddriver.model.securitygroups.Rule
import com.netflix.spinnaker.clouddriver.model.securitygroups.SecurityGroupRule
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsProvider
import com.netflix.spinnaker.clouddriver.aws.cache.Keys
import com.netflix.spinnaker.clouddriver.aws.model.AmazonSecurityGroup
import groovy.transform.Canonical
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

import static com.netflix.spinnaker.clouddriver.aws.cache.Keys.Namespace.SECURITY_GROUPS

@Component
class AmazonSecurityGroupProvider implements SecurityGroupProvider<AmazonSecurityGroup> {

  final AmazonCloudProvider amazonCloudProvider
  final AccountCredentialsProvider accountCredentialsProvider
  final Cache cacheView
  final ObjectMapper objectMapper
  final Set<AmazonCredentials> accounts

  @Autowired
  AmazonSecurityGroupProvider(AmazonCloudProvider amazonCloudProvider,
                              AccountCredentialsProvider accountCredentialsProvider,
                              Cache cacheView,
                              ObjectMapper objectMapper) {
    this.amazonCloudProvider = amazonCloudProvider
    this.accountCredentialsProvider = accountCredentialsProvider
    this.cacheView = cacheView
    this.objectMapper = objectMapper

    final allAmazonCredentials = (Set<AmazonCredentials>) accountCredentialsProvider.all.findAll {
      it instanceof AmazonCredentials
    }
    accounts = ImmutableSet.copyOf(allAmazonCredentials)
  }

  @Override
  String getType() {
    return amazonCloudProvider.id
  }

  @Override
  Set<AmazonSecurityGroup> getAll(boolean includeRules) {
    getAllMatchingKeyPattern(Keys.getSecurityGroupKey(amazonCloudProvider, '*', '*', '*', '*', '*'), includeRules)
  }

  @Override
  Set<AmazonSecurityGroup> getAllByRegion(boolean includeRules, String region) {
    getAllMatchingKeyPattern(Keys.getSecurityGroupKey(amazonCloudProvider, '*', '*', region, '*', '*'), includeRules)
  }

  @Override
  Set<AmazonSecurityGroup> getAllByAccount(boolean includeRules, String account) {
    getAllMatchingKeyPattern(Keys.getSecurityGroupKey(amazonCloudProvider, '*', '*', '*', account, '*'), includeRules)
  }

  @Override
  Set<AmazonSecurityGroup> getAllByAccountAndName(boolean includeRules, String account, String name) {
    getAllMatchingKeyPattern(Keys.getSecurityGroupKey(amazonCloudProvider, name, '*', '*', account, '*'), includeRules)
  }

  @Override
  Set<AmazonSecurityGroup> getAllByAccountAndRegion(boolean includeRules, String account, String region) {
    getAllMatchingKeyPattern(Keys.getSecurityGroupKey(amazonCloudProvider, '*', '*', region, account, '*'), includeRules)
  }

  @Override
  AmazonSecurityGroup get(String account, String region, String name, String vpcId) {
    getAllMatchingKeyPattern(Keys.getSecurityGroupKey(amazonCloudProvider, name, '*', region, account, vpcId), true)[0]
  }

  Set<AmazonSecurityGroup> getAllMatchingKeyPattern(String pattern, boolean includeRules) {
    loadResults(includeRules, cacheView.filterIdentifiers(SECURITY_GROUPS.ns, pattern))
  }

  Set<AmazonSecurityGroup> loadResults(boolean includeRules, Collection<String> identifiers) {
    def transform = this.&fromCacheData.curry(includeRules)
    def data = cacheView.getAll(SECURITY_GROUPS.ns, identifiers, RelationshipCacheFilter.none())
    def transformed = data.collect(transform)

    return transformed
  }

  AmazonSecurityGroup fromCacheData(boolean includeRules, CacheData cacheData) {
    Map<String, String> parts = Keys.parse(amazonCloudProvider, cacheData.id)
    return convertToAmazonSecurityGroup(includeRules, cacheData.attributes, parts.account, parts.region)
  }

  private AmazonSecurityGroup convertToAmazonSecurityGroup(boolean includeRules, Map<String, Object> securityGroup, String account, String region) {
    List<Rule> inboundRules = []

    if (includeRules) {
      SecurityGroup amznSecurityGroup = objectMapper.convertValue(securityGroup, SecurityGroup)
      Map<GroupAndProtocol, Map> rules = [:]
      Map<String, Map> ipRangeRules = [:]
      amznSecurityGroup.ipPermissions.each { permission ->
        addIpRangeRules(permission, ipRangeRules)
        addSecurityGroupRules(permission, rules, account, region)
      }
      inboundRules.addAll buildSecurityGroupRules(rules)
      inboundRules.addAll buildIpRangeRules(ipRangeRules)
    }

    new AmazonSecurityGroup(
      type: amazonCloudProvider.id,
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
        final ingressAccount = accounts.find { it.accountId == sg.userId }
        rules.put(groupAndProtocol, [
          protocol     : permission.ipProtocol,
          securityGroup:
            new AmazonSecurityGroup(
              type: amazonCloudProvider.id,
              id: sg.groupId,
              name: sg.groupName,
              accountId: sg.userId,
              accountName: ingressAccount?.name,
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
