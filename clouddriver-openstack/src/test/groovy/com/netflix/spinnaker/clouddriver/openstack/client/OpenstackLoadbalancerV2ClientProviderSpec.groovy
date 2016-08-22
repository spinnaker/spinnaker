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

import com.netflix.spinnaker.clouddriver.openstack.deploy.description.loadbalancer.OpenstackLoadBalancerDescription.Algorithm
import com.netflix.spinnaker.clouddriver.openstack.deploy.description.loadbalancer.OpenstackLoadBalancerDescription.Listener
import com.netflix.spinnaker.clouddriver.openstack.deploy.description.loadbalancer.OpenstackLoadBalancerDescription.Listener.ListenerType
import com.netflix.spinnaker.clouddriver.openstack.domain.HealthMonitor
import com.netflix.spinnaker.clouddriver.openstack.deploy.exception.OpenstackProviderException
import org.openstack4j.api.exceptions.ServerResponseException
import org.openstack4j.api.networking.NetworkingService
import org.openstack4j.api.networking.ext.HealthMonitorV2Service
import org.openstack4j.api.networking.ext.LbPoolV2Service
import org.openstack4j.api.networking.ext.LbaasV2Service
import org.openstack4j.api.networking.ext.ListenerV2Service
import org.openstack4j.api.networking.ext.LoadBalancerV2Service
import org.openstack4j.model.common.ActionResponse
import org.openstack4j.model.network.ext.HealthMonitorV2
import org.openstack4j.model.network.ext.HealthMonitorV2Update
import org.openstack4j.model.network.ext.LbMethod
import org.openstack4j.model.network.ext.LbPoolV2
import org.openstack4j.model.network.ext.LbPoolV2Update
import org.openstack4j.model.network.ext.ListenerV2
import org.openstack4j.model.network.ext.LoadBalancerV2
import org.springframework.http.HttpStatus
import spock.lang.Shared

class OpenstackLoadbalancerV2ClientProviderSpec extends OpenstackClientProviderSpec {

  @Shared
  Throwable throwable = new ServerResponseException('foo', HttpStatus.INTERNAL_SERVER_ERROR.value())

  def "create load balancer success"() {
    setup:
    NetworkingService networkingService = Mock(NetworkingService)
    LbaasV2Service lbaasV2Service = Mock(LbaasV2Service)
    LoadBalancerV2Service loadBalancerV2Service = Mock(LoadBalancerV2Service)
    LoadBalancerV2 expected = Mock(LoadBalancerV2)

    when:
    LoadBalancerV2 result = provider.createLoadBalancer(region, 'name', 'desc', UUID.randomUUID().toString())

    then:
    1 * mockClient.networking() >> networkingService
    1 * networkingService.lbaasV2() >> lbaasV2Service
    1 * lbaasV2Service.loadbalancer() >> loadBalancerV2Service
    1 * loadBalancerV2Service.create(_ as LoadBalancerV2) >> expected

    and:
    result == expected
    noExceptionThrown()
  }

  def "create load balancer exception"() {
    setup:
    NetworkingService networkingService = Mock(NetworkingService)
    LbaasV2Service lbaasV2Service = Mock(LbaasV2Service)
    LoadBalancerV2Service loadBalancerV2Service = Mock(LoadBalancerV2Service)

    when:
    provider.createLoadBalancer(region, 'name', 'desc', UUID.randomUUID().toString())

    then:
    1 * mockClient.networking() >> networkingService
    1 * networkingService.lbaasV2() >> lbaasV2Service
    1 * lbaasV2Service.loadbalancer() >> loadBalancerV2Service
    1 * loadBalancerV2Service.create(_ as LoadBalancerV2) >> { throw throwable }

    and:
    OpenstackProviderException openstackProviderException = thrown(OpenstackProviderException)
    openstackProviderException.cause == throwable
  }

  def "get load balancer success"() {
    setup:
    String id = UUID.randomUUID().toString()

    and:
    NetworkingService networkingService = Mock(NetworkingService)
    LbaasV2Service lbaasV2Service = Mock(LbaasV2Service)
    LoadBalancerV2Service loadBalancerV2Service = Mock(LoadBalancerV2Service)
    LoadBalancerV2 expected = Mock(LoadBalancerV2)

    when:
    LoadBalancerV2 result = provider.getLoadBalancer(region, id)

    then:
    1 * mockClient.networking() >> networkingService
    1 * networkingService.lbaasV2() >> lbaasV2Service
    1 * lbaasV2Service.loadbalancer() >> loadBalancerV2Service
    1 * loadBalancerV2Service.get(id) >> expected

    and:
    result == expected
    noExceptionThrown()
  }

