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
 */
package com.netflix.spinnaker.clouddriver.openstack.deploy.ops.loadbalancer

import com.netflix.spinnaker.clouddriver.data.task.Task
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository
import com.netflix.spinnaker.clouddriver.openstack.client.OpenstackClientProvider
import com.netflix.spinnaker.clouddriver.openstack.client.OpenstackProviderFactory
import com.netflix.spinnaker.clouddriver.openstack.deploy.description.loadbalancer.OpenstackLoadBalancerDescription
import com.netflix.spinnaker.clouddriver.openstack.deploy.exception.OpenstackOperationException
import com.netflix.spinnaker.clouddriver.openstack.deploy.exception.OpenstackProviderException
import com.netflix.spinnaker.clouddriver.openstack.domain.LoadBalancerPool
import com.netflix.spinnaker.clouddriver.openstack.domain.PoolHealthMonitor
import com.netflix.spinnaker.clouddriver.openstack.domain.PoolHealthMonitorType
import com.netflix.spinnaker.clouddriver.openstack.domain.VirtualIP
import com.netflix.spinnaker.clouddriver.openstack.security.OpenstackCredentials
import com.netflix.spinnaker.clouddriver.openstack.security.OpenstackNamedAccountCredentials
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperations
import org.openstack4j.model.compute.FloatingIP
import org.openstack4j.model.network.NetFloatingIP
import org.openstack4j.model.network.Network
import org.openstack4j.model.network.Port
import org.openstack4j.model.network.Subnet
import org.openstack4j.model.network.ext.HealthMonitor
import org.openstack4j.model.network.ext.HealthMonitorType
import org.openstack4j.model.network.ext.LbPool
import org.openstack4j.model.network.ext.Vip
import spock.lang.Specification
import spock.lang.Subject

class UpsertOpenstackLoadBalancerAtomicOperationSpec extends Specification {
  def provider
  def credentials
  def description

  def setupSpec() {
    TaskRepository.threadLocalTask.set(Mock(Task))
  }

  def setup() {
    provider = Mock(OpenstackClientProvider)
    GroovyMock(OpenstackProviderFactory, global: true)
    OpenstackNamedAccountCredentials credz = Mock(OpenstackNamedAccountCredentials)
    OpenstackProviderFactory.createProvider(credz) >> { provider }
    credentials = new OpenstackCredentials(credz)
    description = new OpenstackLoadBalancerDescription(credentials: credentials)
  }

  def "should create load balancer"() {
    given:
    description.region = 'west'
    description.subnetId = 'subnetId'
    String newPoolId = UUID.randomUUID().toString()
    @Subject def operation = Spy(UpsertOpenstackLoadBalancerAtomicOperation, constructorArgs: [description])
    LbPool lbPool = Mock(LbPool)

    when:
    Map result = operation.operate([])

    then:
    1 * operation.createLoadBalancer(description.region, description.subnetId, _, _, description.healthMonitor) >> lbPool
    1 * lbPool.id >> newPoolId
    result == [(description.region): [id: newPoolId]]
  }

  def "should create load balancer exception"() {
    given:
    description.region = 'west'
    description.subnetId = 'subnetId'
    @Subject def operation = Spy(UpsertOpenstackLoadBalancerAtomicOperation, constructorArgs: [description])
    Throwable throwable = new OpenstackProviderException('foo')

    when:
    operation.operate([])

    then:
    1 * operation.createLoadBalancer(description.region, description.subnetId, _, _, description.healthMonitor) >> {
      throw throwable
    }
    OpenstackOperationException openstackOperationException = thrown(OpenstackOperationException)
    openstackOperationException.cause == throwable
  }

  def "should update load balancer"() {
    given:
    description.region = 'west'
    description.subnetId = 'subnetId'
    description.id = UUID.randomUUID().toString()
    @Subject def operation = Spy(UpsertOpenstackLoadBalancerAtomicOperation, constructorArgs: [description])
    LbPool existingPool = Mock(LbPool)

    when:
    Map result = operation.operate([])

    then:
    1 * operation.updateLoadBalancer(description.region, _, _, description.healthMonitor) >> existingPool
    1 * existingPool.id >> description.id
    result == [(description.region): [id: description.id]]
  }

