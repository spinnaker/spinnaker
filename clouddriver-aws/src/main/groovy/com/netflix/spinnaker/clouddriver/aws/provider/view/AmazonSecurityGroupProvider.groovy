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
import com.amazonaws.services.ec2.model.UserIdGroupPair
import com.fasterxml.jackson.databind.ObjectMapper
import com.google.common.collect.ImmutableSet
import com.netflix.spinnaker.cats.cache.Cache
import com.netflix.spinnaker.cats.cache.CacheData
import com.netflix.spinnaker.cats.cache.RelationshipCacheFilter
import com.netflix.spinnaker.clouddriver.aws.AmazonCloudProvider
import com.netflix.spinnaker.clouddriver.aws.cache.Keys
import com.netflix.spinnaker.clouddriver.aws.model.AmazonSecurityGroup
import com.netflix.spinnaker.clouddriver.aws.security.AmazonCredentials
import com.netflix.spinnaker.clouddriver.model.AddressableRange
import com.netflix.spinnaker.clouddriver.model.SecurityGroupProvider
import com.netflix.spinnaker.clouddriver.model.securitygroups.IpRangeRule
import com.netflix.spinnaker.clouddriver.model.securitygroups.Rule
import com.netflix.spinnaker.clouddriver.model.securitygroups.SecurityGroupRule
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsProvider
import groovy.transform.Canonical
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Component

import static com.netflix.spinnaker.clouddriver.aws.cache.Keys.Namespace.SECURITY_GROUPS

@Component
class AmazonSecurityGroupProvider implements SecurityGroupProvider<AmazonSecurityGroup> {

  final String cloudProvider = AmazonCloudProvider.ID
  final AccountCredentialsProvider accountCredentialsProvider
  final Cache cacheView
  final ObjectMapper objectMapper
  final Set<AmazonCredentials> accounts

  @Autowired
  AmazonSecurityGroupProvider(AccountCredentialsProvider accountCredentialsProvider,
                              Cache cacheView,
                              @Qualifier("amazonObjectMapper") ObjectMapper objectMapper) {
    this.accountCredentialsProvider = accountCredentialsProvider
    this.cacheView = cacheView
    this.objectMapper = objectMapper

    final allAmazonCredentials = (Set<AmazonCredentials>) accountCredentialsProvider.all.findAll {
      it instanceof AmazonCredentials
    }
    accounts = ImmutableSet.copyOf(allAmazonCredentials)
  }

  @Override
  Collection<AmazonSecurityGroup> getAll(boolean includeRules) {
    if (!includeRules) {
      def identifiers = cacheView.filterIdentifiers(SECURITY_GROUPS.ns, Keys.getSecurityGroupKey('*', '*', '*', '*', '*'))
      return identifiers.collect {
        Map parts = Keys.parse(it)
        new AmazonSecurityGroup(
          id: parts.id,
          name: parts.name,
          vpcId: parts.vpcId,
          accountName: parts.account,
          region: parts.region,
          cloudProvider: AmazonCloudProvider.ID
        )
      }
    } else {
      return getAllMatchingKeyPattern(Keys.getSecurityGroupKey('*', '*', '*', '*', '*'), includeRules)
    }
  }

  @Override
  Collection<AmazonSecurityGroup> getAllByRegion(boolean includeRules, String region) {
    getAllMatchingKeyPattern(Keys.getSecurityGroupKey('*', '*', region, '*', '*'), includeRules)
  }

  @Override
  Collection<AmazonSecurityGroup> getAllByAccount(boolean includeRules, String account) {
    getAllMatchingKeyPattern(Keys.getSecurityGroupKey('*', '*', '*', account, '*'), includeRules)
  }

  @Override
  Collection<AmazonSecurityGroup> getAllByAccountAndName(boolean includeRules, String account, String name) {
    getAllMatchingKeyPattern(Keys.getSecurityGroupKey(name, '*', '*', account, '*'), includeRules)
  }

  @Override
  Collection<AmazonSecurityGroup> getAllByAccountAndRegion(boolean includeRules, String account, String region) {
    getAllMatchingKeyPattern(Keys.getSecurityGroupKey('*', '*', region, account, '*'), includeRules)
  }

  @Override
  AmazonSecurityGroup get(String account, String region, String name, String vpcId) {
    getAllMatchingKeyPattern(Keys.getSecurityGroupKey(name, '*', region, account, vpcId), true)[0]
  }

  AmazonSecurityGroup get(boolean includeRules, String account, String region, String name, String vpcId) {
    getAllMatchingKeyPattern(Keys.getSecurityGroupKey(name, '*', region, account, vpcId), includeRules)[0]
  }

  String getNameById(String account, String region, String securityGroupId, String vpcId) {
    String key = getCacheIdentifier(Keys.getSecurityGroupKey('*', securityGroupId, region, account, vpcId))
    if (key) {
      return Keys.parse(key).name
    } else {
      return null
    }
  }

  String getIdByName(String account, String region, String name, String vpcId) {
    String key = getCacheIdentifier(Keys.getSecurityGroupKey(name, "*", region, account, vpcId))
    if (key) {
      return Keys.parse(key).id
    } else {
      return null
    }
  }