  def "get load balancer not found"() {
    setup:
    String id = UUID.randomUUID().toString()

    and:
    NetworkingService networkingService = Mock(NetworkingService)
    LbaasV2Service lbaasV2Service = Mock(LbaasV2Service)
    LoadBalancerV2Service loadBalancerV2Service = Mock(LoadBalancerV2Service)
    LoadBalancerV2 expected = null

    when:
    provider.getLoadBalancer(region, id)

    then:
    1 * mockClient.networking() >> networkingService
    1 * networkingService.lbaasV2() >> lbaasV2Service
    1 * lbaasV2Service.loadbalancer() >> loadBalancerV2Service
    1 * loadBalancerV2Service.get(id) >> expected

    and:
    thrown(OpenstackProviderException)
  }

  def "get load balancer exception"() {
    setup:
    String id = UUID.randomUUID().toString()

    and:
    NetworkingService networkingService = Mock(NetworkingService)
    LbaasV2Service lbaasV2Service = Mock(LbaasV2Service)
    LoadBalancerV2Service loadBalancerV2Service = Mock(LoadBalancerV2Service)

    when:
    provider.getLoadBalancer(region, id)

    then:
    1 * mockClient.networking() >> networkingService
    1 * networkingService.lbaasV2() >> lbaasV2Service
    1 * lbaasV2Service.loadbalancer() >> loadBalancerV2Service
    1 * loadBalancerV2Service.get(id) >> { throw throwable }

    and:
    OpenstackProviderException openstackProviderException = thrown(OpenstackProviderException)
    openstackProviderException.cause == throwable
  }

  def "create listener success"() {
    setup:
    String name = 'name'
    String externalProtocol = Listener.ListenerType.HTTP.toString()
    Integer externalPort = 80
    String description = 'HTTP:80:HTTP:8080'
    String loadBalancerId = UUID.randomUUID().toString()

    and:
    NetworkingService networkingService = Mock(NetworkingService)
    LbaasV2Service lbaasV2Service = Mock(LbaasV2Service)
    ListenerV2Service listenerV2Service = Mock(ListenerV2Service)
    ListenerV2 expected = Mock(ListenerV2)

    when:
    ListenerV2 result = provider.createListener(region, name, externalProtocol, externalPort, description, loadBalancerId)

    then:
    1 * mockClient.networking() >> networkingService
    1 * networkingService.lbaasV2() >> lbaasV2Service
    1 * lbaasV2Service.listener() >> listenerV2Service
    1 * listenerV2Service.create(_ as ListenerV2) >> expected

    and:
    result == expected
    noExceptionThrown()
  }

  def "create listener exception"() {
    setup:
    String name = 'name'
    String externalProtocol = Listener.ListenerType.HTTP.toString()
    Integer externalPort = 80
    String description = 'HTTP:80:HTTP:8080'
    String loadBalancerId = UUID.randomUUID().toString()

    and:
    NetworkingService networkingService = Mock(NetworkingService)
    LbaasV2Service lbaasV2Service = Mock(LbaasV2Service)
    ListenerV2Service listenerV2Service = Mock(ListenerV2Service)

    when:
    provider.createListener(region, name, externalProtocol, externalPort, description, loadBalancerId)

    then:
    1 * mockClient.networking() >> networkingService
    1 * networkingService.lbaasV2() >> lbaasV2Service
    1 * lbaasV2Service.listener() >> listenerV2Service
    1 * listenerV2Service.create(_ as ListenerV2) >> { throw throwable }

    and:
    OpenstackProviderException openstackProviderException = thrown(OpenstackProviderException)
    openstackProviderException.cause == throwable
  }

  def "get listener success"() {
    setup:
    String id = UUID.randomUUID().toString()

    and:
    NetworkingService networkingService = Mock(NetworkingService)
    LbaasV2Service lbaasV2Service = Mock(LbaasV2Service)
    ListenerV2Service listenerV2Service = Mock(ListenerV2Service)
    ListenerV2 expected = Mock(ListenerV2)

    when:
    ListenerV2 result = provider.getListener(region, id)

    then:
    1 * mockClient.networking() >> networkingService
    1 * networkingService.lbaasV2() >> lbaasV2Service
    1 * lbaasV2Service.listener() >> listenerV2Service
    1 * listenerV2Service.get(id) >> expected

    and:
    result == expected
    noExceptionThrown()
  }

