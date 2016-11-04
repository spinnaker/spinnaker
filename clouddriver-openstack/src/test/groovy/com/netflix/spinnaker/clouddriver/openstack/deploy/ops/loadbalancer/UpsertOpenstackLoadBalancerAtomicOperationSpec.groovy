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

import com.netflix.spinnaker.clouddriver.consul.config.ConsulConfig
import com.netflix.spinnaker.clouddriver.data.task.Task
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository
import com.netflix.spinnaker.clouddriver.openstack.client.BlockingStatusChecker
import com.netflix.spinnaker.clouddriver.openstack.client.OpenstackClientProvider
import com.netflix.spinnaker.clouddriver.openstack.client.OpenstackProviderFactory
import com.netflix.spinnaker.clouddriver.openstack.config.OpenstackConfigurationProperties.LbaasConfig
import com.netflix.spinnaker.clouddriver.openstack.deploy.description.loadbalancer.OpenstackLoadBalancerDescription
import com.netflix.spinnaker.clouddriver.openstack.deploy.description.loadbalancer.OpenstackLoadBalancerDescription.Algorithm
import com.netflix.spinnaker.clouddriver.openstack.deploy.description.loadbalancer.OpenstackLoadBalancerDescription.Listener
import com.netflix.spinnaker.clouddriver.openstack.deploy.exception.OpenstackOperationException
import com.netflix.spinnaker.clouddriver.openstack.deploy.exception.OpenstackProviderException
import com.netflix.spinnaker.clouddriver.openstack.deploy.exception.OpenstackResourceNotFoundException
import com.netflix.spinnaker.clouddriver.openstack.domain.HealthMonitor
import com.netflix.spinnaker.clouddriver.openstack.security.OpenstackCredentials
import com.netflix.spinnaker.clouddriver.openstack.security.OpenstackNamedAccountCredentials
import com.netflix.spinnaker.clouddriver.openstack.task.TaskStatusAware
import org.openstack4j.model.compute.FloatingIP
import org.openstack4j.model.network.NetFloatingIP
import org.openstack4j.model.network.Network
import org.openstack4j.model.network.Port
import org.openstack4j.model.network.Subnet
import org.openstack4j.model.network.ext.HealthMonitorType
import org.openstack4j.model.network.ext.HealthMonitorV2
import org.openstack4j.model.network.ext.LbMethod
import org.openstack4j.model.network.ext.LbPoolV2
import org.openstack4j.model.network.ext.LbProvisioningStatus
import org.openstack4j.model.network.ext.ListenerV2
import org.openstack4j.model.network.ext.LoadBalancerV2
import org.openstack4j.openstack.networking.domain.ext.ListItem
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Unroll

@Unroll
class UpsertOpenstackLoadBalancerAtomicOperationSpec extends Specification implements TaskStatusAware {
  OpenstackClientProvider provider
  OpenstackCredentials credentials
  OpenstackLoadBalancerDescription description

  @Shared
  String region = 'region'
  @Shared
  String account = 'test'
  @Shared
  String opName = UPSERT_LOADBALANCER_PHASE
  @Shared
  Throwable openstackProviderException = new OpenstackProviderException('foo')
  @Shared
  BlockingStatusChecker blockingClientAdapter = BlockingStatusChecker.from(60, 5) { true }

  def setupSpec() {
    TaskRepository.threadLocalTask.set(Mock(Task))
  }

  def setup() {
    provider = Mock(OpenstackClientProvider)
    GroovyMock(OpenstackProviderFactory, global: true)
    OpenstackNamedAccountCredentials credz = new OpenstackNamedAccountCredentials("name", "test", "main", "user", "pw", "tenant", "domain", "endpoint", [], false, "", new LbaasConfig(pollTimeout: 60, pollInterval: 5), new ConsulConfig(), null)
    OpenstackProviderFactory.createProvider(credz) >> { provider }
    credentials = new OpenstackCredentials(credz)
    description = new OpenstackLoadBalancerDescription(credentials: credentials, region: region, account: account)
  }

