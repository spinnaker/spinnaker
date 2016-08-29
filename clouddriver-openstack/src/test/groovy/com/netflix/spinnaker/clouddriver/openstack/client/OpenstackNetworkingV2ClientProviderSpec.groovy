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
import org.openstack4j.api.exceptions.ServerResponseException
import org.openstack4j.api.networking.NetFloatingIPService
import org.openstack4j.api.networking.NetworkingService
import org.openstack4j.api.networking.PortService
import org.openstack4j.api.networking.SubnetService
import org.openstack4j.model.network.NetFloatingIP
import org.openstack4j.model.network.Port
import org.openstack4j.model.network.Subnet
import org.openstack4j.openstack.networking.domain.NeutronPort
import org.springframework.http.HttpStatus
import spock.lang.Shared

class OpenstackNetworkingV2ClientProviderSpec extends OpenstackClientProviderSpec {

  @Shared
  Throwable throwable = new ServerResponseException('foo', HttpStatus.INTERNAL_SERVER_ERROR.value())

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


  def "get port success"() {
    setup:
    String id = UUID.randomUUID().toString()

    and:
    NetworkingService networkingService = Mock(NetworkingService)
    PortService portService = Mock(PortService)
    Port expected = Mock(Port)

    when:
    Port result = provider.getPort(region, id)

    then:
    1 * mockClient.networking() >> networkingService
    1 * networkingService.port() >> portService
    1 * portService.get(id) >> expected

    and:
    result == expected
    noExceptionThrown()
  }

  def "get port exception"() {
    setup:
    String id = UUID.randomUUID().toString()

    and:
    NetworkingService networkingService = Mock(NetworkingService)
    PortService portService = Mock(PortService)

    when:
    provider.getPort(region, id)

    then:
    1 * mockClient.networking() >> networkingService
    1 * networkingService.port() >> portService
    1 * portService.get(id) >> { throw throwable }

    and:
    OpenstackProviderException openstackProviderException = thrown(OpenstackProviderException)
    openstackProviderException.cause == throwable
  }

  def "update port success"() {
    setup:
    String id = UUID.randomUUID().toString()
    List<String> groups = ['test']

    and:
    NetworkingService networkingService = Mock(NetworkingService)
    PortService portService = Mock(PortService)
    NeutronPort expected = Mock(NeutronPort)

    when:
    Port result = provider.updatePort(region, id, groups)

    then:
    _ * mockClient.networking() >> networkingService
    _ * networkingService.port() >> portService
    1 * portService.update(_) >> expected

    and:
    result == expected
    noExceptionThrown()
  }

  def "update port exception"() {
    setup:
    String id = UUID.randomUUID().toString()
    List<String> groups = ['test']

    and:
    NetworkingService networkingService = Mock(NetworkingService)
    PortService portService = Mock(PortService)
    NeutronPort expected = Mock(NeutronPort)

    when:
    provider.updatePort(region, id, groups)

    then:
    _ * mockClient.networking() >> networkingService
    _ * networkingService.port() >> portService
    1 * portService.update(_) >> { throw throwable }

    and:
    OpenstackProviderException openstackProviderException = thrown(OpenstackProviderException)
    openstackProviderException.cause == throwable
  }

  def "associate floating ip to port success"() {
    setup:
    String floatingIpId = UUID.randomUUID().toString()
    String portId = UUID.randomUUID().toString()

    and:
    NetworkingService networkingService = Mock(NetworkingService)
    NetFloatingIPService netFloatingIPService = Mock(NetFloatingIPService)
    NetFloatingIP expected = Mock(NetFloatingIP)
    Port port = Mock(Port) { getId() >> portId }

    when:
    NetFloatingIP result = provider.associateFloatingIpToPort(region, floatingIpId, portId)

    then:
    _ * mockClient.networking() >> networkingService
    1 * networkingService.floatingip() >> netFloatingIPService
    1 * netFloatingIPService.associateToPort(floatingIpId, port.id) >> expected

    and:
    result == expected
    noExceptionThrown()
  }

  def "associate floating ip to port exception"() {
    setup:
    String floatingIpId = UUID.randomUUID().toString()
    String portId = UUID.randomUUID().toString()

    and:
    NetworkingService networkingService = Mock(NetworkingService)
    NetFloatingIPService netFloatingIPService = Mock(NetFloatingIPService)
    Port port = Mock(Port) { getId() >> portId }

    when:
    provider.associateFloatingIpToPort(region, floatingIpId, portId)

    then:
    _ * mockClient.networking() >> networkingService
    1 * networkingService.floatingip() >> netFloatingIPService
    1 * netFloatingIPService.associateToPort(floatingIpId, port.id) >> { throw throwable }

    and:
    OpenstackProviderException openstackProviderException = thrown(OpenstackProviderException)
    openstackProviderException.cause == throwable
  }

  def "disassociate floating ip to port success"() {
    setup:
    String floatingIpId = UUID.randomUUID().toString()

    and:
    NetworkingService networkingService = Mock(NetworkingService)
    NetFloatingIPService netFloatingIPService = Mock(NetFloatingIPService)
    NetFloatingIP expected = Mock(NetFloatingIP)

    when:
    NetFloatingIP result = provider.disassociateFloatingIpFromPort(region, floatingIpId)

    then:
    1 * mockClient.networking() >> networkingService
    1 * networkingService.floatingip() >> netFloatingIPService
    1 * netFloatingIPService.disassociateFromPort(floatingIpId) >> expected

    and:
    result == expected
    noExceptionThrown()
  }

  def "disassociate floating ip to port exception"() {
    setup:
    String floatingIpId = UUID.randomUUID().toString()

    and:
    NetworkingService networkingService = Mock(NetworkingService)
    NetFloatingIPService netFloatingIPService = Mock(NetFloatingIPService)

    when:
    provider.disassociateFloatingIpFromPort(region, floatingIpId)

    then:
    1 * mockClient.networking() >> networkingService
    1 * networkingService.floatingip() >> netFloatingIPService
    1 * netFloatingIPService.disassociateFromPort(floatingIpId) >> { throw throwable }

    and:
    OpenstackProviderException openstackProviderException = thrown(OpenstackProviderException)
    openstackProviderException.cause == throwable
  }
}
