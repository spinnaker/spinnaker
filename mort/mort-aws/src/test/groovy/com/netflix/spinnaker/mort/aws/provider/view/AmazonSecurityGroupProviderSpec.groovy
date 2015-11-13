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
import com.amazonaws.services.ec2.model.UserIdGroupPair
import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.cats.cache.CacheData
import com.netflix.spinnaker.cats.cache.DefaultCacheData
import com.netflix.spinnaker.cats.cache.WriteableCache
import com.netflix.spinnaker.cats.mem.InMemoryCache
import com.netflix.spinnaker.clouddriver.aws.AmazonCloudProvider
import com.netflix.spinnaker.mort.aws.cache.Keys
import com.netflix.spinnaker.mort.aws.model.AmazonSecurityGroup
import com.netflix.spinnaker.mort.aws.provider.AwsInfrastructureProvider
import com.netflix.spinnaker.mort.model.securitygroups.IpRangeRule
import com.netflix.spinnaker.mort.model.securitygroups.Rule
import com.netflix.spinnaker.mort.model.securitygroups.SecurityGroupRule
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Subject

class AmazonSecurityGroupProviderSpec extends Specification {

  @Subject
  AmazonSecurityGroupProvider provider

  AmazonCloudProvider amazonCloudProvider = new AmazonCloudProvider()
  WriteableCache cache = new InMemoryCache()
  ObjectMapper mapper = new ObjectMapper()

  def setup() {
    provider = new AmazonSecurityGroupProvider(amazonCloudProvider, cache, mapper)
    cache.mergeAll(Keys.Namespace.SECURITY_GROUPS.ns, getAllGroups())
  }

  void "getAll lists all"() {
    when:
    def result = provider.getAll(false)

    then:
    result.size() == 8
  }

  void "getAllByRegion lists only those in supplied region"() {
    when:
    def result = provider.getAllByRegion(false, region)

    then:
    result.size() == 4
    result.each {
      it.region == region
    }

    where:
    region = 'us-west-1'
  }

  void "getAllByAccount lists only those in supplied account"() {
    when:
    def result = provider.getAllByAccount(false, account)

    then:
    result.size() == 4
    result.each {
      it.accountName == account
    }

    where:
    account = 'prod'
  }

  void "getAllByAccountAndRegion lists only those in supplied account and region"() {
    when:
    def result = provider.getAllByAccountAndRegion(false, account, region)

    then:
    result.size() == 2
    result.each {
      it.accountName == account
      it.region == region
    }

    where:
    account = 'prod'
    region = 'us-west-1'
  }

  void "getAllByAccountAndName lists only those in supplied account with supplied name"() {
    when:
    def result = provider.getAllByAccountAndName(false, account, name)

    then:
    result.size() == 2
    result.each {
      it.accountName == account
      it.name == name
    }

    where:
    account = 'prod'
    name = 'a'
  }

  void "get returns match based on account, region, and name"() {

    when:
    def result = provider.get(account, region, name, null)

    then:
    result != null
    result.accountName == account
    result.region == region
    result.name == name

    where:
    account = 'prod'
    region = 'us-east-1'
    name = 'a'

  }