  def "operate - create load balancer"() {
    given:
    description.with {
      name = 'name'
      subnetId = UUID.randomUUID()
      algorithm = Algorithm.ROUND_ROBIN
      listeners = [new Listener(externalPort: 80, externalProtocol: 'HTTP', internalPort: 8080)]
    }

    and:
    def operation = Spy(UpsertOpenstackLoadBalancerAtomicOperation, constructorArgs: [description])
    LoadBalancerV2 loadBalancer = Stub(LoadBalancerV2) {
      getId() >> '123'
      getVipPortId() >> '321'
    }

    when:
    Map result = operation.operate([])

    then:
    1 * operation.validatePeripherals(region, description.subnetId, description.networkId, description.securityGroups) >> {
    }
    1 * operation.createLoadBalancer(region, description.name, description.subnetId) >> loadBalancer
    1 * operation.buildListenerMap(region, loadBalancer) >> [:]
    1 * operation.addListenersAndPools(region, loadBalancer.id, description.name, description.algorithm, _, description.healthMonitor) >> {
    }
    1 * operation.updateFloatingIp(region, description.networkId, loadBalancer.vipPortId)
    1 * operation.updateSecurityGroups(region, loadBalancer.vipPortId, description.securityGroups)
    0 * operation.updateServerGroup(opName, region, loadBalancer.id)

    and:
    result == [(description.region): [id: loadBalancer.id]]
  }

  def "operate - add / remove load balancer listener pools"() {
    given:
    description.with {
      name = 'name'
      id = UUID.randomUUID()
      subnetId = UUID.randomUUID()
      algorithm = Algorithm.ROUND_ROBIN
      listeners = [new Listener(externalPort: 80, externalProtocol: 'HTTP', internalPort: 8080)]
    }

    and:
    def operation = Spy(UpsertOpenstackLoadBalancerAtomicOperation, constructorArgs: [description])
    LoadBalancerV2 loadBalancer = Stub(LoadBalancerV2) {
      getId() >> '123'
      getVipPortId() >> '321'
    }
    ListenerV2 listenerV2 = Mock(ListenerV2)

    when:
    Map result = operation.operate([])

    then:
    0 * operation.validatePeripherals(region, description.subnetId, description.networkId, description.securityGroups) >> {
    }
    1 * provider.getLoadBalancer(region, description.id) >> loadBalancer
    1 * operation.buildListenerMap(region, loadBalancer) >> ['HTTPS:443:HTTPS:8181': listenerV2]
    1 * operation.addListenersAndPools(region, loadBalancer.id, description.name, description.algorithm, _, description.healthMonitor) >> {
    }
    1 * operation.deleteLoadBalancerPeripherals(opName, region, loadBalancer.id, _ as Collection) >> {}
    1 * operation.updateFloatingIp(region, description.networkId, loadBalancer.vipPortId)
    1 * operation.updateSecurityGroups(region, loadBalancer.vipPortId, description.securityGroups)
    1 * operation.updateServerGroup(_ as String, region, loadBalancer.id)

    and:
    result == [(description.region): [id: loadBalancer.id]]
  }

