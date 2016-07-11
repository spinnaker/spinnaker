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

package com.netflix.spinnaker.clouddriver.openstack.provider.view

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.cats.cache.DefaultCacheData
import com.netflix.spinnaker.cats.cache.WriteableCache
import com.netflix.spinnaker.cats.mem.InMemoryCache
import com.netflix.spinnaker.clouddriver.model.AddressableRange
import com.netflix.spinnaker.clouddriver.model.securitygroups.IpRangeRule
import com.netflix.spinnaker.clouddriver.model.securitygroups.Rule
import com.netflix.spinnaker.clouddriver.openstack.OpenstackCloudProvider
import com.netflix.spinnaker.clouddriver.openstack.cache.Keys
import com.netflix.spinnaker.clouddriver.openstack.model.OpenstackSecurityGroup
import com.netflix.spinnaker.clouddriver.openstack.provider.OpenstackInfrastructureProvider
import redis.clients.jedis.exceptions.JedisException
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Unroll

@Unroll
class OpenstackSecurityGroupProviderSpec extends Specification {

  @Subject
  OpenstackSecurityGroupProvider provider

  WriteableCache cache = new InMemoryCache()
  ObjectMapper mapper = new ObjectMapper()

  @Shared
  Set<OpenstackSecurityGroup> account1East = [1, 2].collect { createSecurityGroup('account1', 'east') }
  @Shared
  Set<OpenstackSecurityGroup> account1West = [1, 2].collect { createSecurityGroup('account1', 'west') }
  @Shared
  Set<OpenstackSecurityGroup> account2East = [1, 2].collect { createSecurityGroup('account2', 'east') }
  @Shared
  Set<OpenstackSecurityGroup> account2West = [1, 2].collect { createSecurityGroup('account2', 'west') }
  @Shared
  Set<OpenstackSecurityGroup> allSecurityGroups = account1East.plus(account1West).plus(account2East).plus(account2West)

  def setup() {
    provider = new OpenstackSecurityGroupProvider(cache, mapper)
    cache.mergeAll(Keys.Namespace.SECURITY_GROUPS.ns, getAllCacheData())
  }

  def "type is openstack"() {
    when:
    def type = provider.getType()

    then:
    type == OpenstackCloudProvider.ID
  }

  def "get all security groups"() {
    when:
    def securityGroups = provider.getAll(true)

    then:
    allSecurityGroups == securityGroups
  }

  def "get all security groups without rules"() {
    given:
    def securityGroupsWithoutRules = allSecurityGroups.collect { sg ->
        new OpenstackSecurityGroup(id: sg.id,
          accountName: sg.accountName,
          region: sg.region,
          name: sg.name,
          description: sg.description,
          inboundRules: []
        )
      } as Set

    when:
    def securityGroups = provider.getAll(false)

    then:
    securityGroups == securityGroupsWithoutRules
  }

  def "get all by region"() {
    when:
    def securityGroups = provider.getAllByRegion(true, region)

    then:
    expected == securityGroups

    where:
    region | expected
    'mid'  | [] as Set
    'west' | account1West.plus(account2West)
    'east' | account2East.plus(account1East)
  }

  def "get all by account"() {
    when:
    def securityGroups = provider.getAllByAccount(true, account)

    then:
    expected == securityGroups

    where:
    account    | expected
    'account3' | [] as Set
    'account1' | account1West.plus(account1East)
    'account2' | account2West.plus(account2East)
  }

  def "get all by account and name"() {
    when:
    def securityGroups = provider.getAllByAccountAndName(true, account, name)

    then:
    expected == securityGroups

    where:
    account    | name        | expected
    'account1' | 'invalid'   | [] as Set
    'invalid'  | 'name-west' | [] as Set
    'account1' | 'name-west' | account1West.findAll { it.name == 'name-west' }
    'account2' | 'name-west' | account2West.findAll { it.name == 'name-west' }
  }

  def "get all by account and region"() {
    when:
    def securityGroups = provider.getAllByAccountAndRegion(true, account, region)

    then:
    expected == securityGroups

    where:
    account    | region    | expected
    'invalid'  | 'west'    | [] as Set
    'account2' | 'invalid' | [] as Set
    'account1' | 'west'    | account1West
    'account2' | 'west'    | account2West
  }

  def "get security group"() {
    when:
    def securityGroup = provider.get(account, region, name, null)

    then:
    if (expected) {
      // Security groups are not guaranteed to be unique by account, region, and name
      // Just ensuring the found security group have those attributes correct
      expected.accountName == securityGroup.accountName
      expected.region == securityGroup.region
      expected.name == securityGroup.name
      expected.inboundRules == securityGroup.inboundRules
    } else {
      securityGroup == null
    }

    where:
    account    | region | name        | expected
    'account1' | 'west' | 'name-east' | null
    'account1' | 'west' | 'name-west' | account1West[0]
    'account2' | 'west' | 'name-west' | account2West[0]
  }

  def "get all with an empty cache"() {
    given:
    // Recreate the provider with an empty cache
    cache = new InMemoryCache()
    provider = new OpenstackSecurityGroupProvider(cache, mapper)

    when:
    def securityGroups = provider.getAll(false)

    then:
    securityGroups.empty
  }

  void "get all throws an exception"() {
    given:
    // Recreate the provider with a mock cache to enable throwing an exception
    cache = Mock(WriteableCache)
    provider = new OpenstackSecurityGroupProvider(cache, mapper)
    def filters = []
    def throwable = new JedisException('test')

    when:
    provider.getAll(false)

    then:
    1 * cache.filterIdentifiers(Keys.Namespace.SECURITY_GROUPS.ns, _) >> filters
    1 * cache.getAll(Keys.Namespace.SECURITY_GROUPS.ns, filters, _) >> { throw throwable }
    Throwable exception = thrown(JedisException)
    exception == throwable
  }


  def createSecurityGroup(String account, String region) {
    new OpenstackSecurityGroup(id: UUID.randomUUID().toString(),
      accountName: account,
      region: region,
      name: "name-$region",
      description: "Description",
      inboundRules: [
        new IpRangeRule(protocol: 'tcp',
          portRanges: [new Rule.PortRange(startPort: 3272, endPort: 3272)] as SortedSet,
          range: new AddressableRange(ip: '10.10.0.0', cidr: '/24')
        )
      ]
    )
  }

  def getAllCacheData() {
    allSecurityGroups.collect { sg ->
      def key = Keys.getSecurityGroupKey(sg.name, sg.id, sg.accountName, sg.region)
      Map<String, Object> attributes = mapper.convertValue(sg, OpenstackInfrastructureProvider.ATTRIBUTES)
      new DefaultCacheData(key, attributes, [:])
    }
  }
}
