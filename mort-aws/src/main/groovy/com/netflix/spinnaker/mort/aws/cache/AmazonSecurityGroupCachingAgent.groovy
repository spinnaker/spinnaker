/*
 * Copyright 2014 Netflix, Inc.
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

package com.netflix.spinnaker.mort.aws.cache

import com.amazonaws.services.ec2.AmazonEC2
import com.amazonaws.services.ec2.model.IpPermission
import com.amazonaws.services.ec2.model.SecurityGroup
import com.netflix.spinnaker.mort.aws.model.AmazonSecurityGroup
import com.netflix.spinnaker.mort.model.AddressableRange
import com.netflix.spinnaker.mort.model.CacheService
import com.netflix.spinnaker.mort.model.CachingAgent
import com.netflix.spinnaker.mort.model.securitygroups.IpRangeRule
import com.netflix.spinnaker.mort.model.securitygroups.Rule
import com.netflix.spinnaker.mort.model.securitygroups.SecurityGroupRule
import groovy.transform.Canonical
import groovy.transform.Immutable
import groovy.util.logging.Slf4j

@Immutable(knownImmutables = ["ec2", "cacheService"])
@Slf4j
class AmazonSecurityGroupCachingAgent implements CachingAgent {
  final String account
  final String region
  final AmazonEC2 ec2
  final CacheService cacheService

  @Canonical
  static class GroupAndProtocol {
      String groupId, protocol
  }

  @Override
  String getDescription() {
      "[$account:$region:sgs]"
  }

  @Override
  int getIntervalMultiplier() {
      1
  }

  @Override
  void call() {
    log.info "$description - Caching..."
    List<SecurityGroup> currentSecurityGroups = ec2.describeSecurityGroups().securityGroups
    Map<String, AmazonSecurityGroup> currentSecurityGroupsByKey = currentSecurityGroups.collectEntries([:]) {
      def key = Keys.getSecurityGroupKey(it.groupName, it.groupId, region, account, it.vpcId)
      [ (key): convertToAmazonSecurityGroup(it)]
    }

    Collection<String> relevantCachedKeys = cacheService.keysByType(Keys.Namespace.SECURITY_GROUPS.ns).findAll {
      def parts = Keys.parse(it)
      parts.account == account && parts.region == region
    }
    Map<String, AmazonSecurityGroup> cachedSecurityGroupsByKey = relevantCachedKeys.collectEntries([:]) {
      [ (it): cacheService.retrieve(it, AmazonSecurityGroup)]
    }

    // evict currently missing ones
    Collection<String> removedKeys = cachedSecurityGroupsByKey.keySet() - currentSecurityGroupsByKey.keySet()
    removedKeys.each {
      log.info("Security group ${it} not found; removing from cache")
      cacheService.free(it)
    }

    // cache new or updated ones
    def addedOrUpdatedSecurityGroupsByKey = currentSecurityGroupsByKey.entrySet() - cachedSecurityGroupsByKey.entrySet()
    addedOrUpdatedSecurityGroupsByKey.each { it ->
      cacheService.put(it.key, it.value)
    }
  }

  private AmazonSecurityGroup convertToAmazonSecurityGroup(SecurityGroup securityGroup) {
    Map<GroupAndProtocol, Map> rules = [:]
    Map<String, Map> ipRangeRules = [:]

    securityGroup.ipPermissions.each { permission ->
      addIpRangeRules(permission, ipRangeRules)
      addSecurityGroupRules(permission, rules)
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

  private void addSecurityGroupRules(IpPermission permission, Map<GroupAndProtocol, Map> rules) {
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
}