  def "operate - update load balancer listener pools"() {
    given:
    description.with {
      name = 'name'
      id = UUID.randomUUID()
      subnetId = UUID.randomUUID()
      listeners = [new Listener(externalPort: 80, externalProtocol: 'HTTP', internalPort: 8080)]
    }

    and:
    def operation = Spy(UpsertOpenstackLoadBalancerAtomicOperation, constructorArgs: [description])
    LoadBalancerV2 loadBalancer = Stub(LoadBalancerV2) {
      getId() >> '123'
      getVipPortId() >> '321'
    }
    ListenerV2 listenerV2 = Mock(ListenerV2)

    when:
    Map result = operation.operate([])

    then:
    0 * operation.validatePeripherals(region, description.subnetId, description.networkId, description.securityGroups) >> {
    }
    1 * provider.getLoadBalancer(region, description.id) >> loadBalancer
    1 * operation.buildListenerMap(region, loadBalancer) >> ['HTTP:80:8080': listenerV2]
    1 * operation.updateListenersAndPools(region, loadBalancer.id, description.algorithm, _, description.healthMonitor) >> {
    }
    1 * operation.updateFloatingIp(region, description.networkId, loadBalancer.vipPortId)
    1 * operation.updateSecurityGroups(region, loadBalancer.vipPortId, description.securityGroups)
    0 * operation.updateServerGroup(opName, region, loadBalancer.id)

    and:
    result == [(description.region): [id: loadBalancer.id]]
  }

  def "operate - throw exception"() {
    given:
    description.with {
      name = 'name'
      id = UUID.randomUUID()
      subnetId = UUID.randomUUID()
      listeners = [new Listener(externalPort: 80, externalProtocol: 'HTTP', internalPort: 8080)]
    }

    and:
    def operation = Spy(UpsertOpenstackLoadBalancerAtomicOperation, constructorArgs: [description])
    LoadBalancerV2 loadBalancer = Stub(LoadBalancerV2) {
      getId() >> '123'
      getVipPortId() >> '321'
    }
    ListenerV2 listenerV2 = Mock(ListenerV2)

    when:
    operation.operate([])

    then:
    1 * provider.getLoadBalancer(region, description.id) >> loadBalancer
    1 * operation.buildListenerMap(region, loadBalancer) >> ['HTTP:80:8080': listenerV2]
    1 * operation.updateListenersAndPools(region, loadBalancer.id, description.algorithm, _, description.healthMonitor) >> {
      throw openstackProviderException
    }

    and:
    OpenstackOperationException exception = thrown(OpenstackOperationException)
    exception.cause == openstackProviderException
  }

  def "create load balancer"() {
    given:
    String name = 'name'
    String subnetId = UUID.randomUUID()
    LoadBalancerV2 loadBalancer = Mock(LoadBalancerV2) {
      getId() >> '123'
      getProvisioningStatus() >> LbProvisioningStatus.ACTIVE
    }

    and:
    def operation = Spy(UpsertOpenstackLoadBalancerAtomicOperation, constructorArgs: [description])

    when:
    LoadBalancerV2 result = operation.createLoadBalancer(region, name, subnetId)

    then:
    1 * operation.createBlockingActiveStatusChecker(credentials, region) >> blockingClientAdapter
    1 * provider.createLoadBalancer(region, name, _, subnetId) >> loadBalancer

    and:
    result == loadBalancer
  }

  def "create load balancer exception"() {
    given:
    String name = 'name'
    String subnetId = UUID.randomUUID()
    LoadBalancerV2 loadBalancer = Mock(LoadBalancerV2)

    and:
    def operation = Spy(UpsertOpenstackLoadBalancerAtomicOperation, constructorArgs: [description])

    when:
    operation.createLoadBalancer(region, name, subnetId)

    then:
    1 * operation.createBlockingActiveStatusChecker(credentials, region) >> blockingClientAdapter
    1 * provider.createLoadBalancer(region, name, _, subnetId) >> { throw openstackProviderException }

    and:
    thrown(OpenstackProviderException)
  }

  def "no update security groups - #testCase"() {
    given:
    String id = UUID.randomUUID()
    Port port = Mock(Port)

    and:
    def operation = new UpsertOpenstackLoadBalancerAtomicOperation(description)

    when:
    operation.updateSecurityGroups(region, id, [securityGroup])

    then:
    1 * provider.getPort(region, id) >> port
    1 * port.getSecurityGroups() >> [securityGroup]

    where:
    testCase | groups | securityGroup
    'empty'  | [] | '123'
    'equal'  | ['123'] | '123'
  }

