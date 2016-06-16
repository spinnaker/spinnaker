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

package com.netflix.spinnaker.clouddriver.openstack.deploy.ops.securitygroup

import com.netflix.spinnaker.clouddriver.data.task.Task
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository
import com.netflix.spinnaker.clouddriver.openstack.client.OpenstackClientProvider
import com.netflix.spinnaker.clouddriver.openstack.client.OpenstackProviderFactory
import com.netflix.spinnaker.clouddriver.openstack.deploy.description.securitygroup.UpsertOpenstackSecurityGroupDescription
import com.netflix.spinnaker.clouddriver.openstack.deploy.exception.OpenstackOperationException
import com.netflix.spinnaker.clouddriver.openstack.security.OpenstackCredentials
import com.netflix.spinnaker.clouddriver.openstack.security.OpenstackNamedAccountCredentials
import org.openstack4j.model.compute.IPProtocol
import org.openstack4j.model.compute.SecGroupExtension
import org.openstack4j.openstack.compute.domain.NovaSecGroupExtension
import spock.lang.Specification

class UpsertOpenstackSecurityGroupAtomicOperationSpec extends Specification {

  private static final String ACCOUNT_NAME = 'account'
  private static final String REGION = 'west'
  OpenstackCredentials credentials
  OpenstackClientProvider provider


  def setupSpec() {
    TaskRepository.threadLocalTask.set(Mock(Task))
  }
  def setup() {
    provider = Mock(OpenstackClientProvider)
    GroovyMock(OpenstackProviderFactory, global: true)
    OpenstackNamedAccountCredentials namedAccountCredentials = Mock(OpenstackNamedAccountCredentials)
    OpenstackProviderFactory.createProvider(namedAccountCredentials) >> { provider }
    credentials = new OpenstackCredentials(namedAccountCredentials)
  }

  def "create security group without rules"() {
    setup:
    def id = UUID.randomUUID().toString()
    def name = 'name'
    def desc = 'description'
    SecGroupExtension securityGroup = new NovaSecGroupExtension(id: id, name: name, description: desc)
    def description = new UpsertOpenstackSecurityGroupDescription(account: ACCOUNT_NAME, region: REGION, credentials: credentials, name: name, description: desc, rules: [])
    def operation = new UpsertOpenstackSecurityGroupAtomicOperation(description)

    when:
    operation.operate([])

    then:
    1 * provider.createSecurityGroup(REGION, name, desc) >> securityGroup
    0 * provider.getSecurityGroup(_, _)
    0 * provider.updateSecurityGroup(_, _, _, _)
    0 * provider.deleteSecurityGroupRule(_, _)
    0 * provider.createSecurityGroupRule(_, _, _, _, _, _)
    noExceptionThrown()
  }

  def "create security group with rules"() {
    setup:
    def id = UUID.randomUUID().toString()
    def name = 'sec-group-1'
    def desc = 'A description'
    SecGroupExtension securityGroup = new NovaSecGroupExtension(id: id, name: name, description: desc)
    def rules = [
      new UpsertOpenstackSecurityGroupDescription.Rule(fromPort: 80, toPort: 80, cidr: '0.0.0.0/0'),
      new UpsertOpenstackSecurityGroupDescription.Rule(fromPort: 443, toPort: 443, cidr: '0.0.0.0/0')
    ]

    def description = new UpsertOpenstackSecurityGroupDescription(account: ACCOUNT_NAME, region: REGION, credentials: credentials, name: name, description: desc, rules: rules)
    def operation = new UpsertOpenstackSecurityGroupAtomicOperation(description)

    when:
    operation.operate([])

    then:
    1 * provider.createSecurityGroup(REGION, name, desc) >> securityGroup
    0 * provider.getSecurityGroup(_, _)
    0 * provider.updateSecurityGroup(_, _, _, _)
    0 * provider.deleteSecurityGroupRule(_, _)
    rules.each { rule ->
      1 * provider.createSecurityGroupRule(REGION, id, IPProtocol.TCP, rule.cidr, rule.fromPort, rule.toPort)
    }
    noExceptionThrown()
  }

  def "update security group"() {
    setup:
    def id = UUID.randomUUID().toString()
    def name = 'sec-group-2'
    def desc= 'A description 2'

    def existingRules = [
      new NovaSecGroupExtension.SecurityGroupRule(id: '1', fromPort: 80, toPort: 8080, cidr: '192.1.68.1/24'),
      new NovaSecGroupExtension.SecurityGroupRule(id: '2', fromPort: 443, toPort: 443, cidr: '0.0.0.0/0')
    ]
    def existingSecurityGroup = new NovaSecGroupExtension(id: id, name: name, description: desc, rules: existingRules)

    def newRules = [
      new UpsertOpenstackSecurityGroupDescription.Rule(fromPort: 80, toPort: 80, cidr: '0.0.0.0/0'),
      new UpsertOpenstackSecurityGroupDescription.Rule(fromPort: 443, toPort: 443, cidr: '0.0.0.0/0')
    ]

    def description = new UpsertOpenstackSecurityGroupDescription(account: ACCOUNT_NAME, region: REGION, id: id, credentials: credentials, name: name, description: desc, rules: newRules)
    def operation = new UpsertOpenstackSecurityGroupAtomicOperation(description)

    when:
    operation.operate([])

    then:
    1 * provider.getSecurityGroup(REGION, id) >> existingSecurityGroup
    1 * provider.updateSecurityGroup(REGION, id, name, desc) >> existingSecurityGroup
    existingRules.each { rule ->
      1 * provider.deleteSecurityGroupRule(REGION, rule.id)
    }
    newRules.each { rule ->
      1 * provider.createSecurityGroupRule(REGION, id, IPProtocol.TCP, rule.cidr, rule.fromPort, rule.toPort)
    }
    0 * provider.createSecurityGroup(_, _, _)
    noExceptionThrown()
  }

  def "upsert security group handles exceptions"() {
    setup:
    def name = 'name'
    def desc = 'desc'
    def description = new UpsertOpenstackSecurityGroupDescription(account: ACCOUNT_NAME, region: REGION, credentials: credentials, name: name, description: desc, rules: [])
    def operation = new UpsertOpenstackSecurityGroupAtomicOperation(description)

    when:
    operation.operate([])

    then:
    1 * provider.createSecurityGroup(REGION, name, desc) >> { throw new OpenstackOperationException('foo') }
    thrown(OpenstackOperationException)
  }
}
