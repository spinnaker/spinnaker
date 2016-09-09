/*
 * Copyright 2016 Veritas Technologies LLC.
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

package com.netflix.spinnaker.clouddriver.openstack.deploy.ops.servergroup

import com.netflix.spinnaker.clouddriver.data.task.Task
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository
import com.netflix.spinnaker.clouddriver.openstack.client.OpenstackClientProvider
import com.netflix.spinnaker.clouddriver.openstack.client.OpenstackProviderFactory
import com.netflix.spinnaker.clouddriver.openstack.config.OpenstackConfigurationProperties
import com.netflix.spinnaker.clouddriver.openstack.deploy.description.servergroup.OpenstackServerGroupAtomicOperationDescription
import com.netflix.spinnaker.clouddriver.openstack.deploy.exception.OpenstackOperationException
import com.netflix.spinnaker.clouddriver.openstack.deploy.exception.OpenstackProviderException
import com.netflix.spinnaker.clouddriver.openstack.deploy.exception.OpenstackResourceNotFoundException
import com.netflix.spinnaker.clouddriver.openstack.security.OpenstackCredentials
import com.netflix.spinnaker.clouddriver.openstack.security.OpenstackNamedAccountCredentials
import org.openstack4j.model.compute.Address
import org.openstack4j.model.heat.Stack
import org.openstack4j.model.network.ext.LbProvisioningStatus
import org.openstack4j.model.network.ext.LoadBalancerV2
import org.openstack4j.model.network.ext.LoadBalancerV2StatusTree
import org.openstack4j.model.network.ext.status.LbPoolV2Status
import org.openstack4j.model.network.ext.status.ListenerV2Status
import org.openstack4j.model.network.ext.status.LoadBalancerV2Status
import spock.lang.Specification
import spock.lang.Subject

class EnableOpenstackAtomicOperationSpec extends Specification {

  private static final STACK = "stack"
  private static final REGION = "region"

  def credentials
  def description
  def provider
  def stack

  List<String> ids = ['foo', 'bar']
  List<String> lbIds = ['lb1','lb2']
  String poolId = '1'
  String subnet = '2'
  String listenerId = '3'
  Integer port = 8080
  String ip = '1.2.3.4'

  def setupSpec() {
    TaskRepository.threadLocalTask.set(Mock(Task))
  }

  def setup() {
    provider = Mock(OpenstackClientProvider)
    GroovyMock(OpenstackProviderFactory, global: true)
    OpenstackNamedAccountCredentials creds = new OpenstackNamedAccountCredentials("name", "test", "main", "user", "pw", "tenant", "domain", "endpoint", [], false, "", new OpenstackConfigurationProperties.LbaasConfig(pollTimeout: 60, pollInterval: 5))
    OpenstackProviderFactory.createProvider(creds) >> { provider }
    credentials = new OpenstackCredentials(creds)
    description = new OpenstackServerGroupAtomicOperationDescription(serverGroupName: STACK, region: REGION, credentials: credentials)
    stack = Mock(Stack) {
      it.tags >> { lbIds }
    }
  }

  def "enable stack adds instances to load balancer"() {
    given:
    @Subject def operation = new EnableOpenstackAtomicOperation(description)
    LoadBalancerV2 loadBalancer1 = Mock(LoadBalancerV2) {
      it.id >> { lbIds[0] }
      it.vipSubnetId >> { subnet }
      it.provisioningStatus >> { LbProvisioningStatus.ACTIVE }
    }
    LoadBalancerV2 loadBalancer2 = Mock(LoadBalancerV2) {
      it.id >> { lbIds[1] }
      it.vipSubnetId >> { subnet }
      it.provisioningStatus >> { LbProvisioningStatus.ACTIVE }
    }
    LoadBalancerV2 mockLB = Mock(LoadBalancerV2) {
      it.id >> { _ }
      it.vipSubnetId >> { subnet }
      it.provisioningStatus >> { LbProvisioningStatus.ACTIVE }
    }
    Map<String, LoadBalancerV2> loadBalancers = [(lbIds[0]):loadBalancer1, (lbIds[1]):loadBalancer2]
    LbPoolV2Status pstatus = Mock(LbPoolV2Status) {
      it.id >> { poolId }
    }
    ListenerV2Status lstatus = Mock(ListenerV2Status) {
      it.id >> { listenerId }
      it.lbPoolV2Statuses >> { [pstatus] }
    }
    LoadBalancerV2Status status = Mock(LoadBalancerV2Status) {
      it.listenerStatuses >> { [lstatus] }
    }
    LoadBalancerV2StatusTree tree = Mock(LoadBalancerV2StatusTree) {
      it.loadBalancerV2Status >> { status }
    }
    Address address = Mock(Address) {
      it.addr >> { ip }
      it.version >> { 6 }
    }

    when:
    operation.operate([])

    then:
    1 * provider.getInstanceIdsForStack(description.region, description.serverGroupName) >> ids
    1 * provider.getStack(description.region, description.serverGroupName) >> stack
    ids.each { id ->
      1 * provider.getIpsForInstance(description.region, id) >> [address]
    }
    lbIds.each { lbId ->
      2 * provider.getInternalLoadBalancerPort(description.region, listenerId) >> port
      1 * provider.getLoadBalancer(description.region, lbId) >> loadBalancers[(lbId)]
      1 * provider.getLoadBalancerStatusTree(description.region, lbId) >> tree
      2 * provider.addMemberToLoadBalancerPool(description.region, ip, poolId, subnet, port, AbstractEnableDisableOpenstackAtomicOperation.DEFAULT_WEIGHT)
      _ * provider.getLoadBalancer(description.region, lbId) >> mockLB
    }
    noExceptionThrown()
  }

  def "enable stack does nothing when stack has no instances"() {
    given:
    @Subject def operation = new EnableOpenstackAtomicOperation(description)

    when:
    operation.operate([])

    then:
    1 * provider.getInstanceIdsForStack(description.region, description.serverGroupName) >> []
    0 * provider.getStack(description.region, description.serverGroupName)
    0 * provider.getIpsForInstance(description.region, _ as String)
    0 * provider.getInternalLoadBalancerPort(description.region, listenerId)
    0 * provider.getLoadBalancer(description.region, _ as String)
    0 * provider.getLoadBalancerStatusTree(description.region, _ as String)
    0 * provider.addMemberToLoadBalancerPool(description.region, ip, poolId, subnet, port, AbstractEnableDisableOpenstackAtomicOperation.DEFAULT_WEIGHT)
    noExceptionThrown()
  }

  def "enable stack does nothing when stack has no load balancers"() {
    given:
    @Subject def operation = new EnableOpenstackAtomicOperation(description)
    Stack emptyStack = Mock(Stack) {
      it.tags >> { [] }
    }

    when:
    operation.operate([])

    then:
    1 * provider.getInstanceIdsForStack(description.region, description.serverGroupName) >> ['1','2','3']
    1 * provider.getStack(description.region, description.serverGroupName) >> emptyStack
    0 * provider.getIpsForInstance(description.region, _ as String)
    0 * provider.getInternalLoadBalancerPort(description.region, listenerId)
    0 * provider.getLoadBalancer(description.region, _ as String)
    0 * provider.getLoadBalancerStatusTree(description.region, _ as String)
    0 * provider.addMemberToLoadBalancerPool(description.region, ip, poolId, subnet, port, AbstractEnableDisableOpenstackAtomicOperation.DEFAULT_WEIGHT)
    noExceptionThrown()
  }

  def "stack not found"() {
    given:
    @Subject def operation = new EnableOpenstackAtomicOperation(description)
    Throwable throwable = new OpenstackProviderException("Unable to find stack $description.serverGroupName in region $description.region")

    when:
    operation.operate([])

    then:
    1 * provider.getInstanceIdsForStack(description.region, description.serverGroupName) >> ['1','2','3']
    1 * provider.getStack(description.region, description.serverGroupName) >> { throw throwable }
    0 * provider.getIpsForInstance(description.region, _ as String)
    0 * provider.getInternalLoadBalancerPort(description.region, listenerId)
    0 * provider.getLoadBalancer(description.region, _ as String)
    0 * provider.getLoadBalancerStatusTree(description.region, _ as String)
    0 * provider.addMemberToLoadBalancerPool(description.region, ip, poolId, subnet, port, AbstractEnableDisableOpenstackAtomicOperation.DEFAULT_WEIGHT)
    Throwable actual = thrown(OpenstackOperationException)
    actual.cause == throwable
  }

  def "load balancer not found"() {
    given:
    @Subject def operation = new EnableOpenstackAtomicOperation(description)
    Throwable throwable = new OpenstackResourceNotFoundException("Unable to find load balancer lb1 in ${description.region}")
    LbPoolV2Status pstatus = Mock(LbPoolV2Status) {
      it.id >> { poolId }
    }
    ListenerV2Status lstatus = Mock(ListenerV2Status) {
      it.id >> { listenerId }
      it.lbPoolV2Statuses >> { [pstatus] }
    }
    LoadBalancerV2Status status = Mock(LoadBalancerV2Status) {
      it.listenerStatuses >> { [lstatus] }
    }
    LoadBalancerV2StatusTree tree = Mock(LoadBalancerV2StatusTree) {
      it.loadBalancerV2Status >> { status }
    }
    Address address = Mock(Address) {
      it.addr >> { ip }
    }
    LoadBalancerV2 mockLB = Mock(LoadBalancerV2) {
      it.provisioningStatus >> { LbProvisioningStatus.ACTIVE }
    }

    when:
    operation.operate([])

    then:
    1 * provider.getInstanceIdsForStack(description.region, description.serverGroupName) >> ids
    1 * provider.getStack(description.region, description.serverGroupName) >> stack
    ids.each { id ->
      1 * provider.getIpsForInstance(description.region, id) >> [address]
    }
    lbIds.each { lbId ->
      1 * provider.getLoadBalancer(description.region, lbId) >> { throw throwable }
      1 * provider.getLoadBalancerStatusTree(description.region, lbId) >> tree
      0 * provider.getInternalLoadBalancerPort(description.region, listenerId) >> port
      0 * provider.addMemberToLoadBalancerPool(description.region, ip, poolId, subnet, port, AbstractEnableDisableOpenstackAtomicOperation.DEFAULT_WEIGHT)
    }
    Throwable actual = thrown(OpenstackOperationException)
    actual.cause.cause == throwable
  }

}