  def "update security groups"() {
    given:
    String id = UUID.randomUUID()
    String securityGroup = UUID.randomUUID()
    Port port = Mock(Port)

    and:
    def operation = new UpsertOpenstackLoadBalancerAtomicOperation(description)

    when:
    operation.updateSecurityGroups(region, id, [securityGroup])

    then:
    1 * provider.getPort(region, id) >> port
    1 * port.getSecurityGroups() >> []
    1 * provider.updatePort(region, id, [securityGroup])
  }

  def "update floating ip - create new floating ip"() {
    given:
    String networkId = UUID.randomUUID()
    String portId = UUID.randomUUID()
    NetFloatingIP netFloatingIP = null
    Network network = Mock(Network)
    FloatingIP floatingIp = Mock(FloatingIP)

    and:
    def operation = new UpsertOpenstackLoadBalancerAtomicOperation(description)

    when:
    operation.updateFloatingIp(region, networkId, portId)

    then:
    1 * provider.getFloatingIpForPort(region, portId) >> netFloatingIP
    1 * provider.getNetwork(region, networkId) >> network
    1 * provider.getOrCreateFloatingIp(region, network.name) >> floatingIp
    1 * provider.associateFloatingIpToPort(region, floatingIp.id, portId)
  }

  def "update floating ip - remove floating ip"() {
    given:
    String networkId = null
    String portId = UUID.randomUUID()
    NetFloatingIP netFloatingIP = Mock(NetFloatingIP)

    and:
    def operation = new UpsertOpenstackLoadBalancerAtomicOperation(description)

    when:
    operation.updateFloatingIp(region, networkId, portId)

    then:
    1 * provider.getFloatingIpForPort(region, portId) >> netFloatingIP
    1 * provider.disassociateFloatingIpFromPort(region, netFloatingIP.id)
  }

  def "update floating ip - already exists"() {
    given:
    String networkId = UUID.randomUUID()
    String portId = UUID.randomUUID()
    NetFloatingIP netFloatingIP = Mock(NetFloatingIP)
    Network network = Mock(Network)
    FloatingIP floatingIp = Mock(FloatingIP)

    and:
    def operation = new UpsertOpenstackLoadBalancerAtomicOperation(description)

    when:
    operation.updateFloatingIp(region, networkId, portId)

    then:
    1 * provider.getFloatingIpForPort(region, portId) >> netFloatingIP
    1 * provider.getNetwork(region, networkId) >> network
    1 * provider.getOrCreateFloatingIp(region, network.name) >> floatingIp
  }

  def "update floating ip - network changed"() {
    given:
    String networkId = UUID.randomUUID()
    String portId = UUID.randomUUID()
    NetFloatingIP netFloatingIP = Mock(NetFloatingIP) {
      getFloatingNetworkId() >> { UUID.randomUUID() }
    }
    Network network = Mock(Network)
    FloatingIP floatingIp = Mock(FloatingIP)

    and:
    def operation = new UpsertOpenstackLoadBalancerAtomicOperation(description)

    when:
    operation.updateFloatingIp(region, networkId, portId)

    then:
    1 * provider.getFloatingIpForPort(region, portId) >> netFloatingIP
    1 * provider.getNetwork(region, networkId) >> network
    1 * provider.getOrCreateFloatingIp(region, network.name) >> floatingIp
    1 * provider.disassociateFloatingIpFromPort(region, netFloatingIP.id)
    1 * provider.associateFloatingIpToPort(region, floatingIp.id, portId)
  }