  def "should update load balancer exception"() {
    given:
    description.region = 'west'
    description.subnetId = 'subnetId'
    description.id = UUID.randomUUID().toString()
    @Subject def operation = Spy(UpsertOpenstackLoadBalancerAtomicOperation, constructorArgs: [description])
    Throwable throwable = new OpenstackProviderException('foo')

    when:
    operation.operate([])

    then:
    1 * operation.updateLoadBalancer(description.region, _, _, description.healthMonitor) >> { throw throwable }

    and:
    OpenstackOperationException openstackOperationException = thrown(OpenstackOperationException)
    openstackOperationException.cause == throwable
    openstackOperationException.message.contains(AtomicOperations.UPSERT_LOAD_BALANCER)
  }

  def "should create load balancer pool - no network id and health monitor"() {
    given:
    description.region = 'west'
    description.subnetId = 'subnetId'
    @Subject def operation = new UpsertOpenstackLoadBalancerAtomicOperation(description)
    LoadBalancerPool loadBalancerPool = Mock()
    VirtualIP virtualIP = Mock()
    def newLoadBalancerPool = Stub(LbPool) { getId() >> UUID.randomUUID().toString() }
    def newVip = Stub(Vip) { getId() >> UUID.randomUUID().toString() }
    def newFloatingIp = Mock(NetFloatingIP)

    when:
    LbPool result = operation.createLoadBalancer(description.region, description.subnetId, loadBalancerPool, virtualIP, description.healthMonitor)

    then:
    1 * provider.getSubnet(description.region, description.subnetId) >> Mock(Subnet)
    1 * provider.createLoadBalancerPool(description.region, loadBalancerPool) >> newLoadBalancerPool
    1 * provider.createVip(description.region, virtualIP) >> newVip
    0 * provider.createHealthCheckForPool(description.region, newLoadBalancerPool.id, description.healthMonitor)
    0 * provider.getNetwork(description.region, description.networkId)
    0 * provider.getOrCreateFloatingIp(description.region, _)
    0 * provider.associateFloatingIpToVip(description.region, _ , newVip.id) >> newFloatingIp
    result == newLoadBalancerPool
    noExceptionThrown()
  }

  def "should create load balancer and floating IP address - no health monitor"() {
    given:
    description.region = 'west'
    description.subnetId = UUID.randomUUID().toString()
    description.networkId = UUID.randomUUID().toString()
    String ipId = UUID.randomUUID().toString()
    String networkName = 'n1'
    @Subject def operation = new UpsertOpenstackLoadBalancerAtomicOperation(description)
    LoadBalancerPool loadBalancerPool = Mock()
    VirtualIP virtualIP = Mock()
    Network network = Mock(Network) {
      getName() >> networkName
    }
    FloatingIP floatingIP = Mock(FloatingIP) {
      getId() >> ipId
    }
    def newLoadBalancerPool = Stub(LbPool) { getId() >> UUID.randomUUID().toString() }
    def newVip = Stub(Vip) { getId() >> UUID.randomUUID().toString() }

    when:
    LbPool result = operation.createLoadBalancer(description.region, description.subnetId, loadBalancerPool, virtualIP, description.healthMonitor)

    then:
    1 * provider.getSubnet(description.region, description.subnetId) >> Mock(Subnet)
    1 * provider.createLoadBalancerPool(description.region, loadBalancerPool) >> newLoadBalancerPool
    1 * provider.createVip(description.region, virtualIP) >> newVip
    0 * provider.createHealthCheckForPool(description.region, newLoadBalancerPool.id, description.healthMonitor)
    1 * provider.getNetwork(description.region, description.networkId) >> network
    1 * provider.getOrCreateFloatingIp(description.region, network.name) >> floatingIP
    1 * provider.associateFloatingIpToVip(description.region, floatingIP.id, newVip.id) >> Mock(NetFloatingIP)
    result == newLoadBalancerPool
    noExceptionThrown()
  }