  String getCacheIdentifier(String pattern) {
    Set ids = cacheView.filterIdentifiers(SECURITY_GROUPS.ns, pattern)
    if (ids.isEmpty()) {
      return null
    } else {
      return ids.toArray()[0]
    }
  }

  AmazonSecurityGroup getById(String account, String region, String securityGroupId, String vpcId) {
    getAllMatchingKeyPattern(Keys.getSecurityGroupKey('*', securityGroupId, region, account, vpcId), true)[0]
  }

  AmazonSecurityGroup getById(boolean includeRules, String account, String region, String securityGroupId, String vpcId) {
    getAllMatchingKeyPattern(Keys.getSecurityGroupKey('*', securityGroupId, region, account, vpcId), includeRules)[0]
  }

  Collection<AmazonSecurityGroup> getAllMatchingKeyPattern(String pattern, boolean includeRules) {
    loadResults(includeRules, cacheView.filterIdentifiers(SECURITY_GROUPS.ns, pattern))
  }

  Collection<AmazonSecurityGroup> loadResults(boolean includeRules, Collection<String> identifiers) {
    def transform = this.&fromCacheData.curry(includeRules)
    def data = cacheView.getAll(SECURITY_GROUPS.ns, identifiers, RelationshipCacheFilter.none())
    def transformed = data.collect(transform)

    return transformed
  }

  AmazonSecurityGroup fromCacheData(boolean includeRules, CacheData cacheData) {
    Map<String, String> parts = Keys.parse(cacheData.id)
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
        addSecurityGroupRules(permission, rules, account, region, securityGroup.vpcId)
      }
      inboundRules.addAll buildSecurityGroupRules(rules)
      inboundRules.addAll buildIpRangeRules(ipRangeRules)
    }

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

  private Map<String, String> getIngressGroupNameAndVpcId(UserIdGroupPair sg, String baseAccount, String ingressAccount, String region, String vpcId) {
    String ingressGroupName = sg.groupName
    String ingressGroupVpcId = vpcId
    // need to query if there's no name, or if the security groups are in different accounts, since they will have
    // different vpcIds.
    if (!ingressGroupName || baseAccount != ingressAccount) {
      def vpcPattern = vpcId ?: '*'
      if (baseAccount != ingressAccount) {
        vpcPattern = '*'
      }
      def keyPattern = Keys.getSecurityGroupKey('*', sg.groupId, region, ingressAccount ?: '*', vpcPattern)
      def matches = cacheView.filterIdentifiers(SECURITY_GROUPS.ns, keyPattern)
      if (matches) {
        def parts = Keys.parse(matches[0])
        ingressGroupName = parts.name
        ingressGroupVpcId = parts.vpcId
      }
    }
    return [name: ingressGroupName, vpcId: ingressGroupVpcId]
  }

  private void addSecurityGroupRules(IpPermission permission, Map<GroupAndProtocol, Map> rules, String account, String region, String vpcId) {
    permission.userIdGroupPairs.each { sg ->
      def groupAndProtocol = new GroupAndProtocol(sg.groupId, permission.ipProtocol)
      if (!rules.containsKey(groupAndProtocol)) {
        final ingressAccount = accounts.find { it.accountId == sg.userId }
        Map<String, String> ingressGroupSummary = getIngressGroupNameAndVpcId(sg, account, ingressAccount?.name, region, vpcId)
        rules.put(groupAndProtocol, [
          protocol     : permission.ipProtocol,
          securityGroup:
            new AmazonSecurityGroup(
              id: sg.groupId,
              name: ingressGroupSummary.name,
              accountId: sg.userId,
              accountName: ingressAccount?.name,
              region: region,
              vpcId: sg.vpcId ?: ingressGroupSummary.vpcId
            ),
          portRanges   : [] as SortedSet
        ])
      }
      rules.get(groupAndProtocol).portRanges += new Rule.PortRange(startPort: permission.fromPort, endPort: permission.toPort)
    }
  }

  private void addIpRangeRules(IpPermission permission, Map<String, Map> rules) {
    permission.ipv6Ranges.each { ipRange ->
      String key = "$ipRange:$permission.ipProtocol"
      if (!rules.containsKey(key)) {
        def rangeParts = ipRange.cidrIpv6.split('/')
        rules.put(key, [
          range     : new AddressableRange(ip: rangeParts[0], cidr: "/${rangeParts[1]}"),
          protocol  : permission.ipProtocol,
          portRanges: [] as SortedSet
        ])
      }
      rules.get(key).portRanges += new Rule.PortRange(startPort: permission.fromPort, endPort: permission.toPort)
    }
    permission.ipv4Ranges.each { ipRange ->
      String key = "$ipRange:$permission.ipProtocol"
      if (!rules.containsKey(key)) {
        def rangeParts = ipRange.cidrIp.split('/')
        rules.put(key, [
          range     : new AddressableRange(ip: rangeParts[0], cidr: "/${rangeParts[1]}"),
          protocol  : permission.ipProtocol,
          portRanges: [] as SortedSet
        ])
      }
      rules.get(key).portRanges += new Rule.PortRange(startPort: permission.fromPort, endPort: permission.toPort)
    }
  }

  @Canonical
  static class GroupAndProtocol {
    String groupId, protocol
  }
}
