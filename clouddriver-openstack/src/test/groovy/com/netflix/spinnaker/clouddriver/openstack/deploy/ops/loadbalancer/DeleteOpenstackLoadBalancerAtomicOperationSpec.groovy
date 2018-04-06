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
import com.netflix.spinnaker.clouddriver.openstack.config.OpenstackConfigurationProperties
import com.netflix.spinnaker.clouddriver.openstack.deploy.description.loadbalancer.DeleteOpenstackLoadBalancerDescription
import com.netflix.spinnaker.clouddriver.openstack.deploy.exception.OpenstackOperationException
import com.netflix.spinnaker.clouddriver.openstack.deploy.exception.OpenstackProviderException
import com.netflix.spinnaker.clouddriver.openstack.security.OpenstackCredentials
import com.netflix.spinnaker.clouddriver.openstack.security.OpenstackNamedAccountCredentials
import org.openstack4j.model.network.ext.LbProvisioningStatus
import org.openstack4j.model.network.ext.LoadBalancerV2
import spock.lang.Specification
import spock.lang.Subject

class DeleteOpenstackLoadBalancerAtomicOperationSpec extends Specification {

  private static final String ACCOUNT_NAME = 'myaccount'

  OpenstackCredentials credentials
  DeleteOpenstackLoadBalancerDescription description
  String lbId = UUID.randomUUID()

  def setupSpec() {
    TaskRepository.threadLocalTask.set(Mock(Task))
  }

  def setup() {
    OpenstackClientProvider provider = Mock(OpenstackClientProvider)
    GroovyMock(OpenstackProviderFactory, global: true)
    OpenstackNamedAccountCredentials credz = Mock(OpenstackNamedAccountCredentials) {
      it.lbaasConfig >> new OpenstackConfigurationProperties.LbaasConfig(pollInterval: 0, pollTimeout: 1)
    }

    OpenstackProviderFactory.createProvider(credz) >> { provider }
    credentials = new OpenstackCredentials(credz)
    description = new DeleteOpenstackLoadBalancerDescription(region: 'region1', id: lbId, account: ACCOUNT_NAME, credentials: credentials)
  }

  def "should not delete load balancer"() {
    given:
    def operation = new DeleteOpenstackLoadBalancerAtomicOperation(description)

    when:
    operation.operate([])

    then:
    1 * credentials.provider.getLoadBalancer(description.region, description.id) >> null
    0 * operation.deleteLoadBalancer(description.region, _) >> {}
    0 * operation.updateServerGroup(DeleteOpenstackLoadBalancerAtomicOperation.BASE_PHASE, description.region, description.id) >> {
    }
    noExceptionThrown()

  }

  def "should not delete in pending state"() {
    given:
    def operation = new DeleteOpenstackLoadBalancerAtomicOperation(description)
    LoadBalancerV2 loadBalancer = Mock(LoadBalancerV2)

    when:
    operation.operate([])

    then:
    1 * credentials.provider.getLoadBalancer(description.region, description.id) >> loadBalancer
    2 * loadBalancer.provisioningStatus >> LbProvisioningStatus.PENDING_UPDATE

    and:
    thrown(OpenstackOperationException)
  }

  def "should throw exception"() {
    given:
    def operation = new DeleteOpenstackLoadBalancerAtomicOperation(description)

    when:
    operation.operate([])

    then:
    1 * credentials.provider.getLoadBalancer(description.region, description.id) >> {
      throw new OpenstackProviderException('foobar')
    }
    OpenstackOperationException ex = thrown(OpenstackOperationException)
    ex.message == 'deleteLoadBalancer failed: foobar'
  }

  def "should delete load balancer"() {
    given:
    def operation = Spy(DeleteOpenstackLoadBalancerAtomicOperation, constructorArgs: [description])
    LoadBalancerV2 loadBalancer = Mock(LoadBalancerV2) {
      getId() >> lbId
      getProvisioningStatus() >> LbProvisioningStatus.ACTIVE
    }
    Map listenerMap = [:]

    when:
    operation.operate([])

    then:
    2 * credentials.provider.getLoadBalancer(description.region, lbId) >>> [loadBalancer, null]
    1 * operation.buildListenerMap(description.region, loadBalancer) >> listenerMap
    1 * credentials.provider.deleteLoadBalancer(description.region, loadBalancer.id)
    noExceptionThrown()
  }

  def "should delete load balancer exception"() {
    given:
    def operation = Spy(DeleteOpenstackLoadBalancerAtomicOperation, constructorArgs: [description])
    LoadBalancerV2 loadBalancer = Mock(LoadBalancerV2) {
      getId() >> UUID.randomUUID()
    }
    Map listenerMap = Mock(Map)
    Collection values = Mock(Collection)

    when:
    operation.deleteLoadBalancer(description.region, loadBalancer)

    then:
    1 * operation.buildListenerMap(description.region, loadBalancer) >> listenerMap
    1 * listenerMap.values() >> values
    1 * operation.deleteLoadBalancerPeripherals(DeleteOpenstackLoadBalancerAtomicOperation.BASE_PHASE, description.region, loadBalancer.id, values) >> {
      throw new OpenstackProviderException('test')
    }
    0 * credentials.provider.deleteLoadBalancer(description.region, loadBalancer.id)

    and:
    thrown(OpenstackProviderException)
  }
}