  def "should create load balancer, floating IP address and health monitor"() {
    given:
    description.region = 'west'
    description.subnetId = 'subnetId'
    description.healthMonitor = Mock(PoolHealthMonitor)
    description.networkId = UUID.randomUUID().toString()
    String ipId = UUID.randomUUID().toString()
    String networkName = 'n1'
    @Subject def operation = new UpsertOpenstackLoadBalancerAtomicOperation(description)
    LoadBalancerPool loadBalancerPool = Mock()
    VirtualIP virtualIP = Mock()
    Network network = Mock(Network) {
      getName() >> networkName
    }
    FloatingIP floatingIP = Mock(FloatingIP) {
      getId() >> ipId
    }
    def newLoadBalancerPool = Stub(LbPool) { getId() >> UUID.randomUUID().toString() }
    def newVip = Stub(Vip) { getId() >> UUID.randomUUID().toString() }

    when:
    LbPool result = operation.createLoadBalancer(description.region, description.subnetId, loadBalancerPool, virtualIP, description.healthMonitor)

    then:
    1 * provider.getSubnet(description.region, description.subnetId) >> Mock(Subnet)
    1 * provider.createLoadBalancerPool(description.region, loadBalancerPool) >> newLoadBalancerPool
    1 * provider.createVip(description.region, virtualIP) >> newVip
    1 * provider.createHealthCheckForPool(description.region, newLoadBalancerPool.id, description.healthMonitor)
    1 * provider.getNetwork(description.region, description.networkId) >> network
    1 * provider.getOrCreateFloatingIp(description.region, network.name) >> floatingIP
    1 * provider.associateFloatingIpToVip(description.region, floatingIP.id, newVip.id) >> Mock(NetFloatingIP)
    result == newLoadBalancerPool
    noExceptionThrown()
  }

  def "should create load balancer, floating IP address and health monitor - exception"() {
    given:
    description.region = 'west'
    description.subnetId = 'subnetId'
    description.networkId = UUID.randomUUID().toString()
    String ipId = UUID.randomUUID().toString()
    String networkName = 'n1'
    description.healthMonitor = Mock(PoolHealthMonitor)
    LoadBalancerPool loadBalancerPool = Mock()
    VirtualIP virtualIP = Mock()
    @Subject def operation = new UpsertOpenstackLoadBalancerAtomicOperation(description)
    Network network = Mock(Network) {
      getName() >> networkName
    }
    FloatingIP floatingIP = Mock(FloatingIP) {
      getId() >> ipId
    }
    def newLoadBalancerPool = Stub(LbPool) { getId() >> UUID.randomUUID().toString() }
    def newVip = Stub(Vip) { getId() >> UUID.randomUUID().toString() }
    Throwable throwable = new OpenstackProviderException('foo')

    when:
    operation.createLoadBalancer(description.region, description.subnetId, loadBalancerPool, virtualIP, description.healthMonitor)

    then:
    1 * provider.getSubnet(description.region, description.subnetId) >> Mock(Subnet)
    1 * provider.createLoadBalancerPool(description.region, loadBalancerPool) >> newLoadBalancerPool
    1 * provider.createVip(description.region, virtualIP) >> newVip
    1 * provider.createHealthCheckForPool(description.region, newLoadBalancerPool.id, description.healthMonitor)
    1 * provider.getNetwork(description.region, description.networkId) >> network
    1 * provider.getOrCreateFloatingIp(description.region, network.name) >> floatingIP
    1 * provider.associateFloatingIpToVip(description.region, floatingIP.id, newVip.id) >> {
      throw throwable
    }

    and:
    OpenstackProviderException openstackProviderException = thrown(OpenstackProviderException)
    openstackProviderException == throwable
  }

  def "should create load balancer - invalid subnet id"() {
    given:
    description.region = 'west'
    description.subnetId = 'subnetId'
    LoadBalancerPool loadBalancerPool = Mock()
    VirtualIP virtualIP = Mock()
    @Subject def operation = new UpsertOpenstackLoadBalancerAtomicOperation(description)

    when:
    operation.createLoadBalancer(description.region, description.subnetId, loadBalancerPool, virtualIP, description.healthMonitor)

    then:
    1 * provider.getSubnet(description.region, description.subnetId) >> null
    OpenstackOperationException ex = thrown(OpenstackOperationException)
    [AtomicOperations.UPSERT_LOAD_BALANCER, description.region, description.subnetId].every {
      ex.message.contains(it)
    }
  }