  def "get listener not found"() {
    setup:
    String id = UUID.randomUUID().toString()

    and:
    NetworkingService networkingService = Mock(NetworkingService)
    LbaasV2Service lbaasV2Service = Mock(LbaasV2Service)
    ListenerV2Service listenerV2Service = Mock(ListenerV2Service)
    ListenerV2 expected = null

    when:
    provider.getListener(region, id)

    then:
    1 * mockClient.networking() >> networkingService
    1 * networkingService.lbaasV2() >> lbaasV2Service
    1 * lbaasV2Service.listener() >> listenerV2Service
    1 * listenerV2Service.get(id) >> expected

    and:
    thrown(OpenstackProviderException)
  }

  def "get listener exception"() {
    setup:
    String id = UUID.randomUUID().toString()

    and:
    NetworkingService networkingService = Mock(NetworkingService)
    LbaasV2Service lbaasV2Service = Mock(LbaasV2Service)
    ListenerV2Service listenerV2Service = Mock(ListenerV2Service)

    when:
    provider.getListener(region, id)

    then:
    1 * mockClient.networking() >> networkingService
    1 * networkingService.lbaasV2() >> lbaasV2Service
    1 * lbaasV2Service.listener() >> listenerV2Service
    1 * listenerV2Service.get(id) >> { throw throwable }

    and:
    OpenstackProviderException openstackProviderException = thrown(OpenstackProviderException)
    openstackProviderException.cause == throwable
  }

  def "delete listener success"() {
    setup:
    String id = UUID.randomUUID().toString()

    and:
    NetworkingService networkingService = Mock(NetworkingService)
    LbaasV2Service lbaasV2Service = Mock(LbaasV2Service)
    ListenerV2Service listenerV2Service = Mock(ListenerV2Service)
    ActionResponse expected = ActionResponse.actionSuccess()

    when:
    ActionResponse result = provider.deleteListener(region, id)

    then:
    1 * mockClient.networking() >> networkingService
    1 * networkingService.lbaasV2() >> lbaasV2Service
    1 * lbaasV2Service.listener() >> listenerV2Service
    1 * listenerV2Service.delete(id) >> expected

    and:
    result == expected
    noExceptionThrown()
  }

  def "delete listener exception"() {
    setup:
    String id = UUID.randomUUID().toString()

    and:
    NetworkingService networkingService = Mock(NetworkingService)
    LbaasV2Service lbaasV2Service = Mock(LbaasV2Service)
    ListenerV2Service listenerV2Service = Mock(ListenerV2Service)
    ActionResponse expected = ActionResponse.actionFailed('failed', 404)

    when:
    provider.deleteListener(region, id)

    then:
    1 * mockClient.networking() >> networkingService
    1 * networkingService.lbaasV2() >> lbaasV2Service
    1 * lbaasV2Service.listener() >> listenerV2Service
    1 * listenerV2Service.delete(id) >> expected

    and:
    Exception e = thrown(OpenstackProviderException)
    [String.valueOf(expected.code), expected.fault].every { e.message.contains(it) }
  }

  def "create pool success"() {
    setup:
    String name = 'name'
    String internalProtocol = ListenerType.HTTP.toString()
    String listenerId = UUID.randomUUID().toString()
    String algorithm = Algorithm.ROUND_ROBIN.name()

    and:
    NetworkingService networkingService = Mock(NetworkingService)
    LbaasV2Service lbaasV2Service = Mock(LbaasV2Service)
    LbPoolV2Service lbPoolV2Service = Mock(LbPoolV2Service)
    LbPoolV2 expected = Mock(LbPoolV2)

    when:
    LbPoolV2 result = provider.createPool(region, name, internalProtocol, algorithm, listenerId)

    then:
    1 * mockClient.networking() >> networkingService
    1 * networkingService.lbaasV2() >> lbaasV2Service
    1 * lbaasV2Service.lbPool() >> lbPoolV2Service
    1 * lbPoolV2Service.create(_ as LbPoolV2) >> expected

    and:
    result == expected
    noExceptionThrown()
  }

