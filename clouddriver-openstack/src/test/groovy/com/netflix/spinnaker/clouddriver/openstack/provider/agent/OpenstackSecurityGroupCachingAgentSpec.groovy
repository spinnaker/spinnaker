/*
 * Copyright 2016 Target, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package com.netflix.spinnaker.clouddriver.openstack.provider.agent

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spectator.api.Registry
import com.netflix.spectator.api.Timer
import com.netflix.spinnaker.cats.agent.CacheResult
import com.netflix.spinnaker.cats.agent.DefaultCacheResult
import com.netflix.spinnaker.cats.cache.CacheData
import com.netflix.spinnaker.cats.cache.DefaultCacheData
import com.netflix.spinnaker.cats.provider.ProviderCache
import com.netflix.spinnaker.clouddriver.cache.OnDemandAgent
import com.netflix.spinnaker.clouddriver.model.AddressableRange
import com.netflix.spinnaker.clouddriver.model.securitygroups.IpRangeRule
import com.netflix.spinnaker.clouddriver.model.securitygroups.Rule
import com.netflix.spinnaker.clouddriver.model.securitygroups.SecurityGroupRule
import com.netflix.spinnaker.clouddriver.openstack.OpenstackCloudProvider
import com.netflix.spinnaker.clouddriver.openstack.cache.Keys
import com.netflix.spinnaker.clouddriver.openstack.client.OpenstackClientProvider
import com.netflix.spinnaker.clouddriver.openstack.deploy.exception.OpenstackProviderException
import com.netflix.spinnaker.clouddriver.openstack.model.OpenstackSecurityGroup
import com.netflix.spinnaker.clouddriver.openstack.provider.OpenstackInfrastructureProvider
import com.netflix.spinnaker.clouddriver.openstack.security.OpenstackCredentials
import com.netflix.spinnaker.clouddriver.openstack.security.OpenstackNamedAccountCredentials
import org.openstack4j.model.compute.IPProtocol
import org.openstack4j.openstack.compute.domain.NovaSecGroupExtension
import redis.clients.jedis.exceptions.JedisException
import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Unroll

import static com.netflix.spinnaker.clouddriver.openstack.cache.Keys.Namespace.ON_DEMAND
import static com.netflix.spinnaker.clouddriver.openstack.cache.Keys.Namespace.SECURITY_GROUPS

class OpenstackSecurityGroupCachingAgentSpec extends Specification {

  @Subject
  OpenstackSecurityGroupCachingAgent cachingAgent
  OpenstackNamedAccountCredentials namedAccountCredentials
  OpenstackCredentials credentials
  OpenstackClientProvider provider
  ObjectMapper objectMapper
  ProviderCache providerCache
  String region = 'east'
  String accountName = 'os-account'

  def setup() {
    providerCache = Mock(ProviderCache)
    provider = Mock(OpenstackClientProvider)
    credentials = GroovyMock(OpenstackCredentials)
    credentials.provider >> provider
    namedAccountCredentials = Mock(OpenstackNamedAccountCredentials)
    namedAccountCredentials.credentials >> credentials
    objectMapper = Mock(ObjectMapper)
    Registry registry = Mock(Registry) {
      _ * timer(*_) >> Mock(Timer)
    }
    cachingAgent = Spy(OpenstackSecurityGroupCachingAgent, constructorArgs: [namedAccountCredentials, region, objectMapper, registry])
    _ * cachingAgent.getAccountName() >> accountName
  }

  def "should load data without inbound rules"() {
    given:
    def id = UUID.randomUUID().toString()
    def name = 'a-security-group'
    def desc = 'a description'
    def key = Keys.getSecurityGroupKey(name, id, accountName, region)
    def instanceAttributes = new HashMap<>()
    def novaSecurityGroup = new NovaSecGroupExtension(name: name, description: desc, id: id)
    def securityGroup = new OpenstackSecurityGroup(id: id,
      accountName: accountName,
      region: region,
      name: name,
      description: desc,
      inboundRules: []
    )

    when:
    CacheResult cacheResult = cachingAgent.loadData(providerCache)

    then:
    1 * provider.getSecurityGroups(region) >> [novaSecurityGroup]
    1 * objectMapper.convertValue(securityGroup, OpenstackInfrastructureProvider.ATTRIBUTES) >> instanceAttributes

    and:
    def cacheData = cacheResult.cacheResults.get(SECURITY_GROUPS.ns).first()
    cacheData.id == key
    cacheData.attributes == instanceAttributes
  }

  def "should load data with cidr inbound rules"() {
    given:
    def id = UUID.randomUUID().toString()
    def name = 'a-security-group'
    def desc = 'a description'
    def key = Keys.getSecurityGroupKey(name, id, accountName, region)
    def instanceAttributes = new HashMap<>()

    def novaSecurityGroup = new NovaSecGroupExtension(name: name, description: desc, id: id, rules: [
      new NovaSecGroupExtension.SecurityGroupRule(fromPort: 80, toPort: 80, ipProtocol: IPProtocol.TCP,
        ipRange: new NovaSecGroupExtension.SecurityGroupRule.RuleIpRange(cidr: '10.10.0.0/24')
      ),
      new NovaSecGroupExtension.SecurityGroupRule(fromPort: 22, toPort: 22, ipProtocol: IPProtocol.TCP,
        ipRange: new NovaSecGroupExtension.SecurityGroupRule.RuleIpRange(cidr: '10.10.0.0')
      )
    ])
    def securityGroup = new OpenstackSecurityGroup(id: id,
      accountName: accountName,
      region: region,
      name: name,
      description: desc,
      inboundRules: [
        new IpRangeRule(protocol: IPProtocol.TCP.value(),
          portRanges: [new Rule.PortRange(startPort: 80, endPort: 80)] as SortedSet,
          range: new AddressableRange(ip: '10.10.0.0', cidr: '/24')
        ),
        new IpRangeRule(protocol: IPProtocol.TCP.value(),
          portRanges: [new Rule.PortRange(startPort: 22, endPort: 22)] as SortedSet,
          range: new AddressableRange(ip: '10.10.0.0', cidr: '/32')
        )
      ]
    )

    when:
    CacheResult cacheResult = cachingAgent.loadData(providerCache)

    then:
    1 * provider.getSecurityGroups(region) >> [novaSecurityGroup]
    1 * objectMapper.convertValue(securityGroup, OpenstackInfrastructureProvider.ATTRIBUTES) >> instanceAttributes

    and:
    def cacheData = cacheResult.cacheResults.get(SECURITY_GROUPS.ns).first()
    cacheData.id == key
    cacheData.attributes == instanceAttributes
  }

  def "should load data with referencing security group inbound rule missing referenced security group"() {
    given:
    def securityGroupId = UUID.randomUUID().toString()
    def name = 'a-security-group'
    def desc = 'a description'
    def instanceAttributes = [:]

    def novaSecurityGroup = new NovaSecGroupExtension(name: name, description: desc, id: securityGroupId, rules: [
      new NovaSecGroupExtension.SecurityGroupRule(fromPort: 80, toPort: 80, ipProtocol: IPProtocol.TCP,
        ipRange: new NovaSecGroupExtension.SecurityGroupRule.RuleIpRange(cidr: null),
        group: new NovaSecGroupExtension.SecurityGroupRule.RuleGroup(name: 'ref', tenantId: 'tenant')
      )
    ])

    def securityGroup = new OpenstackSecurityGroup(id: securityGroupId,
      accountName: accountName,
      region: region,
      name: name,
      description: desc,
      inboundRules: [
        new SecurityGroupRule(protocol: IPProtocol.TCP.value(),
          portRanges: [new Rule.PortRange(startPort: 80, endPort: 80)] as SortedSet,
          securityGroup: new OpenstackSecurityGroup(name: 'ref', type: OpenstackCloudProvider.ID, accountName: accountName, region: region)
        )
      ]
    )

    when:
    CacheResult cacheResult = cachingAgent.loadData(providerCache)

    then:
    1 * provider.getSecurityGroups(region) >> [novaSecurityGroup]
    1 * objectMapper.convertValue(securityGroup, OpenstackInfrastructureProvider.ATTRIBUTES) >> instanceAttributes

    and:
    cacheResult.cacheResults.get(SECURITY_GROUPS.ns).size() == 1
    cacheResult.cacheResults.get(SECURITY_GROUPS.ns).find { it.id == Keys.getSecurityGroupKey(name, securityGroupId, accountName, region) }
  }

  def "should load data with referencing security group inbound rule with referenced security group"() {
    given:
    def securityGroupId = UUID.randomUUID().toString()
    def referencedSecurityGroupId = UUID.randomUUID().toString()
    def name = 'a-security-group'
    def desc = 'a description'
    def instanceAttributes = [:]

    def novaSecurityGroups = [
      new NovaSecGroupExtension(name: name, description: desc, id: securityGroupId, rules: [
        new NovaSecGroupExtension.SecurityGroupRule(fromPort: 80, toPort: 80, ipProtocol: IPProtocol.TCP,
          ipRange: new NovaSecGroupExtension.SecurityGroupRule.RuleIpRange(cidr: null),
          group: new NovaSecGroupExtension.SecurityGroupRule.RuleGroup(name: 'ref', tenantId: 'tenant')
        )
      ]),
      new NovaSecGroupExtension(name: 'ref', description: desc, id: referencedSecurityGroupId, rules: [])
    ]

    def securityGroup = new OpenstackSecurityGroup(id: securityGroupId,
      accountName: accountName,
      region: region,
      name: name,
      description: desc,
      inboundRules: [
        new SecurityGroupRule(protocol: IPProtocol.TCP.value(),
          portRanges: [new Rule.PortRange(startPort: 80, endPort: 80)] as SortedSet,
          securityGroup: new OpenstackSecurityGroup(name: 'ref', type: OpenstackCloudProvider.ID, accountName: accountName, region: region, id: referencedSecurityGroupId)
        )
      ]
    )

    when:
    CacheResult cacheResult = cachingAgent.loadData(providerCache)

    then:
    1 * provider.getSecurityGroups(region) >> novaSecurityGroups
    1 * objectMapper.convertValue(securityGroup, OpenstackInfrastructureProvider.ATTRIBUTES) >> instanceAttributes

    and:
    cacheResult.cacheResults.get(SECURITY_GROUPS.ns).size() == 2
    cacheResult.cacheResults.get(SECURITY_GROUPS.ns).find { it.id == Keys.getSecurityGroupKey(name, securityGroupId, accountName, region) }
    cacheResult.cacheResults.get(SECURITY_GROUPS.ns).find { it.id == Keys.getSecurityGroupKey('ref', referencedSecurityGroupId, accountName, region) }
  }

  def "should load data with referencing security group inbound rule duplicate referenced security group"() {
    given:
    def securityGroupId = UUID.randomUUID().toString()
    def referencedSecurityGroupId = UUID.randomUUID().toString()
    def thirdSecurityGroupid = UUID.randomUUID().toString()
    def name = 'a-security-group'
    def desc = 'a description'
    def instanceAttributes = [:]

    def novaSecurityGroups = [
      new NovaSecGroupExtension(name: name, description: desc, id: securityGroupId, rules: [
        new NovaSecGroupExtension.SecurityGroupRule(fromPort: 80, toPort: 80, ipProtocol: IPProtocol.TCP,
          ipRange: new NovaSecGroupExtension.SecurityGroupRule.RuleIpRange(cidr: null),
          group: new NovaSecGroupExtension.SecurityGroupRule.RuleGroup(name: 'ref', tenantId: 'tenant')
        )
      ]),
      new NovaSecGroupExtension(name: 'ref', description: desc, id: referencedSecurityGroupId, rules: []),
      new NovaSecGroupExtension(name: 'ref', description: desc, id: thirdSecurityGroupid, rules: [])
    ]

    def securityGroup = new OpenstackSecurityGroup(id: securityGroupId,
      accountName: accountName,
      region: region,
      name: name,
      description: desc,
      inboundRules: [
        new SecurityGroupRule(protocol: IPProtocol.TCP.value(),
          portRanges: [new Rule.PortRange(startPort: 80, endPort: 80)] as SortedSet,
          securityGroup: new OpenstackSecurityGroup(name: 'ref', type: OpenstackCloudProvider.ID, accountName: accountName, region: region)
        )
      ]
    )

    when:
    CacheResult cacheResult = cachingAgent.loadData(providerCache)

    then:
    1 * provider.getSecurityGroups(region) >> novaSecurityGroups
    1 * objectMapper.convertValue(securityGroup, OpenstackInfrastructureProvider.ATTRIBUTES) >> instanceAttributes

    and:
    cacheResult.cacheResults.get(SECURITY_GROUPS.ns).size() == 3
    cacheResult.cacheResults.get(SECURITY_GROUPS.ns).find { it.id == Keys.getSecurityGroupKey(name, securityGroupId, accountName, region) }
    cacheResult.cacheResults.get(SECURITY_GROUPS.ns).find { it.id == Keys.getSecurityGroupKey('ref', referencedSecurityGroupId, accountName, region) }
    cacheResult.cacheResults.get(SECURITY_GROUPS.ns).find { it.id == Keys.getSecurityGroupKey('ref', thirdSecurityGroupid, accountName, region) }
  }


  def "load data finds no security groups"() {
    when:
    CacheResult cacheResult = cachingAgent.loadData(providerCache)

    then:
    1 * provider.getSecurityGroups(region) >> []
    cacheResult.cacheResults.get(SECURITY_GROUPS.ns).empty
  }

  def "get security groups handles null"() {
    when:
    CacheResult cacheResult = cachingAgent.loadData(providerCache)

    then:
    1 * provider.getSecurityGroups(region) >> null
    cacheResult.cacheResults.get(SECURITY_GROUPS.ns).empty
  }

  def "load data lets exception bubble up"() {
    given:
    Throwable exception = new OpenstackProviderException()

    when:
    cachingAgent.loadData(providerCache)

    then:
    1 * provider.getSecurityGroups(region) >> { throw exception }
    def ex = thrown(Exception)
    exception == ex
  }

  @Unroll
  def "on demand caching with invalid data"() {
    when:
    def result = cachingAgent.handle(providerCache, data)

    then:
    result == null

    where:
    data << [
      [account: 'os-account', region: 'east'],
      [securityGroupName: 'sg', account: 'other-account', region: 'east'],
      [securityGroupName: 'sg', account: 'os-account', region: 'west']
    ]
  }

  def "handle on demand store"() {
    given:
    def id = UUID.randomUUID().toString()
    def secGroupExt = new NovaSecGroupExtension(name: 'sg', id: id)
    def data = [
      securityGroupName: secGroupExt.name,
      account: accountName,
      region: region
    ]
    CacheData cacheData = new DefaultCacheData(UUID.randomUUID().toString(), [:], [:])
    CacheResult cacheResult = new DefaultCacheResult([(SECURITY_GROUPS.ns): [cacheData]])

    when:
    def result = cachingAgent.handle(providerCache, data)

    then:
    1 * provider.getSecurityGroups(region) >> [secGroupExt]
    1 * cachingAgent.buildCacheResult(_, [secGroupExt]) >> cacheResult
    1 * providerCache.putCacheData(ON_DEMAND.ns, _)

    and:
    result.cacheResult.cacheResults[SECURITY_GROUPS.ns].size() == 1
    result.cacheResult.cacheResults[SECURITY_GROUPS.ns].first() == cacheData
  }

  def "handle on demand unable to find security group"() {
    given:
    String unresolvedKey = Keys.getSecurityGroupKey('sg', '*', accountName, region)
    String key = Keys.getSecurityGroupKey('sg', UUID.randomUUID().toString(), accountName, region)
    def data = [securityGroupName: 'sg', account: accountName, region: region]

    when:
    def result = cachingAgent.handle(providerCache, data)

    then:
    1 * provider.getSecurityGroups(region) >> []
    1 * providerCache.filterIdentifiers(SECURITY_GROUPS.ns, unresolvedKey) >> [key]

    and:
    result.cacheResult.cacheResults[ON_DEMAND.ns].isEmpty()
    result.evictions[SECURITY_GROUPS.ns] == [key]
  }

  def "handle on demand no cache results built"() {
    given:
    String id = UUID.randomUUID().toString()
    String name = 'sg'
    String key = Keys.getSecurityGroupKey(name, id, accountName, region)
    def data = [securityGroupName: name, account: accountName, region: region]
    def securityGroup = new NovaSecGroupExtension(name: name, id: id)

    when:
    def result = cachingAgent.handle(providerCache, data)

    then:
    1 * provider.getSecurityGroups(region) >> [securityGroup]
    1 * cachingAgent.buildCacheResult(_, [securityGroup]) >> { builder, groups -> builder.build() }
    1 * providerCache.evictDeletedItems(ON_DEMAND.ns, [key])

    and:
    result.cacheResult.cacheResults[ON_DEMAND.ns].isEmpty()
    result.evictions.isEmpty()
  }

  def "handles proper type - #testCase"() {
    when:
    def result = cachingAgent.handles(type, cloudProvider)

    then:
    result == expected

    where:
    testCase         | type                                     | cloudProvider             | expected
    'wrong type'     | OnDemandAgent.OnDemandType.LoadBalancer  | OpenstackCloudProvider.ID | false
    'wrong provider' | OnDemandAgent.OnDemandType.SecurityGroup | 'aws'                     | false
    'success'        | OnDemandAgent.OnDemandType.SecurityGroup | OpenstackCloudProvider.ID | true
  }

  def "pending on demand requests"() {
    given:
    def id = UUID.randomUUID().toString()
    def name = 'sg'
    def key = Keys.getSecurityGroupKey(name, id, accountName, region)
    def cacheData = new DefaultCacheData(key, [cacheTime: System.currentTimeMillis(), processedCount: 1, processedTime: System.currentTimeMillis()], [:])

    when:
    def result = cachingAgent.pendingOnDemandRequests(providerCache)

    then:
    1 * providerCache.getIdentifiers(ON_DEMAND.ns) >> [key]
    1 * providerCache.getAll(ON_DEMAND.ns, [key]) >> [cacheData]

    and:
    result.first() == [details: Keys.parse(key), cacheTime: cacheData.attributes.cacheTime, processedCount: cacheData.attributes.processedCount, processedTime: cacheData.attributes.processedTime]
  }

  def "pending on demand requests with exception"() {
    given:
    Throwable throwable = new JedisException('test')

    when:
    cachingAgent.pendingOnDemandRequests(providerCache)

    then:
    1 * providerCache.getIdentifiers(ON_DEMAND.ns) >> { throw throwable }

    and:
    def exception = thrown(JedisException)
    exception == throwable
  }
}
