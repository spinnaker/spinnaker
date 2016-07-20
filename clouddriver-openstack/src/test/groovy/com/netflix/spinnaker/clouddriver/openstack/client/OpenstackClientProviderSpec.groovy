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

import com.netflix.spinnaker.clouddriver.openstack.deploy.exception.OpenstackProviderException
import com.netflix.spinnaker.clouddriver.openstack.deploy.exception.OpenstackResourceNotFoundException
import com.netflix.spinnaker.clouddriver.openstack.domain.LoadBalancerPool
import com.netflix.spinnaker.clouddriver.openstack.domain.PoolHealthMonitor
import com.netflix.spinnaker.clouddriver.openstack.domain.ServerGroupParameters
import com.netflix.spinnaker.clouddriver.openstack.domain.VirtualIP
import org.openstack4j.api.Builders
import org.openstack4j.api.OSClient
import org.openstack4j.api.compute.ComputeFloatingIPService
import org.openstack4j.api.compute.ComputeSecurityGroupService
import org.openstack4j.api.compute.ComputeService
import org.openstack4j.api.compute.ServerService
import org.openstack4j.api.exceptions.ServerResponseException
import org.openstack4j.api.heat.HeatService
import org.openstack4j.api.heat.ResourcesService
import org.openstack4j.api.heat.StackService
import org.openstack4j.api.heat.TemplateService
import org.openstack4j.api.image.ImageService
import org.openstack4j.api.networking.NetFloatingIPService
import org.openstack4j.api.networking.NetworkingService
import org.openstack4j.api.networking.PortService
import org.openstack4j.api.networking.SubnetService
import org.openstack4j.api.networking.ext.HealthMonitorService
import org.openstack4j.api.networking.ext.LbPoolService
import org.openstack4j.api.networking.ext.LoadBalancerService
import org.openstack4j.api.networking.ext.MemberService
import org.openstack4j.api.networking.ext.VipService
import org.openstack4j.model.common.ActionResponse
import org.openstack4j.model.compute.Address
import org.openstack4j.model.compute.Addresses
import org.openstack4j.model.compute.FloatingIP
import org.openstack4j.model.compute.IPProtocol
import org.openstack4j.model.compute.SecGroupExtension
import org.openstack4j.model.compute.Server
import org.openstack4j.model.heat.Resource
import org.openstack4j.model.heat.Stack
import org.openstack4j.model.heat.StackCreate
import org.openstack4j.model.heat.StackUpdate
import org.openstack4j.model.image.Image
import org.openstack4j.model.network.NetFloatingIP
import org.openstack4j.model.network.Port
import org.openstack4j.model.network.Subnet
import org.openstack4j.model.network.ext.HealthMonitor
import org.openstack4j.model.network.ext.LbPool
import org.openstack4j.model.network.ext.Member
import org.openstack4j.model.network.ext.Vip
import org.openstack4j.openstack.compute.domain.NovaSecGroupExtension
import org.springframework.http.HttpStatus
import spock.lang.Specification
import spock.lang.Unroll

@Unroll
class OpenstackClientProviderSpec extends Specification {

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