  def "create pool exception"() {
    setup:
    String name = 'name'
    String internalProtocol = ListenerType.HTTP.toString()
    String listenerId = UUID.randomUUID().toString()
    String algorithm = Algorithm.ROUND_ROBIN.name()

    and:
    NetworkingService networkingService = Mock(NetworkingService)
    LbaasV2Service lbaasV2Service = Mock(LbaasV2Service)
    LbPoolV2Service lbPoolV2Service = Mock(LbPoolV2Service)

    when:
    provider.createPool(region, name, internalProtocol, algorithm, listenerId)

    then:
    1 * mockClient.networking() >> networkingService
    1 * networkingService.lbaasV2() >> lbaasV2Service
    1 * lbaasV2Service.lbPool() >> lbPoolV2Service
    1 * lbPoolV2Service.create(_ as LbPoolV2) >> { throw throwable }

    and:
    OpenstackProviderException openstackProviderException = thrown(OpenstackProviderException)
    openstackProviderException.cause == throwable
  }

  def "get pool success"() {
    setup:
    String id = UUID.randomUUID().toString()

    and:
    NetworkingService networkingService = Mock(NetworkingService)
    LbaasV2Service lbaasV2Service = Mock(LbaasV2Service)
    LbPoolV2Service lbPoolV2Service = Mock(LbPoolV2Service)
    LbPoolV2 expected = Mock(LbPoolV2)

    when:
    LbPoolV2 result = provider.getPool(region, id)

    then:
    1 * mockClient.networking() >> networkingService
    1 * networkingService.lbaasV2() >> lbaasV2Service
    1 * lbaasV2Service.lbPool() >> lbPoolV2Service
    1 * lbPoolV2Service.get(id) >> expected

    and:
    result == expected
    noExceptionThrown()
  }

  def "get pool not found"() {
    setup:
    String id = UUID.randomUUID().toString()

    and:
    NetworkingService networkingService = Mock(NetworkingService)
    LbaasV2Service lbaasV2Service = Mock(LbaasV2Service)
    LbPoolV2Service lbPoolV2Service = Mock(LbPoolV2Service)
    LbPoolV2 expected = null

    when:
    provider.getPool(region, id)

    then:
    1 * mockClient.networking() >> networkingService
    1 * networkingService.lbaasV2() >> lbaasV2Service
    1 * lbaasV2Service.lbPool() >> lbPoolV2Service
    1 * lbPoolV2Service.get(id) >> expected

    and:
    thrown(OpenstackProviderException)
  }

  def "get pool exception"() {
    setup:
    String id = UUID.randomUUID().toString()

    and:
    NetworkingService networkingService = Mock(NetworkingService)
    LbaasV2Service lbaasV2Service = Mock(LbaasV2Service)
    LbPoolV2Service lbPoolV2Service = Mock(LbPoolV2Service)

    when:
    provider.getPool(region, id)

    then:
    1 * mockClient.networking() >> networkingService
    1 * networkingService.lbaasV2() >> lbaasV2Service
    1 * lbaasV2Service.lbPool() >> lbPoolV2Service
    1 * lbPoolV2Service.get(id) >> { throw throwable }

    and:
    OpenstackProviderException openstackProviderException = thrown(OpenstackProviderException)
    openstackProviderException.cause == throwable
  }

  def "update pool success"() {
    setup:
    String id = UUID.randomUUID().toString()
    String method = LbMethod.ROUND_ROBIN.name()

    and:
    NetworkingService networkingService = Mock(NetworkingService)
    LbaasV2Service lbaasV2Service = Mock(LbaasV2Service)
    LbPoolV2Service lbPoolV2Service = Mock(LbPoolV2Service)
    LbPoolV2 expected = Mock(LbPoolV2)

    when:
    LbPoolV2 result = provider.updatePool(region, id, method)

    then:
    1 * mockClient.networking() >> networkingService
    1 * networkingService.lbaasV2() >> lbaasV2Service
    1 * lbaasV2Service.lbPool() >> lbPoolV2Service
    1 * lbPoolV2Service.update(id, _ as LbPoolV2Update) >> expected

    and:
    result == expected
    noExceptionThrown()
  }

  def "update pool exception"() {
    setup:
    String id = UUID.randomUUID().toString()
    String method = LbMethod.ROUND_ROBIN.name()

    and:
    NetworkingService networkingService = Mock(NetworkingService)
    LbaasV2Service lbaasV2Service = Mock(LbaasV2Service)
    LbPoolV2Service lbPoolV2Service = Mock(LbPoolV2Service)

    when:
    provider.updatePool(region, id, method)

    then:
    1 * mockClient.networking() >> networkingService
    1 * networkingService.lbaasV2() >> lbaasV2Service
    1 * lbaasV2Service.lbPool() >> lbPoolV2Service
    1 * lbPoolV2Service.update(id, _ as LbPoolV2Update) >> { throw throwable }

    and:
    OpenstackProviderException openstackProviderException = thrown(OpenstackProviderException)
    openstackProviderException.cause == throwable
  }

