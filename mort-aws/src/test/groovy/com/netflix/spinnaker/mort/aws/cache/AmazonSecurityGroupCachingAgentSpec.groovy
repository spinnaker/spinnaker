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
import com.amazonaws.services.ec2.model.DescribeSecurityGroupsResult
import com.amazonaws.services.ec2.model.IpPermission
import com.amazonaws.services.ec2.model.SecurityGroup
import com.amazonaws.services.ec2.model.UserIdGroupPair
import com.netflix.spinnaker.mort.aws.model.AmazonSecurityGroup
import com.netflix.spinnaker.mort.model.CacheService
import com.netflix.spinnaker.mort.model.securitygroups.IpRangeRule
import com.netflix.spinnaker.mort.model.securitygroups.SecurityGroupRule
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Subject

class AmazonSecurityGroupCachingAgentSpec extends Specification {

  @Subject
  AmazonSecurityGroupCachingAgent agent

  @Shared
  CacheService cacheService

  @Shared
  AmazonEC2 ec2

  static String region = 'region'
  static String account = 'account'

  def setup() {
    cacheService = Mock(CacheService)
    ec2 = Mock(AmazonEC2)
    agent = new AmazonSecurityGroupCachingAgent(
        cacheService: cacheService,
        ec2: ec2,
        region: region,
        account: account)
  }

  void "should add security groups on initial run"() {
    given:
    List initialGroups = [securityGroupA, securityGroupB]
    DescribeSecurityGroupsResult describeResult = Mock(DescribeSecurityGroupsResult)

    when:
    agent.call()

    then:
    1 * ec2.describeSecurityGroups() >> describeResult
    2 * describeResult.getSecurityGroups() >> initialGroups
    1 * cacheService.keysByType(Keys.Namespace.SECURITY_GROUPS.ns) >> [keyGroupA, keyGroupB]
    1 * cacheService.put(keyGroupA, _)
    1 * cacheService.put(keyGroupB, _)
    0 * _
  }

  void "should add missing security group on second run"() {
    given:
    DescribeSecurityGroupsResult initialDescribe = Mock(DescribeSecurityGroupsResult)
    DescribeSecurityGroupsResult secondDescribe = Mock(DescribeSecurityGroupsResult)

    when:
    agent.call()

    then:
    1 * ec2.describeSecurityGroups() >> initialDescribe
    2 * initialDescribe.getSecurityGroups() >> [securityGroupA]
    1 * cacheService.keysByType(Keys.Namespace.SECURITY_GROUPS.ns) >> [keyGroupA]
    1 * cacheService.put(keyGroupA, _)
    0 * _

    when:
    agent.call()

    then:
    1 * ec2.describeSecurityGroups() >> secondDescribe
    2 * secondDescribe.getSecurityGroups() >> [securityGroupA, securityGroupB]
    1 * cacheService.keysByType(Keys.Namespace.SECURITY_GROUPS.ns) >> [keyGroupA, keyGroupB]
    1 * cacheService.put(keyGroupB, _)
    0 * _
  }

  void "should evict security groups when not found on subsequent runs"() {
    given:
    DescribeSecurityGroupsResult initialDescribe = Mock(DescribeSecurityGroupsResult)
    DescribeSecurityGroupsResult secondDescribe = Mock(DescribeSecurityGroupsResult)

    when:
    agent.call()

    then:
    1 * ec2.describeSecurityGroups() >> initialDescribe
    2 * initialDescribe.getSecurityGroups() >> [securityGroupA, securityGroupB]
    1 * cacheService.put(keyGroupA, _)
    1 * cacheService.put(keyGroupB, _)
    1 * cacheService.keysByType(Keys.Namespace.SECURITY_GROUPS.ns) >> [keyGroupA, keyGroupB]
    0 * _

    when:
    securityGroupB.description = 'changed'
    agent.call()

    then:
    1 * ec2.describeSecurityGroups() >> secondDescribe
    2 * secondDescribe.getSecurityGroups() >> [securityGroupA]
    1 * cacheService.keysByType(Keys.Namespace.SECURITY_GROUPS.ns) >> [keyGroupA, keyGroupB]
    1 * cacheService.free(keyGroupB)
    0 * _
  }