  def "should update load balancer pool and vip"() {
    given:
    description.id = UUID.randomUUID().toString()
    description.region = 'west'
    description.name = 'test'
    @Subject def operation = new UpsertOpenstackLoadBalancerAtomicOperation(description)
    LoadBalancerPool loadBalancerPool = Mock()
    VirtualIP virtualIP = Mock()
    LbPool lbPool = Mock()
    Vip vip = Mock()
    Port port = Mock(Port)

    when:
    LbPool result = operation.updateLoadBalancer(description.region, loadBalancerPool, virtualIP, description.healthMonitor)

    then:
    1 * provider.getLoadBalancerPool(description.region, loadBalancerPool.id) >> lbPool
    1 * provider.updateLoadBalancerPool(description.region, loadBalancerPool)
    1 * provider.getVip(description.region, lbPool.vipId) >> vip
    1 * provider.updateVip(description.region, virtualIP)
    1 * provider.getPortForVip(description.region, vip.id) >> port
    result == lbPool
    noExceptionThrown()
  }

  def "should update load balancer pool, vip, add health monitor"() {
    given:
    VirtualIP virtualIP = Mock()
    LbPool lbPool = Mock() {
      getVipId() >> { 'id' }
      getDescription() >> { 'internal_port=8100,created_time=12345678' }
    }
    Vip vip = Mock(Vip) {
      getId() >> { 'id' }
    }
    PoolHealthMonitor poolHealthMonitor = Mock()
    Port port = Mock(Port)

    and:
    LoadBalancerPool loadBalancerPool = new LoadBalancerPool(id: UUID.randomUUID().toString(), name: 'test')
    description.region = 'west'
    description.healthMonitor = poolHealthMonitor
    @Subject def operation = new UpsertOpenstackLoadBalancerAtomicOperation(description)

    when:
    LbPool result = operation.updateLoadBalancer(description.region, loadBalancerPool, virtualIP, description.healthMonitor)

    then:
    1 * provider.getLoadBalancerPool(description.region, loadBalancerPool.id) >> lbPool
    1 * provider.updateLoadBalancerPool(description.region, loadBalancerPool)
    1 * provider.getVip(description.region, lbPool.vipId) >> vip
    1 * provider.updateVip(description.region, virtualIP)
    1 * lbPool.healthMonitors >> []
    1 * provider.createHealthCheckForPool(description.region, lbPool.id, poolHealthMonitor)
    1 * provider.getPortForVip(description.region, vip.id) >> port
    result == lbPool
    noExceptionThrown()
  }

  def "should update load balancer pool, vip, update health monitor"() {
    given:
    @Subject def operation = new UpsertOpenstackLoadBalancerAtomicOperation(description)
    VirtualIP virtualIP = Mock()
    LbPool lbPool = Mock() {
      getVipId() >> { 'id' }
      getDescription() >> { 'internal_port=8100,created_time=12345678' }
    }
    Vip vip = Mock()
    PoolHealthMonitor poolHealthMonitor = Mock()
    def existingMonitor = Stub(HealthMonitor) {
      getType() >> HealthMonitorType.PING
    }
    Port port = Mock(Port)

    and:
    LoadBalancerPool loadBalancerPool = new LoadBalancerPool(id: UUID.randomUUID().toString(), name: 'test')
    description.region = 'west'
    description.healthMonitor = poolHealthMonitor
    String existingHealthMonitorId = UUID.randomUUID().toString()

    when:
    LbPool result = operation.updateLoadBalancer(description.region, loadBalancerPool, virtualIP, description.healthMonitor)

    then:
    1 * provider.getLoadBalancerPool(description.region, loadBalancerPool.id) >> lbPool
    1 * provider.updateLoadBalancerPool(description.region, loadBalancerPool)
    1 * provider.getVip(description.region, lbPool.vipId) >> vip
    1 * provider.updateVip(description.region, virtualIP)
    2 * lbPool.healthMonitors >> [existingHealthMonitorId]
    1 * provider.getHealthMonitor(description.region, existingHealthMonitorId) >> existingMonitor
    1 * poolHealthMonitor.type >> PoolHealthMonitorType.PING
    1 * provider.updateHealthMonitor(description.region, poolHealthMonitor)
    1 * provider.getPortForVip(description.region, vip.id) >> port
    result == lbPool
    noExceptionThrown()
  }