  def "add listeners and pools"() {
    given:
    String name = 'name'
    Algorithm algorithm = Algorithm.ROUND_ROBIN
    String loadBalancerId = UUID.randomUUID()
    String key = 'HTTP:80:8080'

    and:
    Listener listener = new Listener(externalProtocol: 'HTTP', externalPort: 80, internalPort: 8080)
    ListenerV2 newListener = Mock(ListenerV2)
    LbPoolV2 newLbPool = Mock(LbPoolV2)
    HealthMonitor healthMonitor = Mock(HealthMonitor)

    and:
    def operation = Spy(UpsertOpenstackLoadBalancerAtomicOperation, constructorArgs: [description])

    when:
    operation.addListenersAndPools(region, loadBalancerId, name, algorithm, [(key): listener], healthMonitor)

    then:
    1 * operation.createBlockingActiveStatusChecker(credentials, region, loadBalancerId) >> blockingClientAdapter
    1 * provider.createListener(region, name, listener.externalProtocol.name(), listener.externalPort, key, loadBalancerId) >> newListener
    //todo: is this right? just doing listener.externalProtocol.name()
    1 * provider.createPool(region, name, listener.externalProtocol.name(), algorithm.name(), newListener.id) >> newLbPool
    1 * operation.updateHealthMonitor(region, loadBalancerId, newLbPool, healthMonitor) >> {}
  }

  def "update listeners and pools - change algorithm"() {
    given:
    Algorithm algorithm = Algorithm.ROUND_ROBIN
    String loadBalancerId = UUID.randomUUID()
    ListenerV2 listener = Mock(ListenerV2) {
      getId() >> '123'
    }
    HealthMonitor healthMonitor = Mock(HealthMonitor)
    String poolId = UUID.randomUUID()
    LbPoolV2 lbPool = Mock(LbPoolV2)

    and:
    def operation = Spy(UpsertOpenstackLoadBalancerAtomicOperation, constructorArgs: [description])

    when:
    operation.updateListenersAndPools(region, loadBalancerId, algorithm, [listener], healthMonitor)

    then:
    1 * operation.createBlockingActiveStatusChecker(credentials, region, loadBalancerId) >> blockingClientAdapter
    _ * listener.defaultPoolId >> poolId
    1 * provider.getPool(region, poolId) >> lbPool
    _ * lbPool.lbMethod >> LbMethod.LEAST_CONNECTIONS
    1 * provider.updatePool(region, lbPool.id, algorithm.name()) >> lbPool
    1 * operation.updateHealthMonitor(region, loadBalancerId, lbPool, healthMonitor) >> {}
  }

  def "update listeners and pools - no updates"() {
    given:
    Algorithm algorithm = Algorithm.ROUND_ROBIN
    String loadBalancerId = UUID.randomUUID()
    ListenerV2 listener = Mock(ListenerV2) {
      getId() >> '123'
    }
    HealthMonitor healthMonitor = Mock(HealthMonitor)
    String poolId = UUID.randomUUID()
    LbPoolV2 lbPool = Mock(LbPoolV2)

    and:
    def operation = Spy(UpsertOpenstackLoadBalancerAtomicOperation, constructorArgs: [description])

    when:
    operation.updateListenersAndPools(region, loadBalancerId, algorithm, [listener], healthMonitor)

    then:
    1 * operation.createBlockingActiveStatusChecker(credentials, region, loadBalancerId) >> blockingClientAdapter
    _ * listener.defaultPoolId >> poolId
    1 * provider.getPool(region, poolId) >> lbPool
    _ * lbPool.lbMethod >> LbMethod.ROUND_ROBIN
    0 * provider.updatePool(region, lbPool.id, algorithm.name()) >> lbPool
    1 * operation.updateHealthMonitor(region, loadBalancerId, lbPool, healthMonitor) >> {}
  }

