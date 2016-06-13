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
import com.netflix.spinnaker.clouddriver.openstack.deploy.description.loadbalancer.DeleteOpenstackLoadBalancerDescription
import com.netflix.spinnaker.clouddriver.openstack.deploy.exception.OpenstackOperationException
import com.netflix.spinnaker.clouddriver.openstack.deploy.exception.OpenstackProviderException
import com.netflix.spinnaker.clouddriver.openstack.security.OpenstackCredentials
import com.netflix.spinnaker.clouddriver.openstack.security.OpenstackNamedAccountCredentials
import org.openstack4j.model.network.ext.LbPool
import spock.lang.Specification
import spock.lang.Subject

class DeleteOpenstackLoadBalancerAtomicOperationSpec extends Specification {

  private static final String ACCOUNT_NAME = 'myaccount'

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
    description = new DeleteOpenstackLoadBalancerDescription(region: 'region1', id: UUID.randomUUID().toString(), account: ACCOUNT_NAME, credentials: credentials)
  }

  def "should delete load balancer and all components"() {
    given:
    String monitorId = UUID.randomUUID().toString()
    String vipId = UUID.randomUUID().toString()
    @Subject def operation = new DeleteOpenstackLoadBalancerAtomicOperation(description)
    def lbPool = Mock(LbPool)

    when:
    operation.operate([])

    then:
    1 * provider.getLoadBalancerPool(description.region, description.id) >> lbPool
    1 * lbPool.healthMonitors >> [monitorId]
    1 * provider.disassociateAndRemoveHealthMonitor(description.region, description.id, monitorId)
    4 * lbPool.vipId >> vipId
    1 * provider.deleteVip(description.region, vipId)
    1 * provider.deleteLoadBalancerPool(description.region, description.id)
    noExceptionThrown()
  }

  def "should delete load balancer pool - no vip or montiors"() {
    given:
    @Subject def operation = new DeleteOpenstackLoadBalancerAtomicOperation(description)
    def lbPool = Mock(LbPool)

    when:
    operation.operate([])

    then:
    1 * provider.getLoadBalancerPool(description.region, description.id) >> lbPool
    1 * lbPool.healthMonitors >> []
    0 * provider.disassociateAndRemoveHealthMonitor(description.region, description.id, _)
    1 * lbPool.vipId >> null
    0 * provider.deleteVip(description.region, _)
    1 * provider.deleteLoadBalancerPool(description.region, description.id)
    noExceptionThrown()
  }

  def "should delete load balancer pool and vip - no montiors"() {
    given:
    String vipId = UUID.randomUUID().toString()
    @Subject def operation = new DeleteOpenstackLoadBalancerAtomicOperation(description)
    def lbPool = Mock(LbPool)

    when:
    operation.operate([])

    then:
    1 * provider.getLoadBalancerPool(description.region, description.id) >> lbPool
    1 * lbPool.healthMonitors >> []
    0 * provider.disassociateAndRemoveHealthMonitor(description.region, description.id, _)
    4 * lbPool.vipId >> vipId
    1 * provider.deleteVip(description.region, vipId)
    1 * provider.deleteLoadBalancerPool(description.region, description.id)
    noExceptionThrown()
  }

  def "should delete load balancer pool and monitors - no vip"() {
    given:
    String monitorId = UUID.randomUUID().toString()
    @Subject def operation = new DeleteOpenstackLoadBalancerAtomicOperation(description)
    def lbPool = Mock(LbPool)

    when:
    operation.operate([])

    then:
    1 * provider.getLoadBalancerPool(description.region, description.id) >> lbPool
    1 * lbPool.healthMonitors >> [monitorId]
    1 * provider.disassociateAndRemoveHealthMonitor(description.region, description.id, monitorId)
    1 * lbPool.vipId >> null
    0 * provider.deleteVip(description.region, _)
    1 * provider.deleteLoadBalancerPool(description.region, description.id)
    noExceptionThrown()
  }

  def "should not delete load balancer"() {
    given:
    @Subject def operation = new DeleteOpenstackLoadBalancerAtomicOperation(description)
    def lbPool = Mock(LbPool)

    when:
    operation.operate([])

    then:
    1 * provider.getLoadBalancerPool(description.region, description.id) >> null
    0 * lbPool.healthMonitors
    0 * provider.disassociateAndRemoveHealthMonitor(description.region, description.id, _)
    0 * lbPool.vipId
    0 * provider.deleteVip(description.region, _)
    0 * provider.deleteLoadBalancerPool(description.region, description.id)
    noExceptionThrown()
  }

  def "should throw exception"() {
    given:
    @Subject def operation = new DeleteOpenstackLoadBalancerAtomicOperation(description)

    when:
    operation.operate([])

    then:
    1 * provider.getLoadBalancerPool(description.region, description.id) >> { throw new OpenstackProviderException('foobar') }
    OpenstackOperationException ex = thrown(OpenstackOperationException)
    ex.message == 'deleteLoadBalancer failed: foobar'
  }

}