  def "delete pool success"() {
    setup:
    String id = UUID.randomUUID().toString()

    and:
    NetworkingService networkingService = Mock(NetworkingService)
    LbaasV2Service lbaasV2Service = Mock(LbaasV2Service)
    LbPoolV2Service lbPoolV2Service = Mock(LbPoolV2Service)
    ActionResponse expected = ActionResponse.actionSuccess()

    when:
    ActionResponse result = provider.deletePool(region, id)

    then:
    1 * mockClient.networking() >> networkingService
    1 * networkingService.lbaasV2() >> lbaasV2Service
    1 * lbaasV2Service.lbPool() >> lbPoolV2Service
    1 * lbPoolV2Service.delete(id) >> expected

    and:
    result == expected
    noExceptionThrown()
  }

  def "delete pool exception"() {
    setup:
    String id = UUID.randomUUID().toString()

    and:
    NetworkingService networkingService = Mock(NetworkingService)
    LbaasV2Service lbaasV2Service = Mock(LbaasV2Service)
    LbPoolV2Service lbPoolV2Service = Mock(LbPoolV2Service)
    ActionResponse expected = ActionResponse.actionFailed('failed', 404)

    when:
    provider.deletePool(region, id)

    then:
    1 * mockClient.networking() >> networkingService
    1 * networkingService.lbaasV2() >> lbaasV2Service
    1 * lbaasV2Service.lbPool() >> lbPoolV2Service
    1 * lbPoolV2Service.delete(id) >> expected

    and:
    Exception e = thrown(OpenstackProviderException)
    [String.valueOf(expected.code), expected.fault].every { e.message.contains(it) }
  }

  def "create monitor success"() {
    setup:
    String poolId = UUID.randomUUID().toString()
    HealthMonitor healthMonitor = Mock(HealthMonitor)

    and:
    NetworkingService networkingService = Mock(NetworkingService)
    LbaasV2Service lbaasV2Service = Mock(LbaasV2Service)
    HealthMonitorV2Service healthMonitorV2Service = Mock(HealthMonitorV2Service)
    HealthMonitorV2 expected = Mock(HealthMonitorV2)

    when:
    HealthMonitorV2 result = provider.createMonitor(region, poolId, healthMonitor)

    then:
    1 * mockClient.networking() >> networkingService
    1 * networkingService.lbaasV2() >> lbaasV2Service
    1 * lbaasV2Service.healthMonitor() >> healthMonitorV2Service
    1 * healthMonitorV2Service.create(_ as HealthMonitorV2) >> expected

    and:
    result == expected
    noExceptionThrown()
  }

  def "create monitor exception"() {
    setup:
    String poolId = UUID.randomUUID().toString()
    HealthMonitor healthMonitor = Mock(HealthMonitor)

    and:
    NetworkingService networkingService = Mock(NetworkingService)
    LbaasV2Service lbaasV2Service = Mock(LbaasV2Service)
    HealthMonitorV2Service healthMonitorV2Service = Mock(HealthMonitorV2Service)

    when:
    provider.createMonitor(region, poolId, healthMonitor)

    then:
    1 * mockClient.networking() >> networkingService
    1 * networkingService.lbaasV2() >> lbaasV2Service
    1 * lbaasV2Service.healthMonitor() >> healthMonitorV2Service
    1 * healthMonitorV2Service.create(_ as HealthMonitorV2) >> { throw throwable }

    and:
    OpenstackProviderException openstackProviderException = thrown(OpenstackProviderException)
    openstackProviderException.cause == throwable
  }

  def "get monitor success"() {
    setup:
    String id = UUID.randomUUID().toString()

    and:
    NetworkingService networkingService = Mock(NetworkingService)
    LbaasV2Service lbaasV2Service = Mock(LbaasV2Service)
    HealthMonitorV2Service healthMonitorV2Service = Mock(HealthMonitorV2Service)
    HealthMonitorV2 expected = Mock(HealthMonitorV2)

    when:
    HealthMonitorV2 result = provider.getMonitor(region, id)

    then:
    1 * mockClient.networking() >> networkingService
    1 * networkingService.lbaasV2() >> lbaasV2Service
    1 * lbaasV2Service.healthMonitor() >> healthMonitorV2Service
    1 * healthMonitorV2Service.get(id) >> expected

    and:
    result == expected
    noExceptionThrown()
  }

