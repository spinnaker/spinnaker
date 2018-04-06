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

import com.netflix.spinnaker.clouddriver.model.SecurityGroup
import com.netflix.spinnaker.clouddriver.openstack.deploy.exception.OpenstackProviderException
import com.netflix.spinnaker.clouddriver.openstack.deploy.exception.OpenstackResourceNotFoundException
import org.openstack4j.api.compute.ComputeFloatingIPService
import org.openstack4j.api.compute.ComputeSecurityGroupService
import org.openstack4j.api.compute.ComputeService
import org.openstack4j.api.compute.ServerService
import org.openstack4j.api.exceptions.ServerResponseException
import org.openstack4j.model.common.ActionResponse
import org.openstack4j.model.compute.Address
import org.openstack4j.model.compute.Addresses
import org.openstack4j.model.compute.FloatingIP
import org.openstack4j.model.compute.IPProtocol
import org.openstack4j.model.compute.SecGroupExtension
import org.openstack4j.model.compute.Server
import org.openstack4j.openstack.compute.domain.NovaSecGroupExtension
import org.springframework.http.HttpStatus

class OpenstackComputeV2ProviderSpec extends OpenstackClientProviderSpec {

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
    SecurityGroup actual = provider.getSecurityGroup(region, id)

    then:
    !actual
    noExceptionThrown()
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
    address.version >> 4
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