  def "update health monitor"() {
    given:
    String loadBalancerId = UUID.randomUUID()
    LbPoolV2 lbPool = Mock(LbPoolV2)
    HealthMonitor healthMonitor = Mock(HealthMonitor)
    String healthMonitorId = UUID.randomUUID()
    HealthMonitorV2 healthMonitorV2 = Mock(HealthMonitorV2)

    and:
    def operation = Spy(UpsertOpenstackLoadBalancerAtomicOperation, constructorArgs: [description])

    when:
    operation.updateHealthMonitor(region, loadBalancerId, lbPool, healthMonitor)

    then:
    1 * operation.createBlockingActiveStatusChecker(credentials, region, loadBalancerId) >> blockingClientAdapter
    _ * lbPool.healthMonitorId >> healthMonitorId
    1 * provider.getMonitor(region, healthMonitorId) >> healthMonitorV2
    1 * healthMonitorV2.type >> HealthMonitorType.PING
    1 * healthMonitor.type >> HealthMonitor.HealthMonitorType.PING
    1 * provider.updateMonitor(region, healthMonitorId, healthMonitor)
  }

  def "update health monitor - delete/add"() {
    given:
    String loadBalancerId = UUID.randomUUID()
    LbPoolV2 lbPool = Mock(LbPoolV2)
    HealthMonitor healthMonitor = Mock(HealthMonitor)
    String healthMonitorId = UUID.randomUUID()
    HealthMonitorV2 healthMonitorV2 = Mock(HealthMonitorV2)

    and:
    def operation = Spy(UpsertOpenstackLoadBalancerAtomicOperation, constructorArgs: [description])

    when:
    operation.updateHealthMonitor(region, loadBalancerId, lbPool, healthMonitor)

    then:
    1 * operation.createBlockingActiveStatusChecker(credentials, region, loadBalancerId) >> blockingClientAdapter
    _ * lbPool.healthMonitorId >> healthMonitorId
    1 * provider.getMonitor(region, healthMonitorId) >> healthMonitorV2
    1 * healthMonitorV2.type >> HealthMonitorType.PING
    1 * healthMonitor.type >> HealthMonitor.HealthMonitorType.TCP
    1 * provider.deleteMonitor(region, healthMonitorId)
    1 * provider.createMonitor(region, lbPool.id, healthMonitor)
  }

  def "update health monitor - no monitor"() {
    given:
    String loadBalancerId = UUID.randomUUID()
    LbPoolV2 lbPool = Mock(LbPoolV2)
    HealthMonitor healthMonitor = null
    String healthMonitorId = UUID.randomUUID()


    and:
    def operation = Spy(UpsertOpenstackLoadBalancerAtomicOperation, constructorArgs: [description])

    when:
    operation.updateHealthMonitor(region, loadBalancerId, lbPool, healthMonitor)

    then:
    1 * operation.createBlockingActiveStatusChecker(credentials, region, loadBalancerId) >> blockingClientAdapter
    _ * lbPool.healthMonitorId >> healthMonitorId
    1 * operation.removeHealthMonitor(opName, region, loadBalancerId, healthMonitorId) >> {}
  }

  def "update health monitor - add monitor no existing"() {
    given:
    String loadBalancerId = UUID.randomUUID()
    LbPoolV2 lbPool = Mock(LbPoolV2)
    HealthMonitor healthMonitor = Mock(HealthMonitor)
    String healthMonitorId = null

    and:
    def operation = Spy(UpsertOpenstackLoadBalancerAtomicOperation, constructorArgs: [description])

    when:
    operation.updateHealthMonitor(region, loadBalancerId, lbPool, healthMonitor)

    then:
    1 * operation.createBlockingActiveStatusChecker(credentials, region, loadBalancerId) >> blockingClientAdapter
    _ * lbPool.healthMonitorId >> healthMonitorId
    1 * provider.createMonitor(region, lbPool.id, healthMonitor)
  }

  def "remove health monitor"() {
    given:
    String id = UUID.randomUUID()
    String loadBalancerId = UUID.randomUUID()

    and:
    def operation = Spy(UpsertOpenstackLoadBalancerAtomicOperation, constructorArgs: [description])

    when:
    operation.removeHealthMonitor(opName, region, loadBalancerId, id)

    then:
    1 * operation.createBlockingActiveStatusChecker(credentials, region, loadBalancerId) >> blockingClientAdapter
    1 * provider.deleteMonitor(region, id)
  }