  def "get monitor exception"() {
    setup:
    String id = UUID.randomUUID().toString()

    and:
    NetworkingService networkingService = Mock(NetworkingService)
    LbaasV2Service lbaasV2Service = Mock(LbaasV2Service)
    HealthMonitorV2Service healthMonitorV2Service = Mock(HealthMonitorV2Service)

    when:
    provider.getMonitor(region, id)

    then:
    1 * mockClient.networking() >> networkingService
    1 * networkingService.lbaasV2() >> lbaasV2Service
    1 * lbaasV2Service.healthMonitor() >> healthMonitorV2Service
    1 * healthMonitorV2Service.get(id) >> { throw throwable }

    and:
    OpenstackProviderException openstackProviderException = thrown(OpenstackProviderException)
    openstackProviderException.cause == throwable
  }

  def "update monitor success"() {
    setup:
    String id = UUID.randomUUID().toString()
    HealthMonitor healthMonitor = Mock(HealthMonitor)

    and:
    NetworkingService networkingService = Mock(NetworkingService)
    LbaasV2Service lbaasV2Service = Mock(LbaasV2Service)
    HealthMonitorV2Service healthMonitorV2Service = Mock(HealthMonitorV2Service)
    HealthMonitorV2 expected = Mock(HealthMonitorV2)

    when:
    HealthMonitorV2 result = provider.updateMonitor(region, id, healthMonitor)

    then:
    1 * mockClient.networking() >> networkingService
    1 * networkingService.lbaasV2() >> lbaasV2Service
    1 * lbaasV2Service.healthMonitor() >> healthMonitorV2Service
    1 * healthMonitorV2Service.update(id, _ as HealthMonitorV2Update) >> expected

    and:
    result == expected
    noExceptionThrown()
  }

  def "update monitor exception"() {
    setup:
    String id = UUID.randomUUID().toString()
    HealthMonitor healthMonitor = Mock(HealthMonitor)

    and:
    NetworkingService networkingService = Mock(NetworkingService)
    LbaasV2Service lbaasV2Service = Mock(LbaasV2Service)
    HealthMonitorV2Service healthMonitorV2Service = Mock(HealthMonitorV2Service)

    when:
    provider.updateMonitor(region, id, healthMonitor)

    then:
    1 * mockClient.networking() >> networkingService
    1 * networkingService.lbaasV2() >> lbaasV2Service
    1 * lbaasV2Service.healthMonitor() >> healthMonitorV2Service
    1 * healthMonitorV2Service.update(id, _ as HealthMonitorV2Update) >> { throw throwable }

    and:
    OpenstackProviderException openstackProviderException = thrown(OpenstackProviderException)
    openstackProviderException.cause == throwable
  }

  def "delete monitor success"() {
    setup:
    String id = UUID.randomUUID().toString()

    and:
    NetworkingService networkingService = Mock(NetworkingService)
    LbaasV2Service lbaasV2Service = Mock(LbaasV2Service)
    HealthMonitorV2Service healthMonitorV2Service = Mock(HealthMonitorV2Service)
    ActionResponse expected = ActionResponse.actionSuccess()

    when:
    ActionResponse result = provider.deleteMonitor(region, id)

    then:
    1 * mockClient.networking() >> networkingService
    1 * networkingService.lbaasV2() >> lbaasV2Service
    1 * lbaasV2Service.healthMonitor() >> healthMonitorV2Service
    1 * healthMonitorV2Service.delete(id) >> expected

    and:
    result == expected
    noExceptionThrown()
  }

  def "delete monitor exception"() {
    setup:
    String id = UUID.randomUUID().toString()

    and:
    NetworkingService networkingService = Mock(NetworkingService)
    LbaasV2Service lbaasV2Service = Mock(LbaasV2Service)
    HealthMonitorV2Service healthMonitorV2Service = Mock(HealthMonitorV2Service)
    ActionResponse expected = ActionResponse.actionFailed('failed', 404)

    when:
    provider.deleteMonitor(region, id)

    then:
    1 * mockClient.networking() >> networkingService
    1 * networkingService.lbaasV2() >> lbaasV2Service
    1 * lbaasV2Service.healthMonitor() >> healthMonitorV2Service
    1 * healthMonitorV2Service.delete(id) >> expected

    and:
    Exception e = thrown(OpenstackProviderException)
    [String.valueOf(expected.code), expected.fault].every { e.message.contains(it) }
  }
}