  def "should update load balancer pool, vip, remove existing and add new health monitor"() {
    given:
    @Subject def operation = new UpsertOpenstackLoadBalancerAtomicOperation(description)
    LbPool lbPool = Mock() {
      getVipId() >> { 'id' }
      getDescription() >> { 'internal_port=8100,created_time=12345678' }
    }
    Vip vip = Mock()
    VirtualIP virtualIP = Mock()
    PoolHealthMonitor poolHealthMonitor = Mock()
    def existingMonitor = Stub(HealthMonitor) {
      getType() >> HealthMonitorType.PING
    }
    Port port = Mock(Port)

    and:
    LoadBalancerPool loadBalancerPool = new LoadBalancerPool(id: UUID.randomUUID().toString(), name: 'test')
    description.region = 'west'
    description.healthMonitor = poolHealthMonitor
    String existingHealthMonitorId = UUID.randomUUID().toString()

    when:
    LbPool result = operation.updateLoadBalancer(description.region, loadBalancerPool, virtualIP, description.healthMonitor)

    then:
    1 * provider.getLoadBalancerPool(description.region, loadBalancerPool.id) >> lbPool
    1 * provider.updateLoadBalancerPool(description.region, _)
    1 * provider.getVip(description.region, lbPool.vipId) >> vip
    1 * provider.updateVip(description.region, _)
    2 * lbPool.healthMonitors >> [existingHealthMonitorId]
    1 * provider.getHealthMonitor(description.region, existingHealthMonitorId) >> existingMonitor
    1 * poolHealthMonitor.type >> PoolHealthMonitorType.HTTP
    1 * provider.disassociateAndRemoveHealthMonitor(description.region, lbPool.id, existingHealthMonitorId)
    1 * provider.createHealthCheckForPool(description.region, lbPool.id, poolHealthMonitor)
    1 * provider.getPortForVip(description.region, vip.id) >> port
    result == lbPool
    noExceptionThrown()
  }

  def "should update load balancer pool, vip, remove existing"() {
    given:
    @Subject def operation = new UpsertOpenstackLoadBalancerAtomicOperation(description)
    VirtualIP virtualIP = Mock()
    LbPool lbPool = Mock() {
      getVipId() >> { 'id' }
      getDescription() >> { 'internal_port=8100,created_time=12345678' }
    }
    Vip vip = Mock(Vip) {
      getName() >> { 'newVip' }
      getId() >> { 'id' }
    }
    String portId = UUID.randomUUID().toString()
    Port port = Mock(Port) {
      getId() >> { portId }
    }

    and:
    LoadBalancerPool loadBalancerPool = new LoadBalancerPool(id: UUID.randomUUID().toString(), name: 'test')
    description.region = 'west'
    String existingHealthMonitorId = UUID.randomUUID().toString()

    when:
    LbPool result = operation.updateLoadBalancer(description.region, loadBalancerPool, virtualIP, description.healthMonitor)

    then:
    1 * provider.getLoadBalancerPool(description.region, loadBalancerPool.id) >> lbPool
    1 * provider.updateLoadBalancerPool(description.region, loadBalancerPool)
    1 * provider.getVip(description.region, lbPool.vipId) >> vip
    1 * provider.updateVip(description.region, virtualIP)
    2 * lbPool.healthMonitors >> [existingHealthMonitorId]
    1 * provider.disassociateAndRemoveHealthMonitor(description.region, lbPool.id, existingHealthMonitorId)
    1 * provider.getPortForVip(description.region, vip.id) >> port
    1 * provider.getFloatingIpForPort(description.region, port.id)
    result == lbPool
    noExceptionThrown()
  }

