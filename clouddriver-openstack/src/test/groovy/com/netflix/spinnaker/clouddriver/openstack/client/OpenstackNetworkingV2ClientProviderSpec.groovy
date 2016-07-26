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
 *  See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.clouddriver.openstack.client

import com.netflix.spinnaker.clouddriver.openstack.deploy.exception.OpenstackProviderException
import com.netflix.spinnaker.clouddriver.openstack.domain.LoadBalancerPool
import com.netflix.spinnaker.clouddriver.openstack.domain.PoolHealthMonitor
import com.netflix.spinnaker.clouddriver.openstack.domain.VirtualIP
import org.openstack4j.api.compute.ComputeFloatingIPService
import org.openstack4j.api.compute.ComputeService
import org.openstack4j.api.exceptions.ServerResponseException
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
import org.openstack4j.model.compute.FloatingIP
import org.openstack4j.model.network.NetFloatingIP
import org.openstack4j.model.network.Port
import org.openstack4j.model.network.Subnet
import org.openstack4j.model.network.ext.HealthMonitor
import org.openstack4j.model.network.ext.LbPool
import org.openstack4j.model.network.ext.Member
import org.openstack4j.model.network.ext.Vip
import org.springframework.http.HttpStatus

class OpenstackNetworkingV2ClientProviderSpec extends OpenstackClientProviderSpec {

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

  def "get associated floating ip success"() {
    setup:
    String portId = UUID.randomUUID().toString()
    NetworkingService networkingService = Mock()
    NetFloatingIPService floatingIPService = Mock()
    NetFloatingIP floatingIP = Stub() {
      getPortId() >> portId
    }

    when:
    NetFloatingIP result = provider.getFloatingIpForPort(region, portId)

    then:
    1 * mockClient.networking() >> networkingService
    1 * networkingService.floatingip() >> floatingIPService
    1 * floatingIPService.list() >> [floatingIP]
    result == floatingIP
    noExceptionThrown()
  }

  def "get associated floating ip - exception"() {
    setup:
    String portId = UUID.randomUUID().toString()
    NetworkingService networkingService = Mock()
    NetFloatingIPService floatingIPService = Mock()
    NetFloatingIP floatingIP = Stub() {
      getPortId() >> portId
    }
    Throwable throwable = new ServerResponseException('foo', HttpStatus.INTERNAL_SERVER_ERROR.value())

    when:
    provider.getFloatingIpForPort(region, portId)

    then:
    1 * mockClient.networking() >> networkingService
    1 * networkingService.floatingip() >> floatingIPService
    1 * floatingIPService.list() >> { throw throwable }

    and:
    OpenstackProviderException openstackProviderException = thrown(OpenstackProviderException)
    openstackProviderException.cause == throwable
  }

  def "list floating ip success"() {
    setup:
    List<NetFloatingIP> ips = [Mock(NetFloatingIP)]
    NetworkingService networkingService = Mock()
    NetFloatingIPService floatingIPService = Mock()

    when:
    List<NetFloatingIP> result = provider.listNetFloatingIps(region)

    then:
    1 * mockClient.networking() >> networkingService
    1 * networkingService.floatingip() >> floatingIPService
    1 * floatingIPService.list() >> ips
    result == ips
    noExceptionThrown()
  }

  def "list floating ip - exception"() {
    setup:
    NetworkingService networkingService = Mock()
    NetFloatingIPService floatingIPService = Mock()
    Throwable throwable = new ServerResponseException('foo', HttpStatus.INTERNAL_SERVER_ERROR.value())

    when:
    provider.listNetFloatingIps(region)

    then:
    1 * mockClient.networking() >> networkingService
    1 * networkingService.floatingip() >> floatingIPService
    1 * floatingIPService.list() >> { throw throwable }

    and:
    OpenstackProviderException openstackProviderException = thrown(OpenstackProviderException)
    openstackProviderException.cause == throwable
  }

}