  void "should add both ipRangeRules and securityGroup rules"() {
    given:
    String groupId = 'id-a'
    String groupName = 'name-a'
    String vpcId = null
    SecurityGroup mixedRangedGroupA = new SecurityGroup(
      groupId: groupId,
      groupName: groupName,
      vpcId: vpcId,
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

    String account = 'test'
    String region = 'us-east-1'
    def key = Keys.getSecurityGroupKey(amazonCloudProvider, groupName, groupId, region, account, vpcId)
    Map<String, Object> attributes = mapper.convertValue(mixedRangedGroupA, AwsInfrastructureProvider.ATTRIBUTES)
    def cacheData = new DefaultCacheData(key, attributes, [:])
    cache.merge(Keys.Namespace.SECURITY_GROUPS.ns, cacheData)

    when:
    def cachedValue = provider.get(account, region, groupName, vpcId)
    def securityGroupRules = cachedValue.inboundRules.findAll { it.class.isAssignableFrom(SecurityGroupRule)}
    def ipRangeRules = cachedValue.inboundRules.findAll { it.class.isAssignableFrom(IpRangeRule)}

    then:
    cachedValue.inboundRules.size() == 3
    securityGroupRules.size() == 1
    ipRangeRules.size() == 2
    securityGroupRules[0].protocol == 'tcp'
    ipRangeRules.protocol == ['tcp', 'tcp']
    ipRangeRules.range.ip == ['0.0.0.0', '0.0.0.1']
    ipRangeRules.range.cidr == ['/32', '/31']

  }

  void "should add security group ingress with different protocols"() {
    given:
    SecurityGroup securityGroupA = new SecurityGroup(groupId: 'id-a', groupName: 'name-a', description: 'a')
    SecurityGroup securityGroupB = new SecurityGroup(groupId: 'id-b', groupName: 'name-b', description: 'b')
    securityGroupB.ipPermissions = [
      new IpPermission(ipProtocol: "TCP", fromPort: 7001, toPort: 7001, userIdGroupPairs: [
        new UserIdGroupPair(groupId: securityGroupA.groupId, groupName: securityGroupA.groupName)
      ]),
      new IpPermission(ipProtocol: "UDP", fromPort: 7001, toPort: 7001, userIdGroupPairs: [
        new UserIdGroupPair(groupId: securityGroupA.groupId, groupName: securityGroupA.groupName)
      ])
    ]
    String account = 'test'
    String region = 'us-east-1'
    def key = Keys.getSecurityGroupKey(amazonCloudProvider, 'name-b', 'id-b', region, account, null)
    Map<String, Object> attributes = mapper.convertValue(securityGroupB, AwsInfrastructureProvider.ATTRIBUTES)
    def cacheData = new DefaultCacheData(key, attributes, [:])
    cache.merge(Keys.Namespace.SECURITY_GROUPS.ns, cacheData)

    when:
    def sg = provider.get(account, region, 'name-b', null)

    then:
    sg == new AmazonSecurityGroup(type: "aws", id: "id-b", name: "name-b", description: "b",
      accountName: account, region: region, inboundRules: [
      new SecurityGroupRule(protocol: "TCP",
        securityGroup: new AmazonSecurityGroup(
          type: 'aws',
          id: 'id-a',
          name: 'name-a',
          accountName: account,
          region: region
        ),
        portRanges: [
          new Rule.PortRange(startPort: 7001, endPort: 7001)
        ] as SortedSet
      ),
      new SecurityGroupRule(protocol: "UDP",
        securityGroup: new AmazonSecurityGroup(
          type: 'aws',
          id: 'id-a',
          name: 'name-a',
          accountName: account,
          region: region
        ),
        portRanges: [
          new Rule.PortRange(startPort: 7001, endPort: 7001)
        ] as SortedSet
      )
    ])
    0 * _
  }

  void "should group ipRangeRules by addressable range"() {
    given:
    String account = 'test'
    String region = 'us-east-1'
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
    def key = Keys.getSecurityGroupKey(amazonCloudProvider, 'name-a', 'id-a', region, account, null)
    Map<String, Object> attributes = mapper.convertValue(group, AwsInfrastructureProvider.ATTRIBUTES)
    def cacheData = new DefaultCacheData(key, attributes, [:])
    cache.merge(Keys.Namespace.SECURITY_GROUPS.ns, cacheData)

    when:
    def cachedValue = provider.get(account, region, 'name-a', null)

    then:
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
  Map<String, Map<String, List<SecurityGroup>>> securityGroupMap = [
    prod: [
      'us-east-1': [
        new SecurityGroup(groupId: 'a', groupName: 'a'),
        new SecurityGroup(groupId: 'b', groupName: 'b'),
      ],
      'us-west-1': [
        new SecurityGroup(groupId: 'a', groupName: 'a'),
        new SecurityGroup(groupId: 'b', groupName: 'b'),
      ]
    ],
    test: [
      'us-east-1': [
        new SecurityGroup(groupId: 'a', groupName: 'a'),
        new SecurityGroup(groupId: 'b', groupName: 'b'),
      ],
      'us-west-1': [
        new SecurityGroup(groupId: 'a', groupName: 'a'),
        new SecurityGroup(groupId: 'b', groupName: 'b'),
      ]
    ]
  ]

  private List<CacheData> getAllGroups() {
    securityGroupMap.collect { String account, Map<String, List<SecurityGroup>> regions ->
      regions.collect { String region, List<SecurityGroup> groups ->
        groups.collect { SecurityGroup group ->
          Map<String, Object> attributes = mapper.convertValue(group, AwsInfrastructureProvider.ATTRIBUTES)
          new DefaultCacheData(Keys.getSecurityGroupKey(amazonCloudProvider, group.groupName, group.groupId, region, account, group.vpcId), attributes, [:])
        }
      }.flatten()
    }.flatten()
  }
}