  void "should update security group when security group value changes"() {
    given:
    DescribeSecurityGroupsResult initialDescribe = Mock(DescribeSecurityGroupsResult)
    DescribeSecurityGroupsResult secondDescribe = Mock(DescribeSecurityGroupsResult)

    when:
    agent.call()

    then:
    1 * ec2.describeSecurityGroups() >> initialDescribe
    2 * initialDescribe.getSecurityGroups() >> [securityGroupA, securityGroupB]
    1 * cacheService.keysByType(Keys.Namespace.SECURITY_GROUPS.ns) >> [keyGroupA, keyGroupB]
    1 * cacheService.put(keyGroupA, _)
    1 * cacheService.put(keyGroupB, _)
    0 * _

    when:
    securityGroupB.description = 'changed for the worse'
    agent.call()

    then:
    1 * ec2.describeSecurityGroups() >> secondDescribe
    2 * secondDescribe.getSecurityGroups() >> [securityGroupA, securityGroupB]
    1 * cacheService.keysByType(Keys.Namespace.SECURITY_GROUPS.ns) >> [keyGroupA, keyGroupB]
    1 * cacheService.put(keyGroupB, _)
    0 * _
  }

  void "should add both ipRangeRules and securityGroup rules"() {
    given:
    SecurityGroup mixedRangedGroupA = new SecurityGroup(
        groupId: 'id-a',
        groupName: 'name-a',
        description: 'a',
        ipPermissions: [
            new IpPermission(
                ipProtocol: 'tcp',
                fromPort: 7001,
                toPort: 8080,
                ipRanges: ['0.0.0.0/32', '0.0.0.1/31'],
                userIdGroupPairs: [
                    new UserIdGroupPair(groupId: 'id-b', groupName: 'name-b', userId: 'test')
                ]
            )
        ])

    def cachedValue

    DescribeSecurityGroupsResult describeResult = Mock(DescribeSecurityGroupsResult)

    when:
    agent.call()
    def securityGroupRules = cachedValue.inboundRules.findAll { it.class.isAssignableFrom(SecurityGroupRule)}
    def ipRangeRules = cachedValue.inboundRules.findAll { it.class.isAssignableFrom(IpRangeRule)}

    then:
    1 * ec2.describeSecurityGroups() >> describeResult
    2 * describeResult.getSecurityGroups() >> [mixedRangedGroupA]
    1 * cacheService.put(Keys.getSecurityGroupKey(mixedRangedGroupA.groupName, mixedRangedGroupA.groupId, region, account, null), _) >> { args -> cachedValue = args[1] }
    cachedValue.inboundRules.size() == 3
    securityGroupRules.size() == 1
    ipRangeRules.size() == 2
    securityGroupRules[0].protocol == 'tcp'
    ipRangeRules.protocol == ['tcp', 'tcp']
    ipRangeRules.range.ip == ['0.0.0.0', '0.0.0.1']
    ipRangeRules.range.cidr == ['/32', '/31']

  }

  void "should group ipRangeRules by addressable range"() {
    given:
    SecurityGroup group = new SecurityGroup(
        groupId: 'id-a',
        groupName: 'name-a',
        description: 'a',
        ipPermissions: [
            new IpPermission(
                ipProtocol: 'tcp',
                fromPort: 7001,
                toPort: 8080,
                ipRanges: ['0.0.0.0/32']
            ),
            new IpPermission(
                ipProtocol: 'tcp',
                fromPort: 7000,
                toPort: 8000,
                ipRanges: ['0.0.0.0/32', '0.0.0.1/31']
            )
        ])

    AmazonSecurityGroup cachedValue

    DescribeSecurityGroupsResult describeResult = Mock(DescribeSecurityGroupsResult)

    when:
    agent.call()

    then:
    1 * ec2.describeSecurityGroups() >> describeResult
    2 * describeResult.getSecurityGroups() >> [group]
    1 * cacheService.put(Keys.getSecurityGroupKey(group.groupName, group.groupId, region, account, null), _) >> { args -> cachedValue = args[1] }
    cachedValue.inboundRules.size() == 2
    cachedValue.inboundRules.protocol == ['tcp', 'tcp']
    cachedValue.inboundRules.range.ip == ['0.0.0.0', '0.0.0.1']
    cachedValue.inboundRules.range.cidr == ['/32', '/31']
    cachedValue.inboundRules[0].portRanges.startPort == [7000, 7001]
    cachedValue.inboundRules[0].portRanges.endPort == [8000, 8080]
    cachedValue.inboundRules[1].portRanges.startPort == [7000]
    cachedValue.inboundRules[1].portRanges.endPort == [8000]
  }

  @Shared
  SecurityGroup securityGroupA = new SecurityGroup(groupId: 'id-a', groupName: 'name-a', description: 'a')

  @Shared
  String keyGroupA = Keys.getSecurityGroupKey(securityGroupA.groupName, securityGroupA.groupId, region, account, null)

  @Shared
  SecurityGroup securityGroupB = new SecurityGroup(groupId: 'id-b', groupName: 'name-b', description: 'b')

  @Shared
  String keyGroupB = Keys.getSecurityGroupKey(securityGroupB.groupName, securityGroupB.groupId, region, account, null)


}