      List<String> getAllRegions() {
        [region]
      }
    }
    mockClient.useRegion(region) >> mockClient
  }

  def "create security group rule"() {
    setup:
    ComputeService compute = Mock()
    ComputeSecurityGroupService securityGroupService = Mock()
    mockClient.compute() >> compute
    compute.securityGroups() >> securityGroupService

    def id = UUID.randomUUID().toString()

    when:
    provider.createSecurityGroupRule(region, id, protocol, cidr, remoteSecurityGroupId, fromPort, toPort, icmpType, icmpCode)

    then:
    1 * securityGroupService.createRule({ r ->
      def from = protocol == IPProtocol.ICMP ? icmpType : fromPort
      def to = protocol == IPProtocol.ICMP ? icmpCode : toPort
      r.parentGroupId == id && r.ipProtocol == protocol && r.cidr == cidr && r.fromPort == from && r.toPort == to
    })

    where:
    protocol        | cidr        | remoteSecurityGroupId        | fromPort | toPort | icmpType | icmpCode
    IPProtocol.TCP  | '0.0.0.0/0' | null                         | 80       | 81     | null     | null
    IPProtocol.UDP  | null        | UUID.randomUUID().toString() | 80       | 81     | null     | null
    IPProtocol.ICMP | '0.0.0.0/0' | null                         | null     | null   | 2        | 3

  }

  def "create security group rule throws exception"() {
    setup:
    ComputeService compute = Mock()
    ComputeSecurityGroupService securityGroupService = Mock()
    mockClient.compute() >> compute
    compute.securityGroups() >> securityGroupService

    def id = UUID.randomUUID().toString()
    def protocol = IPProtocol.TCP
    def cidr = '0.0.0.0/0'
    def fromPort = 80
    def toPort = 8080

    when:
    provider.createSecurityGroupRule(region, id, protocol, cidr, null, fromPort, toPort, null, null)

    then:
    1 * securityGroupService.createRule(_) >> { throw new RuntimeException('foo') }
    thrown(OpenstackProviderException)
  }

  def "delete security group rule"() {
    setup:
    ComputeService compute = Mock()
    ComputeSecurityGroupService securityGroupService = Mock()
    mockClient.compute() >> compute
    compute.securityGroups() >> securityGroupService

    def id = UUID.randomUUID().toString()

    when:
    provider.deleteSecurityGroupRule(region, id)

    then:
    1 * securityGroupService.deleteRule(id)
  }

  def "delete security group rule throws exception"() {
    setup:
    ComputeService compute = Mock()
    ComputeSecurityGroupService securityGroupService = Mock()
    mockClient.compute() >> compute
    compute.securityGroups() >> securityGroupService

    def id = UUID.randomUUID().toString()

    when:
    provider.deleteSecurityGroupRule(region, id)

    then:
    1 * securityGroupService.deleteRule(id) >> { throw new RuntimeException('foo') }
    thrown(OpenstackProviderException)
  }

  def "delete security group"() {
    setup:
    ComputeService compute = Mock()
    ComputeSecurityGroupService securityGroupService = Mock()
    mockClient.compute() >> compute
    compute.securityGroups() >> securityGroupService
    def id = UUID.randomUUID().toString()
    def success = ActionResponse.actionSuccess()

    when:
    provider.deleteSecurityGroup(region, id)

    then:
    1 * securityGroupService.delete(id) >> success
  }

  def "delete security group handles failure"() {
    setup:
    ComputeService compute = Mock()
    ComputeSecurityGroupService securityGroupService = Mock()
    mockClient.compute() >> compute
    compute.securityGroups() >> securityGroupService
    def id = UUID.randomUUID().toString()
    def failure = ActionResponse.actionFailed('foo', 500)

    when:
    provider.deleteSecurityGroup(region, id)

    then:
    1 * securityGroupService.delete(id) >> failure
    thrown(OpenstackProviderException)
  }

  def "create security group"() {
    setup:
    ComputeService compute = Mock()
    ComputeSecurityGroupService securityGroupService = Mock()
    mockClient.compute() >> compute
    compute.securityGroups() >> securityGroupService

    def name = 'security-group'
    def description = 'description 1'

    when:
    provider.createSecurityGroup(region, name, description)

    then:
    1 * securityGroupService.create(name, description)
  }

  def "create security group throws exception"() {
    setup:
    ComputeService compute = Mock()
    ComputeSecurityGroupService securityGroupService = Mock()
    mockClient.compute() >> compute
    compute.securityGroups() >> securityGroupService

    def name = 'security-group'
    def description = 'description 1'

    when:
    provider.createSecurityGroup(region, name, description)

    then:
    1 * securityGroupService.create(name, description) >> { throw new RuntimeException('foo') }
    thrown(OpenstackProviderException)
  }

  def "update security group"() {
    setup:
    ComputeService compute = Mock()
    ComputeSecurityGroupService securityGroupService = Mock()
    mockClient.compute() >> compute
    compute.securityGroups() >> securityGroupService

    def id = UUID.randomUUID().toString()
    def name = 'security-group'
    def description = 'description 1'

    when:
    provider.updateSecurityGroup(region, id, name, description)

    then:
    1 * securityGroupService.update(id, name, description)
  }

  def "update security group throws exception"() {
    setup:
    ComputeService compute = Mock()
    ComputeSecurityGroupService securityGroupService = Mock()
    mockClient.compute() >> compute
    compute.securityGroups() >> securityGroupService

    def id = UUID.randomUUID().toString()
    def name = 'security-group'
    def description = 'description 1'

    when:
    provider.updateSecurityGroup(region, id, name, description)

    then:
    1 * securityGroupService.update(id, name, description) >> { throw new RuntimeException('foo') }
    thrown(OpenstackProviderException)
  }

  def "get security group"() {
    setup:
    ComputeService compute = Mock()
    ComputeSecurityGroupService securityGroupService = Mock()
    mockClient.compute() >> compute
    compute.securityGroups() >> securityGroupService

    def id = UUID.randomUUID().toString()
    SecGroupExtension securityGroup = new NovaSecGroupExtension()

    when:
    def actual = provider.getSecurityGroup(region, id)

    then:
    actual == securityGroup
    1 * securityGroupService.get(id) >> securityGroup
  }

  def "get security group throws exception when not found"() {
    setup:
    ComputeService compute = Mock()
    ComputeSecurityGroupService securityGroupService = Mock()
    mockClient.compute() >> compute
    compute.securityGroups() >> securityGroupService

    def id = UUID.randomUUID().toString()

    when:
    provider.getSecurityGroup(region, id)

    then:
    1 * securityGroupService.get(id)
    thrown(OpenstackResourceNotFoundException)
  }

  def "get security group throws exception"() {
    setup:
    ComputeService compute = Mock()
    ComputeSecurityGroupService securityGroupService = Mock()
    mockClient.compute() >> compute
    compute.securityGroups() >> securityGroupService

    def id = UUID.randomUUID().toString()

    when:
    provider.getSecurityGroup(region, id)

    then:
    1 * securityGroupService.get(id) >> { throw new RuntimeException('foo') }
    thrown(OpenstackProviderException)
  }

  def "get all security groups by region"() {
    setup:
    ComputeService compute = Mock()
    ComputeSecurityGroupService securityGroupService = Mock()
    mockClient.compute() >> compute
    compute.securityGroups() >> securityGroupService
    def expected = [new NovaSecGroupExtension()]

    when:
    def actual = provider.getSecurityGroups(region)

    then:
    1 * securityGroupService.list() >> expected
    expected == actual
  }

  def "get all security groups by region throws exception"() {
    setup:
    ComputeService compute = Mock()
    ComputeSecurityGroupService securityGroupService = Mock()
    mockClient.compute() >> compute
    compute.securityGroups() >> securityGroupService
    def exception = new RuntimeException('foo')

    when:
    provider.getSecurityGroups(region)

    then:
    1 * securityGroupService.list() >> { throw exception }
    def e = thrown(OpenstackProviderException)
    e.cause == exception
  }

  def "get instances success"() {
    setup:
    ComputeService computeService = Mock()
    ServerService serversService = Mock()
    List<? extends Server> servers = Mock()

    when:
    List<? extends Server> result = provider.getInstances(region)

    then:
    1 * mockClient.compute() >> computeService
    1 * computeService.servers() >> serversService
    1 * serversService.list() >> servers
    result == servers
    noExceptionThrown()
  }

  def "get instances exception"() {
    setup:
    ComputeService computeService = Mock()
    ServerService serversService = Mock()
    Throwable throwable = new ServerResponseException('foo', HttpStatus.INTERNAL_SERVER_ERROR.value())

    when:
    provider.getInstances(region)

    then:
    1 * mockClient.compute() >> computeService
    1 * computeService.servers() >> serversService
    1 * serversService.list() >> { throw throwable }

    and:
    OpenstackProviderException openstackProviderException = thrown(OpenstackProviderException)
    openstackProviderException.cause == throwable
  }

  def "get console output success"() {
    setup:
    String serverId = UUID.randomUUID().toString()
    ComputeService computeService = Mock()
    ServerService serversService = Mock()
    Throwable throwable = new ServerResponseException('foo', HttpStatus.INTERNAL_SERVER_ERROR.value())

    when:
    provider.getConsoleOutput(region, serverId)

    then:
    1 * mockClient.compute() >> computeService
    1 * computeService.servers() >> serversService
    1 * serversService.getConsoleOutput(serverId, -1) >> { throw throwable }

    and:
    OpenstackProviderException openstackProviderException = thrown(OpenstackProviderException)
    openstackProviderException.cause == throwable
  }

  def "get console output exception"() {
    setup:
    String serverId = UUID.randomUUID().toString()
    ComputeService computeService = Mock()
    ServerService serversService = Mock()
    String output = 'output'

    when:
    String result = provider.getConsoleOutput(region, serverId)

    then:
    1 * mockClient.compute() >> computeService
    1 * computeService.servers() >> serversService
    1 * serversService.getConsoleOutput(serverId, -1) >> output
    result == output
    noExceptionThrown()
  }

  def "get vip success"() {
    setup:
    String region = 'region1'
    String vipId = UUID.randomUUID().toString()
    NetworkingService networkingService = Mock()
    LoadBalancerService loadBalancerService = Mock()
    VipService vipService = Mock()
    Vip vip = Mock()

    when:
    Vip result = provider.getVip(region, vipId)

    then:
    1 * mockClient.networking() >> networkingService
    1 * networkingService.loadbalancers() >> loadBalancerService
    1 * loadBalancerService.vip() >> vipService
    1 * vipService.get(vipId) >> vip
    result == vip
    noExceptionThrown()
  }

  def "get vip not found"() {
    setup:
    String region = 'region1'
    String vipId = UUID.randomUUID().toString()
    NetworkingService networkingService = Mock()
    LoadBalancerService loadBalancerService = Mock()
    VipService vipService = Mock()

    when:
    provider.getVip(region, vipId)

    then:
    1 * mockClient.networking() >> networkingService
    1 * networkingService.loadbalancers() >> loadBalancerService
    1 * loadBalancerService.vip() >> vipService
    1 * vipService.get(vipId) >> null

    and:
    OpenstackProviderException openstackProviderException = thrown(OpenstackProviderException)
    [region, vipId].every { openstackProviderException.message.contains(it) }
  }

  def "get vip exception"() {
    setup:
    String region = 'region1'
    String vipId = UUID.randomUUID().toString()
    NetworkingService networkingService = Mock()
    LoadBalancerService loadBalancerService = Mock()
    VipService vipService = Mock()
    Throwable throwable = new ServerResponseException('foo', HttpStatus.INTERNAL_SERVER_ERROR.value())

    when:
    provider.getVip(region, vipId)

    then:
    1 * mockClient.networking() >> networkingService
    1 * networkingService.loadbalancers() >> loadBalancerService
    1 * loadBalancerService.vip() >> vipService
    1 * vipService.get(vipId) >> { throw throwable }

    and:
    OpenstackProviderException openstackProviderException = thrown(OpenstackProviderException)
    openstackProviderException.cause == throwable
  }

  def "get subnet - #testCase"() {
    setup:
    String region = 'region1'
    String subnetId = UUID.randomUUID().toString()
    NetworkingService networkingService = Mock()
    SubnetService subnetService = Mock()

    when:
    Subnet subnet = provider.getSubnet(region, subnetId)

    then:
    1 * mockClient.networking() >> networkingService
    1 * networkingService.subnet() >> subnetService
    1 * subnetService.get(subnetId) >> expected
    subnet == expected
    noExceptionThrown()

    where:
    testCase           | expected
    'Subnet found'     | Mock(Subnet)
    'Subnet not found' | null
  }

  def "get subnet - exception"() {
    setup:
    String region = 'region1'
    String subnetId = UUID.randomUUID().toString()
    NetworkingService networkingService = Mock()
    SubnetService subnetService = Mock()
    Throwable throwable = new ServerResponseException('foo', HttpStatus.INTERNAL_SERVER_ERROR.value())

    when:
    provider.getSubnet(region, subnetId)

    then:
    1 * mockClient.networking() >> networkingService
    1 * networkingService.subnet() >> subnetService
    1 * subnetService.get(subnetId) >> { throw throwable }

    and:
    OpenstackProviderException openstackProviderException = thrown(OpenstackProviderException)
    openstackProviderException.cause == throwable
  }

  def "list all load balancer pools success"() {
    setup:
    NetworkingService networkingService = Mock()
    LoadBalancerService loadBalancerService = Mock()
    LbPoolService lbPoolService = Mock()
    List<? extends LbPool> lbPools = Mock()

    when:
    List<? extends LbPool> result = provider.getAllLoadBalancerPools(region)

    then:
    1 * mockClient.networking() >> networkingService
    1 * networkingService.loadbalancers() >> loadBalancerService
    1 * loadBalancerService.lbPool() >> lbPoolService
    1 * lbPoolService.list() >> lbPools
    result == lbPools
    noExceptionThrown()
  }

  def "list all load balancer pools exception"() {
    setup:
    NetworkingService networkingService = Mock()
    LoadBalancerService loadBalancerService = Mock()
    LbPoolService lbPoolService = Mock()
    Throwable throwable = new ServerResponseException('foo', HttpStatus.INTERNAL_SERVER_ERROR.value())

    when:
    provider.getAllLoadBalancerPools(region)

    then:
    1 * mockClient.networking() >> networkingService
    1 * networkingService.loadbalancers() >> loadBalancerService
    1 * loadBalancerService.lbPool() >> lbPoolService
    1 * lbPoolService.list() >> { throw throwable }

    and:
    OpenstackProviderException openstackProviderException = thrown(OpenstackProviderException)
    openstackProviderException.cause == throwable
  }

  def "create load balancer success"() {
    setup:
    NetworkingService networkingService = Mock()
    LoadBalancerService loadBalancerService = Mock()
    LbPoolService lbPoolService = Mock()
    LoadBalancerPool loadBalancerPool = Mock()
    LbPool lbPool = Mock()

    when:
    LbPool result = provider.createLoadBalancerPool(region, loadBalancerPool)

    then:
    1 * mockClient.networking() >> networkingService
    1 * networkingService.loadbalancers() >> loadBalancerService
    1 * loadBalancerService.lbPool() >> lbPoolService
    1 * lbPoolService.create(_) >> lbPool
    result == lbPool
    noExceptionThrown()
  }

  def "create load balancer exception"() {
    setup:
    NetworkingService networkingService = Mock()
    LoadBalancerService loadBalancerService = Mock()
    LbPoolService lbPoolService = Mock()
    LoadBalancerPool loadBalancerPool = Mock()
    Throwable throwable = new ServerResponseException('foo', HttpStatus.INTERNAL_SERVER_ERROR.value())

    when:
    provider.createLoadBalancerPool(region, loadBalancerPool)

    then:
    1 * mockClient.networking() >> networkingService
    1 * networkingService.loadbalancers() >> loadBalancerService
    1 * loadBalancerService.lbPool() >> lbPoolService
    1 * lbPoolService.create(_) >> { throw throwable }

    and:
    OpenstackProviderException openstackProviderException = thrown(OpenstackProviderException)
    openstackProviderException.cause == throwable
  }

  def "update load balancer success"() {
    setup:
    NetworkingService networkingService = Mock()
    LoadBalancerService loadBalancerService = Mock()
    LbPoolService lbPoolService = Mock()
    LoadBalancerPool loadBalancerPool = Mock()
    LbPool lbPool = Mock()

    when:
    LbPool result = provider.updateLoadBalancerPool(region, loadBalancerPool)

    then:
    1 * mockClient.networking() >> networkingService
    1 * networkingService.loadbalancers() >> loadBalancerService
    1 * loadBalancerService.lbPool() >> lbPoolService
    1 * lbPoolService.update(loadBalancerPool.id, _) >> lbPool
    result == lbPool
    noExceptionThrown()
  }

  def "update load balancer exception"() {
    setup:
    NetworkingService networkingService = Mock()
    LoadBalancerService loadBalancerService = Mock()
    LbPoolService lbPoolService = Mock()
    LoadBalancerPool loadBalancerPool = Mock()
    Throwable throwable = new ServerResponseException('foo', HttpStatus.INTERNAL_SERVER_ERROR.value())

    when:
    provider.updateLoadBalancerPool(region, loadBalancerPool)

    then:
    1 * mockClient.networking() >> networkingService
    1 * networkingService.loadbalancers() >> loadBalancerService
    1 * loadBalancerService.lbPool() >> lbPoolService
    1 * lbPoolService.update(loadBalancerPool.id, _) >> { throw throwable }

    and:
    OpenstackProviderException openstackProviderException = thrown(OpenstackProviderException)
    openstackProviderException.cause == throwable
  }

  def "create vip success"() {
    setup:
    NetworkingService networkingService = Mock()
    LoadBalancerService loadBalancerService = Mock()
    VipService vipService = Mock()
    VirtualIP virtualIP = Mock()
    Vip vip = Mock()

    when:
    Vip result = provider.createVip(region, virtualIP)

    then:
    1 * mockClient.networking() >> networkingService
    1 * networkingService.loadbalancers() >> loadBalancerService
    1 * loadBalancerService.vip() >> vipService
    1 * vipService.create(_) >> vip
    result == vip
    noExceptionThrown()
  }


  def "create vip exception"() {
    setup:
    NetworkingService networkingService = Mock()
    LoadBalancerService loadBalancerService = Mock()
    VipService vipService = Mock()
    VirtualIP virtualIP = Mock()
    Throwable throwable = new ServerResponseException('foo', HttpStatus.INTERNAL_SERVER_ERROR.value())

    when:
    provider.createVip(region, virtualIP)

    then:
    1 * mockClient.networking() >> networkingService
    1 * networkingService.loadbalancers() >> loadBalancerService
    1 * loadBalancerService.vip() >> vipService
    1 * vipService.create(_) >> { throw throwable }

    and:
    OpenstackProviderException openstackProviderException = thrown(OpenstackProviderException)
    openstackProviderException.cause == throwable
  }

  def "update vip success"() {
    setup:
    NetworkingService networkingService = Mock()
    LoadBalancerService loadBalancerService = Mock()
    VipService vipService = Mock()
    VirtualIP virtualIP = Mock()
    Vip vip = Mock()

    when:
    Vip result = provider.updateVip(region, virtualIP)

    then:
    1 * mockClient.networking() >> networkingService
    1 * networkingService.loadbalancers() >> loadBalancerService
    1 * loadBalancerService.vip() >> vipService
    1 * vipService.update(virtualIP.id, _) >> vip
    result == vip
    noExceptionThrown()
  }

  def "update vip exception"() {
    setup:
    NetworkingService networkingService = Mock()
    LoadBalancerService loadBalancerService = Mock()
    VipService vipService = Mock()
    VirtualIP virtualIP = Mock()
    Throwable throwable = new ServerResponseException('foo', HttpStatus.INTERNAL_SERVER_ERROR.value())

    when:
    provider.updateVip(region, virtualIP)

    then:
    1 * mockClient.networking() >> networkingService
    1 * networkingService.loadbalancers() >> loadBalancerService
    1 * loadBalancerService.vip() >> vipService
    1 * vipService.update(virtualIP.id, _) >> { throw throwable }

    and:
    OpenstackProviderException openstackProviderException = thrown(OpenstackProviderException)
    openstackProviderException.cause == throwable
  }

  def "get health monitor success"() {
    setup:
    String healthMonitorId = UUID.randomUUID().toString()
    NetworkingService networkingService = Mock()
    LoadBalancerService loadBalancerService = Mock()
    HealthMonitorService healthMonitorService = Mock()
    HealthMonitor healthMonitor = Mock()

    when:
    HealthMonitor result = provider.getHealthMonitor(region, healthMonitorId)

    then:
    1 * mockClient.networking() >> networkingService
    1 * networkingService.loadbalancers() >> loadBalancerService
    1 * loadBalancerService.healthMonitor() >> healthMonitorService
    1 * healthMonitorService.get(healthMonitorId) >> healthMonitor
    result == healthMonitor
    noExceptionThrown()
  }

  def "get health monitor not found"() {
    setup:
    String healthMonitorId = UUID.randomUUID().toString()
    NetworkingService networkingService = Mock()
    LoadBalancerService loadBalancerService = Mock()
    HealthMonitorService healthMonitorService = Mock()

    when:
    provider.getHealthMonitor(region, healthMonitorId)

    then:
    1 * mockClient.networking() >> networkingService
    1 * networkingService.loadbalancers() >> loadBalancerService
    1 * loadBalancerService.healthMonitor() >> healthMonitorService
    1 * healthMonitorService.get(healthMonitorId) >> null

    and:
    OpenstackProviderException openstackProviderException = thrown(OpenstackProviderException)
    [healthMonitorId, region].every { openstackProviderException.message.contains(it) }
  }


  def "get health monitor exception"() {
    setup:
    String healthMonitorId = UUID.randomUUID().toString()
    NetworkingService networkingService = Mock()
    LoadBalancerService loadBalancerService = Mock()
    HealthMonitorService healthMonitorService = Mock()
    Throwable throwable = new ServerResponseException('foo', HttpStatus.INTERNAL_SERVER_ERROR.value())

    when:
    provider.getHealthMonitor(region, healthMonitorId)

    then:
    1 * mockClient.networking() >> networkingService
    1 * networkingService.loadbalancers() >> loadBalancerService
    1 * loadBalancerService.healthMonitor() >> healthMonitorService
    1 * healthMonitorService.get(healthMonitorId) >> { throw throwable }

    and:
    OpenstackProviderException openstackProviderException = thrown(OpenstackProviderException)
    openstackProviderException.cause == throwable
  }

  def "create health monitor success"() {
    setup:
    String region = 'region1'
    String lbPoolId = UUID.randomUUID().toString()
    NetworkingService networkingService = Mock()
    LoadBalancerService loadBalancerService = Mock()
    LbPoolService lbPoolService = Mock()
    HealthMonitorService healthMonitorService = Mock()
    PoolHealthMonitor poolHealthMonitor = Mock()
    HealthMonitor createdHealthMonitor = Mock()
    HealthMonitor updatedHealthMonitor = Mock()

    when:
    HealthMonitor result = provider.createHealthCheckForPool(region, lbPoolId, poolHealthMonitor)

    then:
    1 * mockClient.networking() >> networkingService
    1 * networkingService.loadbalancers() >> loadBalancerService
    1 * loadBalancerService.healthMonitor() >> healthMonitorService
    1 * healthMonitorService.create(_) >> createdHealthMonitor
    1 * loadBalancerService.lbPool() >> lbPoolService
    1 * lbPoolService.associateHealthMonitor(lbPoolId, createdHealthMonitor.id) >> updatedHealthMonitor
    result == updatedHealthMonitor
    result != createdHealthMonitor
    noExceptionThrown()
  }

  def "create health monitor - null result"() {
    setup:
    String region = 'region1'
    String lbPoolId = UUID.randomUUID().toString()
    NetworkingService networkingService = Mock()
    LoadBalancerService loadBalancerService = Mock()
    LbPoolService lbPoolService = Mock()
    HealthMonitorService healthMonitorService = Mock()
    PoolHealthMonitor poolHealthMonitor = Mock()
    HealthMonitor createdHealthMonitor = null

    when:
    provider.createHealthCheckForPool(region, lbPoolId, poolHealthMonitor)

    then:
    1 * mockClient.networking() >> networkingService
    1 * networkingService.loadbalancers() >> loadBalancerService
    1 * loadBalancerService.healthMonitor() >> healthMonitorService
    1 * healthMonitorService.create(_) >> createdHealthMonitor
    0 * loadBalancerService.lbPool() >> lbPoolService
    0 * lbPoolService.associateHealthMonitor(lbPoolId, _)

    and:
    OpenstackProviderException openstackProviderException = thrown(OpenstackProviderException)
    openstackProviderException.message.contains(lbPoolId)
  }

  def "create health monitor - exception creating"() {
    setup:
    String region = 'region1'
    String lbPoolId = UUID.randomUUID().toString()
    NetworkingService networkingService = Mock()
    LoadBalancerService loadBalancerService = Mock()
    LbPoolService lbPoolService = Mock()
    HealthMonitorService healthMonitorService = Mock()
    PoolHealthMonitor poolHealthMonitor = Mock()
    Throwable throwable = new ServerResponseException('foo', HttpStatus.INTERNAL_SERVER_ERROR.value())

    when:
    provider.createHealthCheckForPool(region, lbPoolId, poolHealthMonitor)

    then:
    1 * mockClient.networking() >> networkingService
    1 * networkingService.loadbalancers() >> loadBalancerService
    1 * loadBalancerService.healthMonitor() >> healthMonitorService
    1 * healthMonitorService.create(_) >> { throw throwable }
    0 * loadBalancerService.lbPool() >> lbPoolService
    0 * lbPoolService.associateHealthMonitor(lbPoolId, _)

    and:
    OpenstackProviderException openstackProviderException = thrown(OpenstackProviderException)
    openstackProviderException.cause == throwable
  }

  def "create health monitor - exception associating"() {
    setup:
    String region = 'region1'
    String lbPoolId = UUID.randomUUID().toString()
    NetworkingService networkingService = Mock()
    LoadBalancerService loadBalancerService = Mock()
    LbPoolService lbPoolService = Mock()
    HealthMonitorService healthMonitorService = Mock()
    PoolHealthMonitor poolHealthMonitor = Mock()
    HealthMonitor createdHealthMonitor = Mock()
    Throwable throwable = new ServerResponseException('foo', HttpStatus.INTERNAL_SERVER_ERROR.value())

    when:
    provider.createHealthCheckForPool(region, lbPoolId, poolHealthMonitor)

    then:
    1 * mockClient.networking() >> networkingService
    1 * networkingService.loadbalancers() >> loadBalancerService
    1 * loadBalancerService.healthMonitor() >> healthMonitorService
    1 * healthMonitorService.create(_) >> createdHealthMonitor
    1 * loadBalancerService.lbPool() >> lbPoolService
    1 * lbPoolService.associateHealthMonitor(lbPoolId, createdHealthMonitor.id) >> { throw throwable }

    and:
    OpenstackProviderException openstackProviderException = thrown(OpenstackProviderException)
    openstackProviderException.cause == throwable
  }

  def "update health monitor success"() {
    setup:
    NetworkingService networkingService = Mock()
    LoadBalancerService loadBalancerService = Mock()
    HealthMonitorService healthMonitorService = Mock()
    HealthMonitor healthMonitor = Mock()
    PoolHealthMonitor poolHealthMonitor = Mock()

    when:
    HealthMonitor result = provider.updateHealthMonitor(region, poolHealthMonitor)

    then:
    1 * mockClient.networking() >> networkingService
    1 * networkingService.loadbalancers() >> loadBalancerService
    1 * loadBalancerService.healthMonitor() >> healthMonitorService
    1 * healthMonitorService.update(poolHealthMonitor.id, _) >> healthMonitor
    result == healthMonitor
    noExceptionThrown()
  }

  def "update health monitor exception"() {
    setup:
    NetworkingService networkingService = Mock()
    LoadBalancerService loadBalancerService = Mock()
    HealthMonitorService healthMonitorService = Mock()
    PoolHealthMonitor poolHealthMonitor = Mock()
    Throwable throwable = new ServerResponseException('foo', HttpStatus.INTERNAL_SERVER_ERROR.value())

    when:
    provider.updateHealthMonitor(region, poolHealthMonitor)

    then:
    1 * mockClient.networking() >> networkingService
    1 * networkingService.loadbalancers() >> loadBalancerService
    1 * loadBalancerService.healthMonitor() >> healthMonitorService
    1 * healthMonitorService.update(poolHealthMonitor.id, _) >> { throw throwable }

    and:
    OpenstackProviderException openstackProviderException = thrown(OpenstackProviderException)
    openstackProviderException.cause == throwable
  }

  def "disassociate and remove health monitor success"() {
    setup:
    String region = 'region1'
    String lbPoolId = UUID.randomUUID().toString()
    String healthMonitorId = UUID.randomUUID().toString()
    NetworkingService networkingService = Mock()
    LoadBalancerService loadBalancerService = Mock()
    LbPoolService lbPoolService = Mock()
    HealthMonitorService healthMonitorService = Mock()

    when:
    provider.disassociateAndRemoveHealthMonitor(region, lbPoolId, healthMonitorId)

    then:
    2 * mockClient.networking() >> networkingService
    2 * networkingService.loadbalancers() >> loadBalancerService
    1 * loadBalancerService.lbPool() >> lbPoolService
    1 * lbPoolService.disAssociateHealthMonitor(lbPoolId, healthMonitorId)
    1 * loadBalancerService.healthMonitor() >> healthMonitorService
    1 * healthMonitorService.delete(healthMonitorId)
    noExceptionThrown()
  }

  def "disassociate exception and no removal"() {
    setup:
    String region = 'region1'
    String lbPoolId = UUID.randomUUID().toString()
    String healthMonitorId = UUID.randomUUID().toString()
    NetworkingService networkingService = Mock()
    LoadBalancerService loadBalancerService = Mock()
    LbPoolService lbPoolService = Mock()
    HealthMonitorService healthMonitorService = Mock()
    Throwable throwable = new ServerResponseException('foo', HttpStatus.INTERNAL_SERVER_ERROR.value())

    when:
    provider.disassociateAndRemoveHealthMonitor(region, lbPoolId, healthMonitorId)

    then:
    1 * mockClient.networking() >> networkingService
    1 * networkingService.loadbalancers() >> loadBalancerService
    1 * loadBalancerService.lbPool() >> lbPoolService
    1 * lbPoolService.disAssociateHealthMonitor(lbPoolId, healthMonitorId) >> { throw throwable }
    0 * loadBalancerService.healthMonitor() >> healthMonitorService
    0 * healthMonitorService.delete(healthMonitorId)

    and:
    OpenstackProviderException openstackProviderException = thrown(OpenstackProviderException)
    openstackProviderException.cause == throwable
  }

  def "disassociate and removal exception"() {
    setup:
    String region = 'region1'
    String lbPoolId = UUID.randomUUID().toString()
    String healthMonitorId = UUID.randomUUID().toString()
    NetworkingService networkingService = Mock()
    LoadBalancerService loadBalancerService = Mock()
    LbPoolService lbPoolService = Mock()
    HealthMonitorService healthMonitorService = Mock()
    Throwable throwable = new ServerResponseException('foo', HttpStatus.INTERNAL_SERVER_ERROR.value())

    when:
    provider.disassociateAndRemoveHealthMonitor(region, lbPoolId, healthMonitorId)

    then:
    2 * mockClient.networking() >> networkingService
    2 * networkingService.loadbalancers() >> loadBalancerService
    1 * loadBalancerService.lbPool() >> lbPoolService
    1 * lbPoolService.disAssociateHealthMonitor(lbPoolId, healthMonitorId)
    1 * loadBalancerService.healthMonitor() >> healthMonitorService
    1 * healthMonitorService.delete(healthMonitorId) >> { throw throwable }

    and:
    OpenstackProviderException openstackProviderException = thrown(OpenstackProviderException)
    openstackProviderException.cause == throwable
  }

  def "delete health monitor success"() {
    setup:
    String healthMonitorId = UUID.randomUUID().toString()
    NetworkingService networkingService = Mock()
    LoadBalancerService loadBalancerService = Mock()
    HealthMonitorService healthMonitorService = Mock()
    ActionResponse actionResponse = ActionResponse.actionSuccess()

    when:
    ActionResponse result = provider.deleteHealthMonitor(region, healthMonitorId)

    then:
    1 * mockClient.networking() >> networkingService
    1 * networkingService.loadbalancers() >> loadBalancerService
    1 * loadBalancerService.healthMonitor() >> healthMonitorService
    1 * healthMonitorService.delete(healthMonitorId) >> actionResponse
    result == actionResponse
    noExceptionThrown()
  }

  def "delete health monitor failed response"() {
    setup:
    String healthMonitorId = UUID.randomUUID().toString()
    NetworkingService networkingService = Mock()
    LoadBalancerService loadBalancerService = Mock()
    HealthMonitorService healthMonitorService = Mock()
    ActionResponse actionResponse = ActionResponse.actionFailed('foo', HttpStatus.INTERNAL_SERVER_ERROR.value())

    when:
    provider.deleteHealthMonitor(region, healthMonitorId)

    then:
    1 * mockClient.networking() >> networkingService
    1 * networkingService.loadbalancers() >> loadBalancerService
    1 * loadBalancerService.healthMonitor() >> healthMonitorService
    1 * healthMonitorService.delete(healthMonitorId) >> actionResponse

    and:
    OpenstackProviderException openstackProviderException = thrown(OpenstackProviderException)
    openstackProviderException.message.contains(actionResponse.fault)
    openstackProviderException.message.contains(String.valueOf(actionResponse.code))
  }

  def "delete health monitor exception"() {
    setup:
    String healthMonitorId = UUID.randomUUID().toString()
    NetworkingService networkingService = Mock()
    LoadBalancerService loadBalancerService = Mock()
    HealthMonitorService healthMonitorService = Mock()
    Throwable throwable = new ServerResponseException('foo', HttpStatus.INTERNAL_SERVER_ERROR.value())

    when:
    provider.deleteHealthMonitor(region, healthMonitorId)

    then:
    1 * mockClient.networking() >> networkingService
    1 * networkingService.loadbalancers() >> loadBalancerService
    1 * loadBalancerService.healthMonitor() >> healthMonitorService
    1 * healthMonitorService.delete(healthMonitorId) >> { throw throwable }

    and:
    OpenstackProviderException openstackProviderException = thrown(OpenstackProviderException)
    openstackProviderException.cause == throwable
  }

  def "disassociate health monitor success"() {
    setup:
    String lbPoolId = UUID.randomUUID().toString()
    String healthMonitorId = UUID.randomUUID().toString()
    NetworkingService networkingService = Mock()
    LoadBalancerService loadBalancerService = Mock()
    LbPoolService lbPoolService = Mock()
    ActionResponse actionResponse = ActionResponse.actionSuccess()

    when:
    ActionResponse result = provider.disassociateHealthMonitor(region, lbPoolId, healthMonitorId)

    then:
    1 * mockClient.networking() >> networkingService
    1 * networkingService.loadbalancers() >> loadBalancerService
    1 * loadBalancerService.lbPool() >> lbPoolService
    1 * lbPoolService.disAssociateHealthMonitor(lbPoolId, healthMonitorId) >> actionResponse
    result == actionResponse
    noExceptionThrown()
  }

  def "disassociate health monitor failed response"() {
    setup:
    String lbPoolId = UUID.randomUUID().toString()
    String healthMonitorId = UUID.randomUUID().toString()
    NetworkingService networkingService = Mock()
    LoadBalancerService loadBalancerService = Mock()
    LbPoolService lbPoolService = Mock()
    ActionResponse actionResponse = ActionResponse.actionFailed('foo', HttpStatus.INTERNAL_SERVER_ERROR.value())

    when:
    provider.disassociateHealthMonitor(region, lbPoolId, healthMonitorId)

    then:
    1 * mockClient.networking() >> networkingService
    1 * networkingService.loadbalancers() >> loadBalancerService
    1 * loadBalancerService.lbPool() >> lbPoolService
    1 * lbPoolService.disAssociateHealthMonitor(lbPoolId, healthMonitorId) >> actionResponse

    and:
    OpenstackProviderException openstackProviderException = thrown(OpenstackProviderException)
    openstackProviderException.message.contains(actionResponse.fault)
    openstackProviderException.message.contains(String.valueOf(actionResponse.code))
  }

  def "disassociate health monitor exception"() {
    setup:
    String lbPoolId = UUID.randomUUID().toString()
    String healthMonitorId = UUID.randomUUID().toString()
    NetworkingService networkingService = Mock()
    LoadBalancerService loadBalancerService = Mock()
    LbPoolService lbPoolService = Mock()
    Throwable throwable = new ServerResponseException('foo', HttpStatus.INTERNAL_SERVER_ERROR.value())

    when:
    provider.disassociateHealthMonitor(region, lbPoolId, healthMonitorId)

    then:
    1 * mockClient.networking() >> networkingService
    1 * networkingService.loadbalancers() >> loadBalancerService
    1 * loadBalancerService.lbPool() >> lbPoolService
    1 * lbPoolService.disAssociateHealthMonitor(lbPoolId, healthMonitorId) >> { throw throwable }

    and:
    OpenstackProviderException openstackProviderException = thrown(OpenstackProviderException)
    openstackProviderException.cause == throwable
  }

  def "associate floating ip to vip success"() {
    setup:
    String floatingIp = UUID.randomUUID().toString()
    String vipId = UUID.randomUUID().toString()
    String portId = UUID.randomUUID().toString()
    NetworkingService networkingService = Mock()
    PortService portService = Mock()
    Port port = Mock()
    NetFloatingIPService floatingIPService = Mock()
    NetFloatingIP netFloatingIP = Mock()

    when:
    NetFloatingIP result = provider.associateFloatingIpToVip(region, floatingIp, vipId)

    then:
    2 * mockClient.networking() >> networkingService
    1 * networkingService.port() >> portService
    1 * portService.list() >> [port]
    1 * port.name >> "vip-${vipId}"
    1 * port.id >> portId
    1 * networkingService.floatingip() >> floatingIPService
    1 * floatingIPService.associateToPort(floatingIp, portId) >> netFloatingIP
    result == netFloatingIP
    noExceptionThrown()
  }

  def "associate floating ip to vip - not found"() {
    setup:
    String floatingIp = UUID.randomUUID().toString()
    String vipId = UUID.randomUUID().toString()
    String portId = UUID.randomUUID().toString()
    NetworkingService networkingService = Mock()
    PortService portService = Mock()
    Port port = Mock()
    NetFloatingIPService floatingIPService = Mock()
    NetFloatingIP netFloatingIP = Mock()

    when:
    provider.associateFloatingIpToVip(region, floatingIp, vipId)

    then:
    1 * mockClient.networking() >> networkingService
    1 * networkingService.port() >> portService
    1 * portService.list() >> [port]
    1 * port.name >> "vip"
    0 * port.id >> portId
    0 * networkingService.floatingip() >> floatingIPService
    0 * floatingIPService.associateToPort(floatingIp, portId) >> netFloatingIP

    and:
    OpenstackProviderException openstackProviderException = thrown(OpenstackProviderException)
    openstackProviderException.message.contains(vipId)
  }

  def "associate floating ip to vip - exception"() {
    setup:
    String floatingIp = UUID.randomUUID().toString()
    String vipId = UUID.randomUUID().toString()
    String portId = UUID.randomUUID().toString()
    NetworkingService networkingService = Mock()
    PortService portService = Mock()
    Port port = Mock()
    NetFloatingIPService floatingIPService = Mock()
    Throwable throwable = new ServerResponseException('foo', HttpStatus.INTERNAL_SERVER_ERROR.value())

    when:
    provider.associateFloatingIpToVip(region, floatingIp, vipId)

    then:
    2 * mockClient.networking() >> networkingService
    1 * networkingService.port() >> portService
    1 * portService.list() >> [port]
    1 * port.name >> "vip-${vipId}"
    1 * port.id >> portId
    1 * networkingService.floatingip() >> floatingIPService
    1 * floatingIPService.associateToPort(floatingIp, portId) >> { throw throwable }

    and:
    OpenstackProviderException openstackProviderException = thrown(OpenstackProviderException)
    openstackProviderException.cause == throwable
  }

  def "disassociate floating ip success"() {
    setup:
    String floatingIp = UUID.randomUUID().toString()
    NetworkingService networkingService = Mock()
    NetFloatingIPService floatingIPService = Mock()
    NetFloatingIP netFloatingIP = Mock()

    when:
    NetFloatingIP result = provider.disassociateFloatingIp(region, floatingIp)

    then:
    1 * mockClient.networking() >> networkingService
    1 * networkingService.floatingip() >> floatingIPService
    1 * floatingIPService.disassociateFromPort(floatingIp) >> netFloatingIP
    result == netFloatingIP
    noExceptionThrown()
  }

  def "disassociate floating ip exception"() {
    setup:
    String floatingIp = UUID.randomUUID().toString()
    NetworkingService networkingService = Mock()
    NetFloatingIPService floatingIPService = Mock()
    Throwable throwable = new ServerResponseException('foo', HttpStatus.INTERNAL_SERVER_ERROR.value())

    when:
    provider.disassociateFloatingIp(region, floatingIp)

    then:
    1 * mockClient.networking() >> networkingService
    1 * networkingService.floatingip() >> floatingIPService
    1 * floatingIPService.disassociateFromPort(floatingIp) >> { throw throwable }

    and:
    OpenstackProviderException openstackProviderException = thrown(OpenstackProviderException)
    openstackProviderException.cause == throwable
  }

  def "get associated floating ip success"() {
    setup:
    String vipId = UUID.randomUUID().toString()
    String deviceId = UUID.randomUUID().toString()
    NetworkingService networkingService = Mock()
    PortService portService = Mock()
    Port port = Mock()
    ComputeService computeService = Mock()
    ComputeFloatingIPService floatingIPService = Mock()
    FloatingIP floatingIP = Stub() {
      getInstanceId() >> deviceId
    }

    when:
    FloatingIP result = provider.getAssociatedFloatingIp(region, vipId)

    then:
    1 * mockClient.networking() >> networkingService
    1 * networkingService.port() >> portService
    1 * portService.list() >> [port]
    1 * port.name >> "vip-${vipId}"
    1 * mockClient.compute() >> computeService
    1 * computeService.floatingIps() >> floatingIPService
    1 * floatingIPService.list() >> [floatingIP]
    1 * port.deviceId >> deviceId
    result == floatingIP
    noExceptionThrown()
  }

  def "get associated floating ip - port not found"() {
    setup:
    String vipId = UUID.randomUUID().toString()
    String deviceId = UUID.randomUUID().toString()
    NetworkingService networkingService = Mock()
    PortService portService = Mock()
    Port port = Mock()
    ComputeService computeService = Mock()
    ComputeFloatingIPService floatingIPService = Mock()
    FloatingIP floatingIP = Stub() {
      getInstanceId() >> deviceId
    }

    when:
    provider.getAssociatedFloatingIp(region, vipId)

    then:
    1 * mockClient.networking() >> networkingService
    1 * networkingService.port() >> portService
    1 * portService.list() >> []
    0 * mockClient.compute() >> computeService
    0 * computeService.floatingIps() >> floatingIPService
    0 * floatingIPService.list() >> [floatingIP]
    0 * port.deviceId >> deviceId

    and:
    OpenstackProviderException openstackProviderException = thrown(OpenstackProviderException)
    openstackProviderException.message.contains(vipId)
  }

  def "get associated floating ip - exception"() {
    setup:
    String vipId = UUID.randomUUID().toString()
    String deviceId = UUID.randomUUID().toString()
    NetworkingService networkingService = Mock()
    PortService portService = Mock()
    Port port = Mock()
    ComputeService computeService = Mock()
    ComputeFloatingIPService floatingIPService = Mock()
    Throwable throwable = new ServerResponseException('foo', HttpStatus.INTERNAL_SERVER_ERROR.value())

    when:
    provider.getAssociatedFloatingIp(region, vipId)

    then:
    1 * mockClient.networking() >> networkingService
    1 * networkingService.port() >> portService
    1 * portService.list() >> [port]
    1 * port.name >> "vip-${vipId}"
    1 * mockClient.compute() >> computeService
    1 * computeService.floatingIps() >> floatingIPService
    1 * floatingIPService.list() >> { throw throwable }
    0 * port.deviceId >> deviceId

    and:
    OpenstackProviderException openstackProviderException = thrown(OpenstackProviderException)
    openstackProviderException.cause == throwable
  }

  def "get port for vip found"() {
    setup:
    String vipId = UUID.randomUUID().toString()
    NetworkingService networkingService = Mock()
    PortService portService = Mock()
    Port port = Stub(Port) {
      getName() >> "vip-$vipId"
    }

    when:
    Port result = provider.getPortForVip(region, vipId)

    then:
    1 * mockClient.networking() >> networkingService
    1 * networkingService.port() >> portService
    1 * portService.list() >> [port]
    result == port
    noExceptionThrown()
  }

  def "get port for vip not found"() {
    setup:
    String vipId = UUID.randomUUID().toString()
    NetworkingService networkingService = Mock()
    PortService portService = Mock()
    Port port = Stub(Port) {
      getName() >> "vip-${UUID.randomUUID().toString()}"
    }

    when:
    Port result = provider.getPortForVip(region, vipId)

    then:
    1 * mockClient.networking() >> networkingService
    1 * networkingService.port() >> portService
    1 * portService.list() >> [port]
    result == null
    noExceptionThrown()
  }

  def "get port for vip not found empty list"() {
    setup:
    String vipId = UUID.randomUUID().toString()
    NetworkingService networkingService = Mock()
    PortService portService = Mock()
    Port port = Stub(Port) {
      getName() >> "vip-${UUID.randomUUID().toString()}"
    }

    when:
    Port result = provider.getPortForVip(region, vipId)

    then:
    1 * mockClient.networking() >> networkingService
    1 * networkingService.port() >> portService
    1 * portService.list() >> []
    result == null
    noExceptionThrown()
  }

  def "get port for vip exception"() {
    setup:
    String vipId = UUID.randomUUID().toString()
    NetworkingService networkingService = Mock()
    PortService portService = Mock()
    Throwable throwable = new ServerResponseException('foo', HttpStatus.INTERNAL_SERVER_ERROR.value())

    when:
    provider.getPortForVip(region, vipId)

    then:
    1 * mockClient.networking() >> networkingService
    1 * networkingService.port() >> portService
    1 * portService.list() >> { throw throwable }

    and:
    OpenstackProviderException openstackProviderException = thrown(OpenstackProviderException)
    openstackProviderException.cause == throwable
  }

  def "handle request succeeds"() {
    setup:
    def success = ActionResponse.actionSuccess()

    when:
    def response = provider.handleRequest { success }

    then:
    success == response
    noExceptionThrown()
  }

  def "handle request fails with failed action request"() {
    setup:
    def failed = ActionResponse.actionFailed("foo", 500)

    when:
    provider.handleRequest { failed }

    then:
    Exception ex = thrown(OpenstackProviderException)
    ex.message.contains("foo")
    ex.message.contains("500")
  }

  def "handle request fails with closure throwing exception"() {
    setup:
    def exception = new Exception("foo")

    when:
    provider.handleRequest { throw exception }

    then:
    Exception ex = thrown(OpenstackProviderException)
    ex.cause == exception
    ex.cause.message.contains("foo")
  }

  def "handle request non-action response"() {
    setup:
    def object = new Object()

    when:
    def response = provider.handleRequest { object }

    then:
    object == response
    noExceptionThrown()
  }

  def "handle request null response"() {
    when:
    def response = provider.handleRequest { null }

    then:
    response == null
    noExceptionThrown()
  }

  def "deploy heat stack succeeds"() {
    setup:
    Stack stack = Mock(Stack)
    HeatService heat = Mock(HeatService)
    StackService stackApi = Mock(StackService)
    mockClient.heat() >> heat
    heat.stacks() >> stackApi
    String stackName = "mystack"
    String tmpl = "foo: bar"
    Map<String, String> subtmpl = [sub: "foo: bar"]
    String region = 'region'
    boolean disableRollback = false
    int timeoutMins = 5
    String instanceType = 'm1.small'
    String image = 'ubuntu-latest'
    int maxSize = 5
    int minSize = 3
    String networkId = '1234'
    String poolId = '5678'
    List<String> securityGroups = ['sg1']
    ServerGroupParameters parameters = new ServerGroupParameters(instanceType: instanceType, image: image, maxSize: maxSize, minSize: minSize, networkId: networkId, poolId: poolId, securityGroups: securityGroups)
    Map<String, String> params = [
      'flavor'         : parameters.instanceType,
      'image'          : parameters.image,
      'internal_port'  : "$parameters.internalPort".toString(),
      'max_size'       : "$parameters.maxSize".toString(),
      'min_size'       : "$parameters.minSize".toString(),
      'network_id'     : parameters.networkId,
      'pool_id'        : parameters.poolId,
      'security_groups': parameters.securityGroups.join(',')
    ]
    StackCreate stackCreate = Builders.stack().disableRollback(disableRollback).files(subtmpl).name(stackName).parameters(params).template(tmpl).timeoutMins(timeoutMins).build()

    when:
    provider.deploy(region, stackName, tmpl, subtmpl, parameters, disableRollback, timeoutMins)

    then:
    1 * stackApi.create(_ as StackCreate) >> { StackCreate sc ->
      sc.disableRollback == stackCreate.disableRollback
      sc.name == stackCreate.name
      sc.parameters == stackCreate.parameters
      sc.template == stackCreate.parameters
      stack
    }
    noExceptionThrown()
  }

  def "deploy heat stack fails - exception"() {
    setup:
    HeatService heat = Mock(HeatService)
    StackService stackApi = Mock(StackService)
    mockClient.heat() >> heat
    heat.stacks() >> stackApi
    String stackName = "mystack"
    String tmpl = "foo: bar"
    Map<String, String> subtmpl = [sub: "foo: bar"]
    String region = 'region'
    boolean disableRollback = false
    int timeoutMins = 5
    String instanceType = 'm1.small'
    String image = 'ubuntu-latest'
    int maxSize = 5
    int minSize = 3
    String networkId = '1234'
    String poolId = '5678'
    List<String> securityGroups = ['sg1']
    ServerGroupParameters parameters = new ServerGroupParameters(instanceType: instanceType, image: image, maxSize: maxSize, minSize: minSize, networkId: networkId, poolId: poolId, securityGroups: securityGroups)

    when:
    provider.deploy(region, stackName, tmpl, subtmpl, parameters, disableRollback, timeoutMins)

    then:
    1 * stackApi.create(_ as StackCreate) >> { throw new Exception('foobar') }
    Exception e = thrown(OpenstackProviderException)
    e.message == 'Unable to process request'
    e.cause.message == 'foobar'
  }

  def "get instance ids for stack succeeds"() {
    setup:
    HeatService heat = Mock()
    ResourcesService resourcesService = Mock()
    String id1 = UUID.randomUUID().toString()
    String id2 = UUID.randomUUID().toString()
    Resource r1 = Stub() {
      getPhysicalResourceId() >> id1
      getType() >> "OS::Nova::Server"
    }
    Resource r2 = Stub() {
      getPhysicalResourceId() >> id2
      getType() >> "not:a:server"
    }
    List<? extends Resource> resources = [r1, r2]

    when:
    List<String> result = provider.getInstanceIdsForStack(region, "mystack")

    then:
    1 * mockClient.heat() >> heat
    1 * heat.resources() >> resourcesService
    1 * resourcesService.list("mystack") >> resources
    result == [id1]
    noExceptionThrown()
  }

  def "get instance ids for stack throws exception"() {
    setup:
    HeatService heat = Mock()
    ResourcesService resourcesService = Mock()
    Throwable throwable = new ServerResponseException('foo', HttpStatus.INTERNAL_SERVER_ERROR.value())

    when:
    provider.getInstanceIdsForStack(region, "mystack")

    then:
    1 * mockClient.heat() >> heat
    1 * heat.resources() >> resourcesService
    1 * resourcesService.list("mystack") >> { throw throwable }

    and:
    OpenstackProviderException openstackProviderException = thrown(OpenstackProviderException)
    openstackProviderException.cause == throwable
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
    port << [0, 65536]
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
    Throwable throwable = new Exception("foobar")

    when:
    provider.getLoadBalancerPool(region, lbid)

    then:
    1 * poolService.get(lbid) >> { throw throwable }
    Exception e = thrown(OpenstackProviderException)
    e.cause == throwable
  }

  def "test get load balancer not found"() {
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
    1 * poolService.get(lbid) >> null
    Exception e = thrown(OpenstackProviderException)
    e.message == "Unable to find load balancer ${lbid} in ${region}"
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
    e.message == "Unable to process request"
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
    provider.removeMemberFromLoadBalancerPool(region, memberId)

    then:
    1 * memberService.delete(memberId) >> failure
    Exception e = thrown(OpenstackProviderException)
    e.message.contains('failed')
    e.message.contains('404')
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
    e.message == "Unable to process request"
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
    e.message == "Unable to process request"
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

  def "get heat template succeeds"() {
    setup:
    HeatService heat = Mock()
    TemplateService templateApi = Mock()
    mockClient.useRegion(_ as String).heat() >> heat
    heat.templates() >> templateApi

    when:
    provider.getHeatTemplate("myregion", "mystack", "mystackid")

    then:
    1 * mockClient.useRegion("myregion") >> mockClient
    1 * mockClient.heat() >> heat
    1 * templateApi.getTemplateAsString("mystack", "mystackid")
    noExceptionThrown()
  }

  def "get stack succeeds"() {
    setup:
    String stackName = 'stackofpancakesyumyum'
    HeatService heatService = Mock(HeatService)
    StackService stackService = Mock(StackService)
    mockClient.heat() >> heatService
    heatService.stacks() >> stackService
    Stack mockStack = Mock(Stack)

    when:
    Stack actual = provider.getStack('region1', stackName)

    then:
    1 * stackService.getStackByName(stackName) >> mockStack
    actual == mockStack
  }

  def "get stack does not find stack"() {
    given:
    HeatService heat = Mock()
    StackService stackApi = Mock()
    mockClient.heat() >> heat
    heat.stacks() >> stackApi
    def name = 'mystack'

    when:
    provider.getStack(region, name)

    then:
    1 * stackApi.getStackByName(name) >> null
    def ex = thrown(OpenstackProviderException)
    ex.message.contains(name)
  }

  def "get stack fails - exception"() {
    setup:
    String stackName = 'stackofpancakesyumyum'
    HeatService heatService = Mock(HeatService)
    StackService stackService = Mock(StackService)
    mockClient.heat() >> heatService
    heatService.stacks() >> stackService

    when:
    provider.getStack('region1', stackName)

    then:
    1 * stackService.getStackByName(stackName) >> { throw new Exception('foo') }
    Exception e = thrown(OpenstackProviderException)
    e.cause.message == 'foo'
  }

  def "delete stack succeeds"() {
    setup:
    def success = ActionResponse.actionSuccess()
    String stackName = 'stackofpancakesyumyum'
    String stackId = UUID.randomUUID().toString()
    HeatService heatService = Mock(HeatService)
    StackService stackService = Mock(StackService)
    mockClient.heat() >> heatService
    heatService.stacks() >> stackService
    Stack mockStack = Mock(Stack)
    mockStack.name >> stackName
    mockStack.id >> stackId

    when:
    provider.destroy('region1', mockStack)

    then:
    1 * stackService.delete(stackName, stackId) >> success
  }

  def "delete stack fails - exception"() {
    setup:
    String stackName = 'stackofpancakesyumyum'
    String stackId = UUID.randomUUID().toString()
    HeatService heatService = Mock(HeatService)
    StackService stackService = Mock(StackService)
    mockClient.heat() >> heatService
    heatService.stacks() >> stackService
    Stack mockStack = Mock(Stack)
    mockStack.name >> stackName
    mockStack.id >> stackId

    when:
    provider.destroy('region1', mockStack)

    then:
    1 * stackService.delete(stackName, stackId) >> { throw new Exception('foo') }
    Exception e = thrown(OpenstackProviderException)
    e.cause.message == 'foo'
  }

  def "delete stack fails - failed status"() {
    setup:
    ActionResponse failed = ActionResponse.actionFailed('foo', 400)
    String stackName = 'stackofpancakesyumyum'
    String stackId = UUID.randomUUID().toString()
    HeatService heatService = Mock(HeatService)
    StackService stackService = Mock(StackService)
    mockClient.heat() >> heatService
    heatService.stacks() >> stackService
    Stack mockStack = Mock(Stack)
    mockStack.name >> stackName
    mockStack.id >> stackId

    when:
    provider.destroy('region1', mockStack)

    then:
    1 * stackService.delete(stackName, stackId) >> failed
    Exception e = thrown(OpenstackProviderException)
    e.message == "Action request failed with fault foo and code 400"
  }

  def "resize stack succeeds"() {
    setup:
    ActionResponse success = ActionResponse.actionSuccess()
    String stackName = 'stackofpancakesyumyum'
    String stackId = UUID.randomUUID().toString()
    HeatService heatService = Mock(HeatService)
    StackService stackService = Mock(StackService)
    mockClient.heat() >> heatService
    heatService.stacks() >> stackService
    Stack mockStack = Mock(Stack)
    mockStack.name >> stackName
    mockStack.id >> stackId
    String region = 'r1'
    String instanceType = 'm1.small'
    String image = 'ubuntu-latest'
    int maxSize = 5
    int minSize = 3
    String networkId = '1234'
    String poolId = '5678'
    List<String> securityGroups = ['sg1']
    ServerGroupParameters parameters = new ServerGroupParameters(instanceType: instanceType, image: image, maxSize: maxSize, minSize: minSize, networkId: networkId, poolId: poolId, securityGroups: securityGroups)
    Map<String, String> params = [
      'flavor'         : parameters.instanceType,
      'image'          : parameters.image,
      'internal_port'  : "$parameters.internalPort".toString(),
      'max_size'       : "$parameters.maxSize".toString(),
      'min_size'       : "$parameters.minSize".toString(),
      'network_id'     : parameters.networkId,
      'pool_id'        : parameters.poolId,
      'security_groups': parameters.securityGroups.join(',')
    ]
    String template = "foo: bar"
    Map<String, String> subtmpl = [sub: "foo: bar"]

    when:
    provider.updateStack(region, stackName, stackId, template, subtmpl, parameters)

    then:
    1 * stackService.update(stackName, stackId, _ as StackUpdate) >> { String name, String id, StackUpdate su ->
      name == stackName
      id == stackId
      su.parameters == params
      success
    }
    noExceptionThrown()
  }

  def "resize stack failed - failed status"() {
    setup:
    ActionResponse failed = ActionResponse.actionFailed('ERROR', 500)
    String stackName = 'stackofpancakesyumyum'
    String stackId = UUID.randomUUID().toString()
    HeatService heatService = Mock(HeatService)
    StackService stackService = Mock(StackService)
    mockClient.heat() >> heatService
    heatService.stacks() >> stackService
    Stack mockStack = Mock(Stack)
    mockStack.name >> stackName
    mockStack.id >> stackId
    String region = 'r1'
    String instanceType = 'm1.small'
    String image = 'ubuntu-latest'
    int maxSize = 5
    int minSize = 3
    String networkId = '1234'
    String poolId = '5678'
    List<String> securityGroups = ['sg1']
    ServerGroupParameters parameters = new ServerGroupParameters(instanceType: instanceType, image: image, maxSize: maxSize, minSize: minSize, networkId: networkId, poolId: poolId, securityGroups: securityGroups)
    String template = "foo: bar"
    Map<String, String> subtmpl = [sub: "foo: bar"]

    when:
    provider.updateStack(region, stackName, stackId, template, subtmpl, parameters)

    then:
    1 * stackService.update(stackName, stackId, _ as StackUpdate) >> { String name, String id, StackUpdate su ->
      failed
    }
    thrown(OpenstackProviderException)
  }

  def "resize stack failed - exception thrown"() {
    setup:
    String stackName = 'stackofpancakesyumyum'
    String stackId = UUID.randomUUID().toString()
    HeatService heatService = Mock(HeatService)
    StackService stackService = Mock(StackService)
    mockClient.heat() >> heatService
    heatService.stacks() >> stackService
    Stack mockStack = Mock(Stack)
    mockStack.name >> stackName
    mockStack.id >> stackId
    String region = 'r1'
    String instanceType = 'm1.small'
    String image = 'ubuntu-latest'
    int maxSize = 5
    int minSize = 3
    String networkId = '1234'
    String poolId = '5678'
    List<String> securityGroups = ['sg1']
    ServerGroupParameters parameters = new ServerGroupParameters(instanceType: instanceType, image: image, maxSize: maxSize, minSize: minSize, networkId: networkId, poolId: poolId, securityGroups: securityGroups)
    String template = "foo: bar"
    Map<String, String> subtmpl = [sub: "foo: bar"]

    when:
    provider.updateStack(region, stackName, stackId, template, subtmpl, parameters)

    then:
    1 * stackService.update(stackName, stackId, _ as StackUpdate) >> { throw new Exception('foo') }
    thrown(OpenstackProviderException)
  }


  def "list subnets succeeds"() {
    setup:
    NetworkingService networkingService = Mock(NetworkingService)
    SubnetService subnetService = Mock(SubnetService)
    List<Subnet> mockSubnets = [Mock(Subnet)]

    when:
    List<Subnet> result = provider.listSubnets(region)

    then:
    1 * mockClient.networking() >> networkingService
    1 * networkingService.subnet() >> subnetService
    1 * subnetService.list() >> mockSubnets

    and:
    result == mockSubnets
    noExceptionThrown()
  }

  def "list subnets exception"() {
    setup:
    NetworkingService networkingService = Mock(NetworkingService)
    SubnetService subnetService = Mock(SubnetService)
    Throwable throwable = new ServerResponseException('foo', HttpStatus.INTERNAL_SERVER_ERROR.value())

    when:
    provider.listSubnets(region)

    then:
    1 * mockClient.networking() >> networkingService
    1 * networkingService.subnet() >> subnetService
    1 * subnetService.list() >> { throw throwable }

    and:
    OpenstackProviderException openstackProviderException = thrown(OpenstackProviderException)
    openstackProviderException.cause == throwable
  }

  def "list images succeeds"() {
    setup:
    Map<String, String> filters = null
    ImageService imageService = Mock(ImageService)
    List<Image> images = [Mock(Image)]

    when:
    List<Subnet> result = provider.listImages(region, filters)

    then:
    1 * mockClient.images() >> imageService
    1 * imageService.list(filters) >> images

    and:
    result == images
    noExceptionThrown()
  }

  def "list images exception"() {
    setup:
    Map<String, String> filters = null
    ImageService imageService = Mock(ImageService)
    Throwable throwable = new ServerResponseException('foo', HttpStatus.INTERNAL_SERVER_ERROR.value())

    when:
    provider.listImages(region, filters)

    then:
    1 * mockClient.images() >> imageService
    1 * imageService.list(filters) >> { throw throwable }

    and:
    OpenstackProviderException openstackProviderException = thrown(OpenstackProviderException)
    openstackProviderException.cause == throwable
  }

  def "get instances by server group"() {
    setup:
    ComputeService computeService = Mock(ComputeService)
    ServerService serverService = Mock(ServerService)
    Server server = Mock(Server)
    List<Server> servers = [server]
    String stackId = UUID.randomUUID().toString()
    Map<String, String> metadata = ['metering.stack': stackId]

    when:
    Map<String, List<Server>> result = provider.getInstancesByServerGroup(region)

    then:
    1 * mockClient.compute() >> computeService
    1 * computeService.servers() >> serverService
    1 * serverService.list() >> servers
    1 * server.metadata >> metadata

    and:
    result == [(stackId): servers]
    noExceptionThrown()
  }

  def "get instances by server group exception"() {
    setup:
    ComputeService computeService = Mock(ComputeService)
    ServerService serverService = Mock(ServerService)
    Throwable throwable = new ServerResponseException('foo', HttpStatus.INTERNAL_SERVER_ERROR.value())

    when:
    provider.getInstancesByServerGroup(region)

    then:
    1 * mockClient.compute() >> computeService
    1 * computeService.servers() >> serverService
    1 * serverService.list() >> { throw throwable }

    and:
    OpenstackProviderException openstackProviderException = thrown(OpenstackProviderException)
    openstackProviderException.cause == throwable
  }

  def "list vips success"() {
    setup:
    NetworkingService networkingService = Mock()
    LoadBalancerService loadBalancerService = Mock()
    VipService vipService = Mock()
    List<? extends Vip> vips = Mock()

    when:
    List<? extends LbPool> result = provider.listVips(region)

    then:
    1 * mockClient.networking() >> networkingService
    1 * networkingService.loadbalancers() >> loadBalancerService
    1 * loadBalancerService.vip() >> vipService
    1 * vipService.list() >> vips
    result == vips
    noExceptionThrown()
  }

  def "list vips exception"() {
    setup:
    NetworkingService networkingService = Mock()
    LoadBalancerService loadBalancerService = Mock()
    VipService vipService = Mock()
    Throwable throwable = new ServerResponseException('foo', HttpStatus.INTERNAL_SERVER_ERROR.value())

    when:
    provider.listVips(region)

    then:
    1 * mockClient.networking() >> networkingService
    1 * networkingService.loadbalancers() >> loadBalancerService
    1 * loadBalancerService.vip() >> vipService
    1 * vipService.list() >> { throw throwable }

    and:
    OpenstackProviderException openstackProviderException = thrown(OpenstackProviderException)
    openstackProviderException.cause == throwable
  }

  def "list ports success"() {
    setup:
    NetworkingService networkingService = Mock()
    PortService portService = Mock()
    List<? extends Port> ports = Mock()

    when:
    List<? extends Port> result = provider.listPorts(region)

    then:
    1 * mockClient.networking() >> networkingService
    1 * networkingService.port() >> portService
    1 * portService.list() >> ports
    result == ports
    noExceptionThrown()
  }

  def "list ports exception"() {
    setup:
    NetworkingService networkingService = Mock()
    PortService portService = Mock()
    Throwable throwable = new ServerResponseException('foo', HttpStatus.INTERNAL_SERVER_ERROR.value())

    when:
    provider.listPorts(region)

    then:
    1 * mockClient.networking() >> networkingService
    1 * networkingService.port() >> portService
    1 * portService.list() >> { throw throwable }

    and:
    OpenstackProviderException openstackProviderException = thrown(OpenstackProviderException)
    openstackProviderException.cause == throwable
  }

  def "list floating ips success"() {
    setup:
    ComputeService computeService = Mock()
    ComputeFloatingIPService ipService = Mock()
    List<? extends FloatingIP> ips = Mock()

    when:
    List<? extends FloatingIP> result = provider.listFloatingIps(region)

    then:
    1 * mockClient.compute() >> computeService
    1 * computeService.floatingIps() >> ipService
    1 * ipService.list() >> ips
    result == ips
    noExceptionThrown()
  }

  def "list floating ips exception"() {
    setup:
    ComputeService computeService = Mock()
    ComputeFloatingIPService ipService = Mock()
    Throwable throwable = new ServerResponseException('foo', HttpStatus.INTERNAL_SERVER_ERROR.value())

    when:
    provider.listFloatingIps(region)

    then:
    1 * mockClient.compute() >> computeService
    1 * computeService.floatingIps() >> ipService
    1 * ipService.list() >> { throw throwable }

    and:
    OpenstackProviderException openstackProviderException = thrown(OpenstackProviderException)
    openstackProviderException.cause == throwable
  }

}
