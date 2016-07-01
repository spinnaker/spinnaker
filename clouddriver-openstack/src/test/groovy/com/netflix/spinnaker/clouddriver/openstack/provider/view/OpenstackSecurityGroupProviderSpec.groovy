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

import com.netflix.spinnaker.clouddriver.openstack.OpenstackCloudProvider
import com.netflix.spinnaker.clouddriver.openstack.client.OpenstackClientProvider
import com.netflix.spinnaker.clouddriver.openstack.client.OpenstackProviderFactory
import com.netflix.spinnaker.clouddriver.openstack.model.OpenstackSecurityGroup
import com.netflix.spinnaker.clouddriver.openstack.security.OpenstackCredentials
import com.netflix.spinnaker.clouddriver.openstack.security.OpenstackNamedAccountCredentials
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsProvider
import org.openstack4j.model.compute.IPProtocol
import org.openstack4j.model.compute.SecGroupExtension
import org.openstack4j.openstack.compute.domain.NovaSecGroupExtension
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Unroll

@Unroll
class OpenstackSecurityGroupProviderSpec extends Specification {

  @Subject
  OpenstackSecurityGroupProvider provider

  AccountCredentialsProvider accountCredentialsProvider

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
    accountCredentialsProvider = Mock()

    // TODO Shouldn't need the spy once the data is coming from the caching layer
    provider = Spy(OpenstackSecurityGroupProvider, constructorArgs: [accountCredentialsProvider])
  }

  def "type is openstack"() {
    when:
    def type = provider.getType()

    then:
    type == OpenstackCloudProvider.ID
  }

  def "get all security groups"() {
    // TODO Flesh out better once the caching layer is in place
    when:
    def securityGroups = provider.getAll(false)

    then:
    1 * provider.getSecurityGroups(_) >> allSecurityGroups
    allSecurityGroups == securityGroups
  }

  def "get all by region"() {
    when:
    def securityGroups = provider.getAllByRegion(false, region)

    then:
    1 * provider.getSecurityGroups(_) >> all
    expected == securityGroups

    where:
    region | all               | expected
    'west' | account1West      | account1West
    'east' | account1West      | [] as Set
    'east' | allSecurityGroups | account2East.plus(account1East)
  }

  def "get all by account"() {
    when:
    def securityGroups = provider.getAllByAccount(false, account)

    then:
    1 * provider.getSecurityGroups(_) >> all
    expected == securityGroups

    where:
    account    | all               | expected
    'account1' | account1West      | account1West
    'account1' | account2West      | [] as Set
    'account2' | allSecurityGroups | account2West.plus(account2East)
  }

  def "get all by account and name"() {
    when:
    def securityGroups = provider.getAllByAccountAndName(false, account, name)

    then:
    1 * provider.getSecurityGroups(_) >> all
    expected == securityGroups

    where:
    account    | name        | all               | expected
    'account1' | 'name-west' | account2West      | [] as Set
    'account2' | 'name-east' | account2West      | [] as Set
    'account1' | 'name-west' | account1West      | account1West.findAll { it.name == 'name-west' }
    'account2' | 'name-west' | allSecurityGroups | account2West.findAll { it.name == 'name-west' }
  }

  def "get all by account and region"() {
    when:
    def securityGroups = provider.getAllByAccountAndRegion(false, account, region)

    then:
    1 * provider.getSecurityGroups(_) >> all
    expected == securityGroups

    where:
    account    | region | all               | expected
    'account1' | 'west' | account2West      | [] as Set
    'account2' | 'east' | account2West      | [] as Set
    'account1' | 'west' | account1West      | account1West
    'account2' | 'west' | allSecurityGroups | account2West
  }

  def "get security group"() {
    when:
    def securityGroup = provider.get(account, region, name, null)

    then:
    1 * provider.getSecurityGroups(_) >> all
    expected == securityGroup

    where:
    account    | region | name        | all               | expected
    'account1' | 'west' | 'name-east' | account2West      | null
    'account2' | 'east' | 'name-west' | account2West      | null
    'account1' | 'west' | 'name-west' | account1West      | account1West[0]
    'account2' | 'west' | 'name-west' | allSecurityGroups | account2West[0]
  }

  def "get all with rules"() {
    given:
    String region = 'region'
    SecGroupExtension secGroupExtension = new NovaSecGroupExtension(id: UUID.randomUUID().toString(),
      name: 'sec-group',
      description: "description",
      rules: [
        createRule(1000, 1003, '10.10.0.0/32', IPProtocol.TCP),
        createRule(2000, 2003, '0.0.0.0/24', IPProtocol.TCP),
        createRule(80, 80, '192.168.1.1', IPProtocol.TCP)
      ]
    )
    OpenstackClientProvider clientProvider = Mock()
    GroovyMock(OpenstackProviderFactory, global: true)
    OpenstackNamedAccountCredentials namedAccountCredentials = Mock()
    OpenstackProviderFactory.createProvider(namedAccountCredentials) >> { clientProvider }
    OpenstackCredentials credentials = new OpenstackCredentials(namedAccountCredentials)

    when:
    Set<OpenstackSecurityGroup> securityGroups = provider.getAll(true)

    then:
    1 * accountCredentialsProvider.getAll() >> [namedAccountCredentials]
    1 * namedAccountCredentials.getCredentials() >> credentials
    1 * clientProvider.getProperty('allRegions') >> [region]
    1 * clientProvider.getSecurityGroups(region) >> [secGroupExtension]

    and:
    securityGroups.size() == 1
    securityGroups.first().with {
      name == 'sec-group'
      description == 'description'
      region == region
      inboundRules.size() == 3
    }
  }

  def createRule(int fromPort, int toPort, String cidr, IPProtocol protocol) {
    def ipRange = new NovaSecGroupExtension.SecurityGroupRule.RuleIpRange(cidr: cidr)
    new NovaSecGroupExtension.SecurityGroupRule(fromPort: fromPort, toPort: toPort, ipProtocol: protocol, ipRange: ipRange)
  }



  def createSecurityGroup(String account, String region) {
    new OpenstackSecurityGroup(id: UUID.randomUUID().toString(),
      accountName: account,
      region: region,
      name: "name-$region",
      description: "Description",
      inboundRules: [])
  }
}