  def "should update load balancer pool and vip - add floating ip"() {
    given:
    @Subject def operation = new UpsertOpenstackLoadBalancerAtomicOperation(description)
    LbPool lbPool = Mock() {
      getVipId() >> { 'id' }
      getDescription() >> { 'internal_port=8100,created_time=12345678' }
    }
    Vip vip = Mock(Vip) {
      getName() >> { 'newVip' }
      getId() >> { 'id' }
    }
    VirtualIP virtualIP = Mock()

    and:
    LoadBalancerPool loadBalancerPool = new LoadBalancerPool(id: UUID.randomUUID().toString(), name: 'test')
    description.region = 'west'
    description.networkId = UUID.randomUUID().toString()
    String ipId = UUID.randomUUID().toString()
    String networkName = 'n1'
    Network network = Mock(Network) {
      getName() >> networkName
    }
    FloatingIP floatingIP = Mock(FloatingIP) {
      getId() >> ipId
    }
    String portId = UUID.randomUUID().toString()
    Port port = Mock(Port) {
      getId() >> { portId }
    }

    when:
    LbPool result = operation.updateLoadBalancer(description.region, loadBalancerPool, virtualIP, description.healthMonitor)

    then:
    1 * provider.getLoadBalancerPool(description.region, loadBalancerPool.id) >> lbPool
    1 * provider.updateLoadBalancerPool(description.region, loadBalancerPool)
    1 * provider.getVip(description.region, lbPool.vipId) >> vip
    1 * provider.updateVip(description.region, virtualIP)
    1 * lbPool.healthMonitors >> []
    1 * provider.getNetwork(description.region, description.networkId) >> network
    1 * provider.getOrCreateFloatingIp(description.region, network.name) >> floatingIP
    1 * provider.getPortForVip(description.region, vip.id) >> port
    1 * provider.getFloatingIpForPort(description.region, port.id) >> null
    1 * provider.associateFloatingIpToVip(description.region, floatingIP.id, vip.id)
    result == lbPool
    noExceptionThrown()
  }

  def "should update load balancer pool and vip - remove and add floating ip"() {
    given:
    @Subject def operation = new UpsertOpenstackLoadBalancerAtomicOperation(description)
    LbPool lbPool = Mock() {
      getVipId() >> { 'id' }
      getDescription() >> { 'internal_port=8100,created_time=12345678' }
    }
    Vip vip = Mock(Vip) {
      getName() >> { 'newVip' }
    }
    VirtualIP virtualIP = Mock()
    String oldIpId = UUID.randomUUID().toString()
    NetFloatingIP oldFloatingIP = Mock(NetFloatingIP) {
      getId() >> oldIpId
    }

    and:
    LoadBalancerPool loadBalancerPool = new LoadBalancerPool(id: UUID.randomUUID().toString(), name: 'test')
    description.region = 'west'
    description.networkId = UUID.randomUUID().toString()
    String ipId = UUID.randomUUID().toString()
    String networkName = 'n1'
    Network network = Mock(Network) {
      getName() >> networkName
    }
    FloatingIP floatingIP = Mock(FloatingIP) {
      getId() >> ipId
    }
    String portId = UUID.randomUUID().toString()
    Port port = Mock(Port) {
      getId() >> { portId }
    }

    when:
    LbPool result = operation.updateLoadBalancer(description.region, loadBalancerPool, virtualIP, description.healthMonitor)

    then:
    1 * provider.getLoadBalancerPool(description.region, loadBalancerPool.id) >> lbPool
    1 * provider.updateLoadBalancerPool(description.region, loadBalancerPool)
    1 * provider.getVip(description.region, lbPool.vipId) >> vip
    1 * provider.updateVip(description.region, virtualIP)
    1 * lbPool.healthMonitors >> []
    1 * provider.getNetwork(description.region, description.networkId) >> network
    1 * provider.getOrCreateFloatingIp(description.region, network.name) >> floatingIP
    1 * provider.getPortForVip(description.region, vip.id) >> port
    1 * provider.getFloatingIpForPort(description.region, port.id) >> oldFloatingIP
    1 * provider.disassociateFloatingIp(description.region, oldFloatingIP.id)
    1 * provider.associateFloatingIpToVip(description.region, floatingIP.id, vip.id)
    result == lbPool
    noExceptionThrown()
  }

  def "should update load balancer pool and vip - remove floating ip"() {
    given:
    @Subject def operation = new UpsertOpenstackLoadBalancerAtomicOperation(description)
    LbPool lbPool = Mock() {
      getVipId() >> { 'id' }
      getDescription() >> { 'internal_port=8100,created_time=12345678' }
    }
    Vip vip = Mock(Vip) {
      getName() >> { 'newVip' }
    }
    NetFloatingIP floatingIP = Mock()
    VirtualIP virtualIP = Mock()
    String portId = UUID.randomUUID().toString()
    Port port = Mock(Port) {
      getId() >> { portId }
    }

    and:
    LoadBalancerPool loadBalancerPool = new LoadBalancerPool(id: UUID.randomUUID().toString(), name: 'test')
    description.region = 'west'

    when:
    LbPool result = operation.updateLoadBalancer(description.region, loadBalancerPool, virtualIP, description.healthMonitor)

    then:
    1 * provider.getLoadBalancerPool(description.region, loadBalancerPool.id) >> lbPool
    1 * provider.updateLoadBalancerPool(description.region, loadBalancerPool)
    1 * provider.getVip(description.region, lbPool.vipId) >> vip
    1 * provider.updateVip(description.region, virtualIP)
    1 * lbPool.healthMonitors >> []
    1 * provider.getPortForVip(description.region, vip.id) >> port
    1 * provider.getFloatingIpForPort(description.region, port.id) >> floatingIP
    1 * provider.disassociateFloatingIp(description.region, floatingIP.id)
    result == lbPool
    noExceptionThrown()
  }