  def "create blocking status checker"() {
    given:
    String loadBalancerId = UUID.randomUUID()

    and:
    def operation = new UpsertOpenstackLoadBalancerAtomicOperation(description)

    when:
    BlockingStatusChecker result = operation.createBlockingActiveStatusChecker(credentials, region, loadBalancerId)

    then:
    result.statusChecker != null
  }

  def "build listener map"() {
    given:
    LoadBalancerV2 loadBalancer = Mock(LoadBalancerV2)
    ListItem listItem = Mock(ListItem)
    String listenerId = UUID.randomUUID()
    ListenerV2 listener = Mock(ListenerV2)
    String desc = 'test'

    and:
    def operation = new UpsertOpenstackLoadBalancerAtomicOperation(description)

    when:
    Map<String, ListenerV2> result = operation.buildListenerMap(region, loadBalancer)

    then:
    1 * loadBalancer.listeners >> [listItem]
    1 * listItem.id >> listenerId
    1 * provider.getListener(region, listenerId) >> listener
    1 * listener.description >> desc

    and:
    result == [(desc): listener]
  }

  def "validatePeripherals success"() {
    given:
    String subnetId = UUID.randomUUID()
    String networkId = UUID.randomUUID()
    String securityGroup = UUID.randomUUID()
    def operation = new UpsertOpenstackLoadBalancerAtomicOperation(description)

    when:
    operation.validatePeripherals(region, subnetId, networkId, [securityGroup])

    then:
    1 * provider.getSubnet(region, subnetId) >> Mock(Subnet)
    1 * provider.getNetwork(region, networkId) >> Mock(Network)
    1 * provider.getSecurityGroup(region, securityGroup)
    noExceptionThrown()
  }

  def "validatePeripherals subnet"() {
    given:
    String subnetId = UUID.randomUUID()
    String networkId = UUID.randomUUID()
    String securityGroup = UUID.randomUUID()
    def operation = new UpsertOpenstackLoadBalancerAtomicOperation(description)

    when:
    operation.validatePeripherals(region, subnetId, networkId, [securityGroup])

    then:
    1 * provider.getSubnet(region, subnetId) >> null
    0 * provider.getNetwork(region, networkId) >> Mock(Network)
    0 * provider.getSecurityGroup(region, securityGroup)

    and:
    thrown(OpenstackResourceNotFoundException)
  }

  def "validatePeripherals network"() {
    given:
    String subnetId = UUID.randomUUID()
    String networkId = UUID.randomUUID()
    String securityGroup = UUID.randomUUID()
    def operation = new UpsertOpenstackLoadBalancerAtomicOperation(description)

    when:
    operation.validatePeripherals(region, subnetId, networkId, [securityGroup])

    then:
    1 * provider.getSubnet(region, subnetId) >> Mock(Subnet)
    1 * provider.getNetwork(region, networkId) >> null
    0 * provider.getSecurityGroup(region, securityGroup)

    and:
    thrown(OpenstackResourceNotFoundException)
  }

  def "validatePeripherals security groups"() {
    given:
    String subnetId = UUID.randomUUID()
    String networkId = UUID.randomUUID()
    String securityGroup = UUID.randomUUID()
    def operation = new UpsertOpenstackLoadBalancerAtomicOperation(description)

    when:
    operation.validatePeripherals(region, subnetId, networkId, [securityGroup])

    then:
    1 * provider.getSubnet(region, subnetId) >> Mock(Subnet)
    1 * provider.getNetwork(region, networkId) >> Mock(Network)
    1 * provider.getSecurityGroup(region, securityGroup) >> { throw new OpenstackResourceNotFoundException('test') }

    and:
    thrown(OpenstackResourceNotFoundException)
  }
}
