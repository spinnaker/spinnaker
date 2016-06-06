/*
 * Copyright 2016 Target, Inc.
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

package com.netflix.spinnaker.clouddriver.openstack.client

import com.netflix.spinnaker.clouddriver.openstack.deploy.description.securitygroup.OpenstackSecurityGroupDescription
import com.netflix.spinnaker.clouddriver.openstack.deploy.exception.OpenstackOperationException
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperations
import org.openstack4j.api.OSClient
import org.openstack4j.api.compute.ComputeSecurityGroupService
import org.openstack4j.api.compute.ComputeService
import org.openstack4j.api.heat.HeatService
import org.openstack4j.api.heat.StackService
import org.openstack4j.model.common.ActionResponse
import org.openstack4j.model.compute.IPProtocol
import org.openstack4j.model.compute.SecGroupExtension
import org.openstack4j.openstack.compute.domain.NovaSecGroupExtension
import spock.lang.Specification

class OpenstackClientProviderSpec extends Specification {

  private static final String OPERATION = "TestOperation"
  private OpenstackClientProvider provider
  private OSClient mockClient

  def setup() {
    mockClient = Mock(OSClient)

    // Subclass the provider so we get the method defined in the abstract class without dealing with a real client.
    provider = new OpenstackClientProvider() {
      @Override
      OSClient getClient() {
        mockClient
      }

      @Override
      String getTokenId() {
        null
      }
    }
  }

  def "create security group without rules"() {
    setup:
    def name = "sec-group-1"
    def description = "A description"
    ComputeService compute = Mock()
    ComputeSecurityGroupService securityGroupApi = Mock()
    SecGroupExtension securityGroup = Mock()

    when:
    provider.upsertSecurityGroup(null, name, description, [])

    then:
    1 * mockClient.compute() >> compute
    1 * compute.securityGroups() >> securityGroupApi
    1 * securityGroupApi.create(name, description) >> securityGroup
    0 * securityGroupApi.createRule(_)
    0 * securityGroupApi.deleteRule(_)
    noExceptionThrown()
  }

  def "create security group with rules"() {
    setup:
    ComputeService compute = Mock()
    ComputeSecurityGroupService securityGroupService = Mock()
    mockClient.compute() >> compute
    compute.securityGroups() >> securityGroupService

    def name = "sec-group-1"
    def description = "A description"
    SecGroupExtension securityGroup = new NovaSecGroupExtension()
    def rules = [
      new OpenstackSecurityGroupDescription.Rule(fromPort: 80, toPort: 80, cidr: "0.0.0.0/0"),
      new OpenstackSecurityGroupDescription.Rule(fromPort: 443, toPort: 443, cidr: "0.0.0.0/0")
    ]

    when:
    provider.upsertSecurityGroup(null, name, description, rules)

    then:
    1 * securityGroupService.create(name, description) >> securityGroup
    0 * securityGroupService.deleteRule(_)
    rules.each { rule ->
      1 * securityGroupService.createRule({ SecGroupExtension.Rule r ->
        r.toPort == rule.toPort &&  r.fromPort == rule.fromPort && r.IPProtocol == IPProtocol.TCP
      })
    }
    noExceptionThrown()
  }

  def "update security group"() {
    setup:
    ComputeService compute = Mock()
    ComputeSecurityGroupService securityGroupService = Mock()
    mockClient.compute() >> compute
    compute.securityGroups() >> securityGroupService

    def id = UUID.randomUUID().toString()
    def name = "sec-group-2"
    def description = "A description 2"

    def existingRules = [
      new NovaSecGroupExtension.SecurityGroupRule(id: '1', fromPort: 80, toPort: 8080, cidr: "192.1.68.1/24"),
      new NovaSecGroupExtension.SecurityGroupRule(id: '2', fromPort: 443, toPort: 443, cidr: "0.0.0.0/0")
    ]
    def existingSecurityGroup = new NovaSecGroupExtension(id: id, name: "name", description: "desc", rules: existingRules)

    def newRules = [
      new OpenstackSecurityGroupDescription.Rule(fromPort: 80, toPort: 80, cidr: "0.0.0.0/0"),
      new OpenstackSecurityGroupDescription.Rule(fromPort: 443, toPort: 443, cidr: "0.0.0.0/0")
    ]

    when:
    provider.upsertSecurityGroup(id, name, description, newRules)

    then:
    1 * securityGroupService.get(id) >> existingSecurityGroup
    1 * securityGroupService.update(id, name, description) >> existingSecurityGroup
    existingRules.each { rule ->
      1 * securityGroupService.deleteRule(rule.id)
    }
    newRules.each { rule ->
      1 * securityGroupService.createRule({ SecGroupExtension.Rule r ->
        r.toPort == rule.toPort &&  r.fromPort == rule.fromPort && r.IPProtocol == IPProtocol.TCP
      })
    }
    noExceptionThrown()
  }

  def "upsert security group handles exceptions"() {
    setup:
    ComputeService compute = Mock()
    ComputeSecurityGroupService securityGroupService = Mock()
    mockClient.compute() >> compute
    compute.securityGroups() >> securityGroupService

    def name = "name"
    def description = "desc"

    when:
    provider.upsertSecurityGroup(null, name, description, [])

    then:
    1 * securityGroupService.create(name, description) >> { throw new RuntimeException("foo") }
    OpenstackOperationException ex = thrown(OpenstackOperationException)
    ex.message.contains("foo")
    ex.message.contains(AtomicOperations.UPSERT_SECURITY_GROUP)
  }

  def "handle request succeeds"() {
    setup:
    def success = ActionResponse.actionSuccess()

    when:
    def response = provider.handleRequest(OPERATION) { success }

    then:
    success == response
    noExceptionThrown()
  }

  def "handle request fails with failed action request"() {
    setup:
    def failed = ActionResponse.actionFailed("foo", 500)

    when:
    provider.handleRequest(OPERATION) { failed }

    then:
    OpenstackOperationException ex = thrown(OpenstackOperationException)
    ex.message.contains("foo")
    ex.message.contains("500")
    ex.message.contains(OPERATION)
  }

  def "handle request fails with closure throwing exception"() {
    setup:
    def exception = new Exception("foo")

    when:
    provider.handleRequest(OPERATION) { throw exception }

    then:
    OpenstackOperationException ex = thrown(OpenstackOperationException)
    ex.cause == exception
    ex.message.contains("foo")
    ex.message.contains(OPERATION)
  }

  def "handle request non-action response"() {
    setup:
    def object = new Object()

    when:
    def response = provider.handleRequest(OPERATION) { object }

    then:
    object == response
    noExceptionThrown()
  }

  def "handle request null response"() {
    when:
    def response = provider.handleRequest(OPERATION) { null }

    then:
    response == null
    noExceptionThrown()
  }

  def "deploy heat stack succeeds"() {

    setup:
    HeatService heat = Mock()
    StackService stackApi = Mock()
    mockClient.heat() >> heat
    heat.stacks() >> stackApi

    when:
    provider.deploy("mystack", "{}", [:], false, 1)

    then:
    1 * stackApi.create("mystack", "{}", [:], false, 1)
    noExceptionThrown()
  }
}
