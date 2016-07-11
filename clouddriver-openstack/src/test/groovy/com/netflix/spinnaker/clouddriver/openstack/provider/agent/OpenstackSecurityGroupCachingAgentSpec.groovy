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
import com.netflix.spinnaker.cats.agent.CacheResult
import com.netflix.spinnaker.cats.provider.ProviderCache
import com.netflix.spinnaker.clouddriver.model.AddressableRange
import com.netflix.spinnaker.clouddriver.model.securitygroups.IpRangeRule
import com.netflix.spinnaker.clouddriver.model.securitygroups.Rule
import com.netflix.spinnaker.clouddriver.openstack.cache.Keys
import com.netflix.spinnaker.clouddriver.openstack.client.OpenstackClientProvider
import com.netflix.spinnaker.clouddriver.openstack.deploy.exception.OpenstackProviderException
import com.netflix.spinnaker.clouddriver.openstack.model.OpenstackSecurityGroup
import com.netflix.spinnaker.clouddriver.openstack.provider.OpenstackInfrastructureProvider
import com.netflix.spinnaker.clouddriver.openstack.security.OpenstackCredentials
import com.netflix.spinnaker.clouddriver.openstack.security.OpenstackNamedAccountCredentials
import org.openstack4j.model.compute.IPProtocol
import org.openstack4j.openstack.compute.domain.NovaSecGroupExtension
import spock.lang.Specification
import spock.lang.Subject

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
    cachingAgent = Spy(OpenstackSecurityGroupCachingAgent, constructorArgs: [namedAccountCredentials, region, objectMapper])
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

  def "should load data with inbound rules"() {
    given:
    def id = UUID.randomUUID().toString()
    def name = 'a-security-group'
    def desc = 'a description'
    def key = Keys.getSecurityGroupKey(name, id, accountName, region)
    def instanceAttributes = new HashMap<>()

    def novaSecurityGroup = new NovaSecGroupExtension(name: name, description: desc, id: id, rules: [
      new NovaSecGroupExtension.SecurityGroupRule(fromPort: 80,
        toPort: 80,
        ipProtocol: IPProtocol.TCP,
        ipRange: new NovaSecGroupExtension.SecurityGroupRule.RuleIpRange(cidr: '10.10.0.0/24')
      ),
      new NovaSecGroupExtension.SecurityGroupRule(fromPort: 22,
        toPort: 22,
        ipProtocol: IPProtocol.TCP,
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
    CacheResult cacheResult = cachingAgent.loadData(providerCache)

    then:
    1 * provider.getSecurityGroups(region) >> { throw exception }
    def ex = thrown(Exception)
    exception == ex
  }
}
