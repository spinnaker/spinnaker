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
import com.netflix.spinnaker.mort.model.*
import com.netflix.spinnaker.mort.model.securitygroups.*
import groovy.transform.Immutable
import groovy.util.logging.Slf4j
import rx.Observable

@Immutable(knownImmutables = ["ec2", "cacheService"])
@Slf4j
class AmazonSecurityGroupCachingAgent implements CachingAgent {
  final String account
  final String region
  final AmazonEC2 ec2
  final CacheService cacheService

  private Map<String, Integer> lastRun = [:]

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
    def result = ec2.describeSecurityGroups()
    def thisRun = [:]
    Observable.from(result.securityGroups).filter {
      !lastRun.containsKey(it.groupId) || lastRun.get(it.groupId) != it.hashCode()
    }.subscribe {
      thisRun.put(it.groupId, it.hashCode())
      cacheOne it
    }
    evictDeletedSecurityGroups(result.securityGroups)
    lastRun = thisRun
  }

  void evictDeletedSecurityGroups(List<SecurityGroup> currentSecurityGroups) {
    def relevantKeys = cacheService.keysByType(Keys.Namespace.SECURITY_GROUPS.ns).findAll {
      def parts = Keys.parse(it)
      parts.account == account && parts.region == region
    }
    relevantKeys.each { relevantKey ->
      def parts = Keys.parse(relevantKey)
      def match = currentSecurityGroups.find {
        it.groupName == parts.name && (it.vpcId ?: 'null') == parts.vpcId
      }
      if (!match) {
        log.info("Security group ${relevantKey} not found; removing from cache")
        cacheService.free(relevantKey)
      }
    }
  }

  void cacheOne(SecurityGroup securityGroup) {
    Map<String, Map> rules = [:]
    Map<String, Map> ipRangeRules = [:]

    securityGroup.ipPermissions.each { permission ->
      addIpRangeRules(permission, ipRangeRules)
      addSecurityGroupRules(permission, rules)
    }

    List<Rule> inboundRules = []
    inboundRules.addAll buildSecurityGroupRules(rules)
    inboundRules.addAll buildIpRangeRules(ipRangeRules)

    cacheService.put(
        Keys.getSecurityGroupKey(securityGroup.groupName, securityGroup.groupId, region, account, securityGroup.vpcId),
        new AmazonSecurityGroup(
            id: securityGroup.groupId,
            vpcId: securityGroup.vpcId,
            name: securityGroup.groupName,
            description: securityGroup.description,
            accountName: account,
            region: region,
            inboundRules: inboundRules
        )
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

  private List<SecurityGroupRule> buildSecurityGroupRules(LinkedHashMap<String, Map> rules) {
    List<SecurityGroupRule> securityGroupRules = rules.values().collect { rule ->
      new SecurityGroupRule(
          securityGroup: rule.securityGroup,
          portRanges: rule.portRanges,
          protocol: rule.protocol
      )
    }.sort()
    securityGroupRules
  }

  private void addSecurityGroupRules(IpPermission permission, Map<String, Map> rules) {
    permission.userIdGroupPairs.each { sg ->
      if (!rules.containsKey(sg.groupId)) {
        rules.put(sg.groupId, [
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
      rules.get(sg.groupId).portRanges += new Rule.PortRange(startPort: permission.fromPort, endPort: permission.toPort)
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