  def "should do no updates"() {
    given:
    @Subject def operation = new UpsertOpenstackLoadBalancerAtomicOperation(description)
    LbPool lbPool = Mock()
    Vip vip = Mock(Vip)
    VirtualIP virtualIP = Mock()
    LoadBalancerPool loadBalancerPool = Mock()
    description.region = 'west'
    String portId = UUID.randomUUID().toString()
    Port port = Mock(Port) {
      getId() >> { portId }
    }

    when:
    LbPool result = operation.updateLoadBalancer(description.region, loadBalancerPool, virtualIP, description.healthMonitor)

    then:
    1 * provider.getLoadBalancerPool(description.region, loadBalancerPool.id) >> lbPool
    1 * loadBalancerPool.equals(lbPool) >> true
    1 * virtualIP.equals(vip) >> true
    0 * provider.updateLoadBalancerPool(description.region, loadBalancerPool)
    1 * provider.getVip(description.region, lbPool.vipId) >> vip
    0 * provider.updateVip(description.region, virtualIP)
    1 * provider.getPortForVip(description.region, vip.id) >> port
    1 * provider.getFloatingIpForPort(description.region, port.id) >> null
    result == lbPool
    noExceptionThrown()
  }

  def "should update load balancer pool - not found"() {
    given:
    @Subject def operation = new UpsertOpenstackLoadBalancerAtomicOperation(description)
    Throwable throwable = new OpenstackProviderException('foo')

    and:
    LoadBalancerPool loadBalancerPool = new LoadBalancerPool(id: UUID.randomUUID().toString(), name: 'name')
    description.region = 'west'

    when:
    operation.updateLoadBalancer(description.region, loadBalancerPool, Mock(VirtualIP), description.healthMonitor)

    then:
    1 * provider.getLoadBalancerPool(description.region, loadBalancerPool.id) >> { throw throwable }

    and:
    OpenstackProviderException openstackProviderException = thrown(OpenstackProviderException)
    openstackProviderException == throwable
  }

  def "should update load balancer pool, vip, add health monitor - exception"() {
    given:
    @Subject def operation = new UpsertOpenstackLoadBalancerAtomicOperation(description)
    LbPool lbPool = Mock() {
      getVipId() >> { 'id' }
      getDescription() >> { 'internal_port=8100,created_time=12345678' }
    }
    Vip vip = Mock()
    VirtualIP virtualIP = Mock()
    PoolHealthMonitor poolHealthMonitor = Mock()
    Throwable throwable = new OpenstackProviderException('foo')

    and:
    LoadBalancerPool loadBalancerPool = new LoadBalancerPool(id: UUID.randomUUID().toString(), name: 'name')
    description.region = 'west'
    description.healthMonitor = poolHealthMonitor

    when:
    operation.updateLoadBalancer(description.region, loadBalancerPool, virtualIP, description.healthMonitor)

    then:
    1 * provider.getLoadBalancerPool(description.region, loadBalancerPool.id) >> lbPool
    1 * provider.updateLoadBalancerPool(description.region, loadBalancerPool)
    1 * provider.getVip(description.region, lbPool.vipId) >> vip
    1 * provider.updateVip(description.region, virtualIP)
    1 * lbPool.healthMonitors >> []
    1 * provider.createHealthCheckForPool(description.region, lbPool.id, poolHealthMonitor) >> { throw throwable }

    and:
    OpenstackProviderException openstackProviderException = thrown(OpenstackProviderException)
    openstackProviderException == throwable
  }
}
