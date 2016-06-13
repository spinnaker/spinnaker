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
import com.netflix.spinnaker.clouddriver.openstack.deploy.exception.OpenstackProviderException
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperations
import org.openstack4j.api.OSClient
import org.openstack4j.api.compute.ComputeSecurityGroupService
import org.openstack4j.api.compute.ComputeService
import org.openstack4j.api.compute.ServerService
import org.openstack4j.api.heat.HeatService
import org.openstack4j.api.heat.StackService
import org.openstack4j.api.networking.NetworkingService
import org.openstack4j.api.networking.ext.HealthMonitorService
import org.openstack4j.api.networking.ext.LbPoolService
import org.openstack4j.api.networking.ext.LoadBalancerService
import org.openstack4j.api.networking.ext.MemberService
import org.openstack4j.api.networking.ext.VipService
import org.openstack4j.api.networking.ext.MemberService
import org.openstack4j.model.common.ActionResponse
import org.openstack4j.model.compute.Address
import org.openstack4j.model.compute.Addresses
import org.openstack4j.model.compute.IPProtocol
import org.openstack4j.model.compute.SecGroupExtension
import org.openstack4j.model.compute.Server
import org.openstack4j.model.network.ext.LbPool
import org.openstack4j.model.network.ext.Member
import org.openstack4j.openstack.compute.domain.NovaSecGroupExtension
import org.springframework.http.HttpStatus
import spock.lang.Specification
import spock.lang.Unroll

@Unroll
class OpenstackClientProviderSpec extends Specification {

  private static final String OPERATION = "TestOperation"
  private OpenstackClientProvider provider
  private OSClient mockClient
  private String region = 'region1'

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

