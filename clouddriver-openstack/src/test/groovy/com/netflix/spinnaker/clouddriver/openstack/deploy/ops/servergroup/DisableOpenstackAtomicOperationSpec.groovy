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
import com.netflix.spinnaker.clouddriver.openstack.deploy.description.servergroup.OpenstackServerGroupAtomicOperationDescription
import com.netflix.spinnaker.clouddriver.openstack.deploy.exception.OpenstackOperationException
import com.netflix.spinnaker.clouddriver.openstack.deploy.exception.OpenstackProviderException
import com.netflix.spinnaker.clouddriver.openstack.deploy.exception.OpenstackResourceNotFoundException
import com.netflix.spinnaker.clouddriver.openstack.security.OpenstackCredentials
import com.netflix.spinnaker.clouddriver.openstack.security.OpenstackNamedAccountCredentials
import org.openstack4j.model.heat.Stack
import org.openstack4j.model.network.ext.LoadBalancerV2StatusTree
import org.openstack4j.model.network.ext.status.LbPoolV2Status
import org.openstack4j.model.network.ext.status.ListenerV2Status
import org.openstack4j.model.network.ext.status.LoadBalancerV2Status
import org.openstack4j.model.network.ext.status.MemberV2Status
import spock.lang.Specification
import spock.lang.Subject

class DisableOpenstackAtomicOperationSpec extends Specification {

  private static final STACK = "stack"
  private static final REGION = "region"

  def credentials
  def description
  def provider
  def stack

  List<String> ids = ['foo', 'bar']
  List<String> lbIds = ['lb1','lb2']
  String memberId = '42'
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
    OpenstackNamedAccountCredentials creds = Mock(OpenstackNamedAccountCredentials)
    OpenstackProviderFactory.createProvider(creds) >> { provider }
    credentials = new OpenstackCredentials(creds)
    description = new OpenstackServerGroupAtomicOperationDescription(serverGroupName: STACK, region: REGION, credentials: credentials)
    stack = Mock(Stack) {
      it.tags >> { lbIds }
    }
  }

  def "disable stack removes instances from load balancer"() {
    given:
    @Subject def operation = new DisableOpenstackAtomicOperation(description)
    MemberV2Status mstatus = Mock(MemberV2Status) {
      it.id >> { memberId }
      it.address >> { ip }
    }
    LbPoolV2Status pstatus = Mock(LbPoolV2Status) {
      it.id >> { poolId }
      it.memberStatuses >> { [mstatus] }
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

    when:
    operation.operate([])

    then:
    1 * provider.getInstanceIdsForStack(description.region, description.serverGroupName) >> ids
    1 * provider.getStack(description.region, description.serverGroupName) >> stack
    ids.each { id ->
      1 * provider.getIpForInstance(description.region, id) >> ip
    }
    lbIds.each { lbId ->
      1 * provider.getLoadBalancerStatusTree(description.region, lbId) >> tree
      2 * provider.removeMemberFromLoadBalancerPool(description.region, poolId, memberId)
    }
    noExceptionThrown()
  }

  def "enable stack does nothing when stack has no instances"() {
    given:
    @Subject def operation = new DisableOpenstackAtomicOperation(description)

    when:
    operation.operate([])

    then:
    1 * provider.getInstanceIdsForStack(description.region, description.serverGroupName) >> []
    0 * provider.getStack(description.region, description.serverGroupName)
    0 * provider.getIpForInstance(description.region, _ as String)
    0 * provider.getLoadBalancerStatusTree(description.region, _ as String)
    0 * provider.removeMemberFromLoadBalancerPool(description.region, poolId, memberId)
    noExceptionThrown()
  }

  def "enable stack does nothing when stack has no load balancers"() {
    given:
    @Subject def operation = new DisableOpenstackAtomicOperation(description)
    Stack emptyStack = Mock(Stack) {
      it.tags >> { [] }
    }

    when:
    operation.operate([])

    then:
    1 * provider.getInstanceIdsForStack(description.region, description.serverGroupName) >> ['1','2','3']
    1 * provider.getStack(description.region, description.serverGroupName) >> emptyStack
    0 * provider.getIpForInstance(description.region, _ as String)
    0 * provider.getLoadBalancerStatusTree(description.region, _ as String)
    0 * provider.removeMemberFromLoadBalancerPool(description.region, poolId, memberId)
    noExceptionThrown()
  }

  def "stack not found"() {
    given:
    @Subject def operation = new DisableOpenstackAtomicOperation(description)
    Throwable throwable = new OpenstackProviderException("Unable to find stack $description.serverGroupName in region $description.region")

    when:
    operation.operate([])

    then:
    1 * provider.getInstanceIdsForStack(description.region, description.serverGroupName) >> ['1','2','3']
    1 * provider.getStack(description.region, description.serverGroupName) >> { throw throwable }
    0 * provider.getIpForInstance(description.region, _ as String)
    0 * provider.getLoadBalancerStatusTree(description.region, _ as String)
    0 * provider.removeMemberFromLoadBalancerPool(description.region, poolId, memberId)
    Throwable actual = thrown(OpenstackOperationException)
    actual.cause == throwable
  }

  def "load balancer not found"() {
    given:
    @Subject def operation = new DisableOpenstackAtomicOperation(description)
    Throwable throwable = new OpenstackResourceNotFoundException("Unable to find load balancer lb1 in ${description.region}")

    when:
    operation.operate([])

    then:
    1 * provider.getInstanceIdsForStack(description.region, description.serverGroupName) >> ids
    1 * provider.getStack(description.region, description.serverGroupName) >> stack
    ids.each { id ->
      1 * provider.getIpForInstance(description.region, id) >> ip
    }
    lbIds.each { lbId ->
      1 * provider.getLoadBalancerStatusTree(description.region, lbId) >> { throw throwable }
      0 * provider.removeMemberFromLoadBalancerPool(description.region, poolId, memberId)
    }
    Throwable actual = thrown(OpenstackOperationException)
    actual.cause.cause == throwable
  }

}