      OSClient getRegionClient(String region) {
        mockClient
      }
    }
    mockClient.useRegion(region) >> mockClient
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
        r.toPort == rule.toPort && r.fromPort == rule.fromPort && r.IPProtocol == IPProtocol.TCP
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
        r.toPort == rule.toPort && r.fromPort == rule.fromPort && r.IPProtocol == IPProtocol.TCP
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

  def "test get internal load balancer port succeeds"() {
    setup:
    LbPool pool = Mock(LbPool)
    pool.description >> 'internal_port=1234'

    when:
    int port = provider.getInternalLoadBalancerPort(pool)

    then:
    port == 1234
  }

  def "test get internal load balancer port throws exception"() {
    setup:
    LbPool pool = Mock(LbPool)
    pool.description >> "internal_port=$port"

    when:
    provider.getInternalLoadBalancerPort(pool)

    then:
    Exception e = thrown(OpenstackProviderException)
    e.message == "Internal pool port $port is outside of the valid range.".toString()

    where:
    port << [0,65536]
  }

  def "test get ip address for instance succeeds"() {
    setup:
    String id = UUID.randomUUID().toString()
    ComputeService computeService = Mock(ComputeService)
    mockClient.compute() >> computeService
    ServerService serverService = Mock(ServerService)
    computeService.servers() >> serverService
    Server server = Mock(Server)
    Addresses addresses = Mock(Addresses)
    server.addresses >> addresses
    Address address = Mock(Address)
    addresses.addresses >> ['test': [address]]
    address.addr >> '1.2.3.4'

    when:
    String ip = provider.getIpForInstance(region, id)

    then:
    1 * serverService.get(id) >> server
    ip == '1.2.3.4'
  }

  def "test get ip address for instance throws exception"() {
    setup:
    String id = UUID.randomUUID().toString()
    ComputeService computeService = Mock(ComputeService)
    mockClient.compute() >> computeService
    ServerService serverService = Mock(ServerService)
    computeService.servers() >> serverService
    Server server = Mock(Server)
    Addresses addresses = Mock(Addresses)
    server.addresses >> addresses
    addresses.addresses >> [:]

    when:
    provider.getIpForInstance(region, id)

    then:
    1 * serverService.get(id) >> server
    Exception e = thrown(OpenstackProviderException)
    e.message == "Instance ${id} has no IP address".toString()
  }

  def "test get load balancer succeeds"() {
    setup:
    String lbid = UUID.randomUUID().toString()
    NetworkingService networkingService = Mock(NetworkingService)
    mockClient.networking() >> networkingService
    LoadBalancerService lbService = Mock(LoadBalancerService)
    networkingService.loadbalancers() >> lbService
    LbPoolService poolService = Mock(LbPoolService)
    lbService.lbPool() >> poolService
    LbPool pool = Mock(LbPool)

    when:
    LbPool actual = provider.getLoadBalancerPool(region, lbid)

    then:
    1 * poolService.get(lbid) >> pool
    pool == actual
  }

  def "test get load balancer throws exception"() {
    setup:
    String lbid = UUID.randomUUID().toString()
    NetworkingService networkingService = Mock(NetworkingService)
    mockClient.networking() >> networkingService
    LoadBalancerService lbService = Mock(LoadBalancerService)
    networkingService.loadbalancers() >> lbService
    LbPoolService poolService = Mock(LbPoolService)
    lbService.lbPool() >> poolService

    when:
    provider.getLoadBalancerPool(region, lbid)

    then:
    1 * poolService.get(lbid) >> { throw new Exception("foobar") }
    Exception e = thrown(OpenstackProviderException)
    e.message == "Unable to find load balancer ${lbid}".toString()
  }

  def "test add member to load balancer pool succeeds"() {
    setup:
    String ip = '1.2.3.4'
    int port = 8100
    int weight = 1
    String lbid = UUID.randomUUID().toString()
    NetworkingService networkingService = Mock(NetworkingService)
    mockClient.networking() >> networkingService
    LoadBalancerService lbService = Mock(LoadBalancerService)
    networkingService.loadbalancers() >> lbService
    MemberService memberService = Mock(MemberService)
    lbService.member() >> memberService
    Member mockMember = Mock(Member)

    when:
    Member actual = provider.addMemberToLoadBalancerPool(region, ip, lbid, port, weight)

    then:
    1 * memberService.create(_ as Member) >> mockMember
    mockMember == actual
  }

  def "test add member to load balancer pool throws exception"() {
    setup:
    String ip = '1.2.3.4'
    int port = 8100
    int weight = 1
    String lbid = UUID.randomUUID().toString()
    NetworkingService networkingService = Mock(NetworkingService)
    mockClient.networking() >> networkingService
    LoadBalancerService lbService = Mock(LoadBalancerService)
    networkingService.loadbalancers() >> lbService
    MemberService memberService = Mock(MemberService)
    lbService.member() >> memberService

    when:
    provider.addMemberToLoadBalancerPool(region, ip, lbid, port, weight)

    then:
    1 * memberService.create(_ as Member) >> { throw new Exception("foobar") }
    Exception e = thrown(OpenstackProviderException)
    e.message == "Unable to add ip $ip to load balancer ${lbid}".toString()
  }

  def "test remove member from load balancer pool succeeds"() {
    setup:
    def success = ActionResponse.actionSuccess()
    String memberId = UUID.randomUUID().toString()
    NetworkingService networkingService = Mock(NetworkingService)
    mockClient.networking() >> networkingService
    LoadBalancerService lbService = Mock(LoadBalancerService)
    networkingService.loadbalancers() >> lbService
    MemberService memberService = Mock(MemberService)
    lbService.member() >> memberService

    when:
    ActionResponse response = provider.removeMemberFromLoadBalancerPool(region, memberId)

    then:
    1 * memberService.delete(memberId) >> success
    response != null
    response.code == 200
    response.success
    response == success
  }

  def "test remove member from load balancer pool fails"() {
    setup:
    def failure = ActionResponse.actionFailed('failed', 404)
    String memberId = UUID.randomUUID().toString()
    NetworkingService networkingService = Mock(NetworkingService)
    mockClient.networking() >> networkingService
    LoadBalancerService lbService = Mock(LoadBalancerService)
    networkingService.loadbalancers() >> lbService
    MemberService memberService = Mock(MemberService)
    lbService.member() >> memberService

    when:
    ActionResponse response = provider.removeMemberFromLoadBalancerPool(region, memberId)

    then:
    1 * memberService.delete(memberId) >> failure
    response != null
    response.fault == 'failed'
    response.code == 404
    response == failure
  }

  def "test remove member from load balancer pool throws exception"() {
    setup:
    String memberId = UUID.randomUUID().toString()
    NetworkingService networkingService = Mock(NetworkingService)
    mockClient.networking() >> networkingService
    LoadBalancerService lbService = Mock(LoadBalancerService)
    networkingService.loadbalancers() >> lbService
    MemberService memberService = Mock(MemberService)
    lbService.member() >> memberService

    when:
    provider.removeMemberFromLoadBalancerPool(region, memberId)

    then:
    1 * memberService.delete(memberId) >> { throw new Exception('foobar') }
    Exception e = thrown(OpenstackProviderException)
    e.message == "Unable to remove load balancer member $memberId".toString()
  }

  def "test get member id for instance succeeds"() {
    setup:
    String ip = '1.2.3.4'
    String id = UUID.randomUUID().toString()
    String memberId = UUID.randomUUID().toString()
    NetworkingService networkingService = Mock(NetworkingService)
    mockClient.networking() >> networkingService
    LoadBalancerService lbService = Mock(LoadBalancerService)
    networkingService.loadbalancers() >> lbService
    MemberService memberService = Mock(MemberService)
    lbService.member() >> memberService
    LbPool pool = Mock(LbPool)
    Member member = Mock(Member)
    member.id >> memberId
    member.address >> ip
    pool.members >> [memberId]

    when:
    String actual = provider.getMemberIdForInstance(region, ip, pool)

    then:
    1 * memberService.list() >> [member]
    actual == memberId
  }

  def "test get member id for instance, member not found, throws exception"() {
    setup:
    String ip = '1.2.3.4'
    String memberId = UUID.randomUUID().toString()
    NetworkingService networkingService = Mock(NetworkingService)
    mockClient.networking() >> networkingService
    LoadBalancerService lbService = Mock(LoadBalancerService)
    networkingService.loadbalancers() >> lbService
    MemberService memberService = Mock(MemberService)
    lbService.member() >> memberService
    LbPool pool = Mock(LbPool)
    Member member = Mock(Member)
    member.id >> memberId
    member.address >> ip
    pool.members >> [memberId]

    when:
    provider.getMemberIdForInstance(region, ip, pool)

    then:
    1 * memberService.list() >> []
    Exception e = thrown(OpenstackProviderException)
    e.message == "Instance with ip ${ip} is not associated with any load balancer memberships".toString()
  }

  def "test get member id for instance, member found but not part of load balancer, throws exception"() {
    setup:
    String ip = '1.2.3.4'
    String memberId = UUID.randomUUID().toString()
    NetworkingService networkingService = Mock(NetworkingService)
    mockClient.networking() >> networkingService
    LoadBalancerService lbService = Mock(LoadBalancerService)
    networkingService.loadbalancers() >> lbService
    MemberService memberService = Mock(MemberService)
    lbService.member() >> memberService
    LbPool pool = Mock(LbPool)
    pool.id >> UUID.randomUUID().toString()
    Member member = Mock(Member)
    member.id >> memberId
    member.address >> ip
    pool.members >> []

    when:
    provider.getMemberIdForInstance(region, ip, pool)

    then:
    1 * memberService.list() >> [member]
    Exception e = thrown(OpenstackProviderException)
    e.message == "Member id ${memberId} is not associated with load balancer with id ${pool.id}".toString()
  }

  def "test get member id for instance throws exception"() {
    setup:
    String ip = '1.2.3.4'
    NetworkingService networkingService = Mock(NetworkingService)
    mockClient.networking() >> networkingService
    LoadBalancerService lbService = Mock(LoadBalancerService)
    networkingService.loadbalancers() >> lbService
    MemberService memberService = Mock(MemberService)
    lbService.member() >> memberService
    LbPool pool = Mock(LbPool)

    when:
    provider.getMemberIdForInstance(region, ip, pool)

    then:
    1 * memberService.list() >> { throw new Exception('foobar') }
    Exception e = thrown(OpenstackProviderException)
    e.message == "Failed to list load balancer members".toString()
  }

  def "delete vip success"() {
    setup:
    String region = 'region1'
    String vipId = UUID.randomUUID().toString()
    NetworkingService networkingService = Mock()
    LoadBalancerService loadBalancerService = Mock()
    VipService vipService = Mock()
    ActionResponse response = ActionResponse.actionSuccess()

    when:
    provider.deleteVip(region, vipId)

    then:
    1 * mockClient.networking() >> networkingService
    1 * networkingService.loadbalancers() >> loadBalancerService
    1 * loadBalancerService.vip() >> vipService
    1 * vipService.delete(vipId) >> response
    noExceptionThrown()
  }

  def "delete vip - action failed"() {
    setup:
    String region = 'region1'
    String vipId = UUID.randomUUID().toString()
    NetworkingService networkingService = Mock()
    LoadBalancerService loadBalancerService = Mock()
    VipService vipService = Mock()
    ActionResponse response = ActionResponse.actionFailed('foo', HttpStatus.INTERNAL_SERVER_ERROR.value())

    when:
    provider.deleteVip(region, vipId)

    then:
    1 * mockClient.networking() >> networkingService
    1 * networkingService.loadbalancers() >> loadBalancerService
    1 * loadBalancerService.vip() >> vipService
    1 * vipService.delete(vipId) >> response
    OpenstackProviderException ex = thrown(OpenstackProviderException)
    ex.message.contains("foo")
  }

  def "delete vip - exception"() {
    setup:
    String region = 'region1'
    String vipId = UUID.randomUUID().toString()
    NetworkingService networkingService = Mock()
    LoadBalancerService loadBalancerService = Mock()
    VipService vipService = Mock()
    Throwable throwable = new Exception('foo')

    when:
    provider.deleteVip(region, vipId)

    then:
    1 * mockClient.networking() >> networkingService
    1 * networkingService.loadbalancers() >> loadBalancerService
    1 * loadBalancerService.vip() >> vipService
    1 * vipService.delete(vipId) >> { throw throwable }
    OpenstackProviderException ex = thrown(OpenstackProviderException)
    ex.cause == throwable
  }

  def "delete load balancer pool success"() {
    setup:
    String region = 'region1'
    String loadBalancerId = UUID.randomUUID().toString()
    NetworkingService networkingService = Mock()
    LoadBalancerService loadBalancerService = Mock()
    LbPoolService lbPoolService = Mock()
    ActionResponse response = ActionResponse.actionSuccess()

    when:
    provider.deleteLoadBalancerPool(region, loadBalancerId)

    then:
    1 * mockClient.networking() >> networkingService
    1 * networkingService.loadbalancers() >> loadBalancerService
    1 * loadBalancerService.lbPool() >> lbPoolService
    1 * lbPoolService.delete(loadBalancerId) >> response
    noExceptionThrown()
  }

  def "delete load balancer pool - action failed"() {
    setup:
    String region = 'region1'
    String loadBalancerId = UUID.randomUUID().toString()
    NetworkingService networkingService = Mock()
    LoadBalancerService loadBalancerService = Mock()
    LbPoolService lbPoolService = Mock()
    ActionResponse response = ActionResponse.actionFailed('foo', HttpStatus.INTERNAL_SERVER_ERROR.value())

    when:
    provider.deleteLoadBalancerPool(region, loadBalancerId)

    then:
    1 * mockClient.networking() >> networkingService
    1 * networkingService.loadbalancers() >> loadBalancerService
    1 * loadBalancerService.lbPool() >> lbPoolService
    1 * lbPoolService.delete(loadBalancerId) >> response
    OpenstackProviderException ex = thrown(OpenstackProviderException)
    ex.message.contains("foo")
  }

  def "delete load balancer pool - exception"() {
    setup:
    String region = 'region1'
    String loadBalancerId = UUID.randomUUID().toString()
    NetworkingService networkingService = Mock()
    LoadBalancerService loadBalancerService = Mock()
    LbPoolService lbPoolService = Mock()
    Throwable throwable = new Exception('foo')

    when:
    provider.deleteLoadBalancerPool(region, loadBalancerId)

    then:
    1 * mockClient.networking() >> networkingService
    1 * networkingService.loadbalancers() >> loadBalancerService
    1 * loadBalancerService.lbPool() >> lbPoolService
    1 * lbPoolService.delete(loadBalancerId) >> { throw throwable }
    OpenstackProviderException ex = thrown(OpenstackProviderException)
    ex.cause == throwable
  }

  def "disassociate and remove health monitor success"() {
    setup:
    String region = 'region1'
    String loadBalancerId = UUID.randomUUID().toString()
    String healthMonitorId = UUID.randomUUID().toString()
    NetworkingService networkingService = Mock()
    LoadBalancerService loadBalancerService = Mock()
    LbPoolService lbPoolService = Mock()
    HealthMonitorService healthMonitorService = Mock()
    ActionResponse response = ActionResponse.actionSuccess()

    when:
    provider.disassociateAndRemoveHealthMonitor(region, loadBalancerId, healthMonitorId)

    then:
    1 * mockClient.networking() >> networkingService
    1 * networkingService.loadbalancers() >> loadBalancerService
    1 * loadBalancerService.lbPool() >> lbPoolService
    1 * loadBalancerService.healthMonitor() >> healthMonitorService
    1 * lbPoolService.disAssociateHealthMonitor(loadBalancerId, healthMonitorId) >> response
    1 * healthMonitorService.delete(healthMonitorId) >> response
    noExceptionThrown()
  }

  def "disassociate and remove health monitor - failed action"() {
    setup:
    String region = 'region1'
    String loadBalancerId = UUID.randomUUID().toString()
    String healthMonitorId = UUID.randomUUID().toString()
    NetworkingService networkingService = Mock()
    LoadBalancerService loadBalancerService = Mock()
    LbPoolService lbPoolService = Mock()
    HealthMonitorService healthMonitorService = Mock()
    ActionResponse response = ActionResponse.actionFailed('foo', HttpStatus.INTERNAL_SERVER_ERROR.value())

    when:
    provider.disassociateAndRemoveHealthMonitor(region, loadBalancerId, healthMonitorId)

    then:
    1 * mockClient.networking() >> networkingService
    1 * networkingService.loadbalancers() >> loadBalancerService
    1 * loadBalancerService.lbPool() >> lbPoolService
    1 * loadBalancerService.healthMonitor() >> healthMonitorService
    1 * lbPoolService.disAssociateHealthMonitor(loadBalancerId, healthMonitorId) >> response
    1 * healthMonitorService.delete(healthMonitorId) >> response
    OpenstackProviderException ex = thrown(OpenstackProviderException)
    ex.message.contains("foo")
  }

  def "disassociate and remove health monitor - exception"() {
    setup:
    String region = 'region1'
    String loadBalancerId = UUID.randomUUID().toString()
    String healthMonitorId = UUID.randomUUID().toString()
    NetworkingService networkingService = Mock()
    LoadBalancerService loadBalancerService = Mock()
    LbPoolService lbPoolService = Mock()
    HealthMonitorService healthMonitorService = Mock()
    Throwable throwable = new Exception('foo')

    when:
    provider.disassociateAndRemoveHealthMonitor(region, loadBalancerId, healthMonitorId)

    then:
    1 * mockClient.networking() >> networkingService
    1 * networkingService.loadbalancers() >> loadBalancerService
    1 * loadBalancerService.lbPool() >> lbPoolService
    1 * loadBalancerService.healthMonitor() >> healthMonitorService
    1 * lbPoolService.disAssociateHealthMonitor(loadBalancerId, healthMonitorId) >> ActionResponse.actionSuccess()
    1 * healthMonitorService.delete(healthMonitorId) >> { throw throwable }
    OpenstackProviderException ex = thrown(OpenstackProviderException)
    ex.cause == throwable
  }
}
