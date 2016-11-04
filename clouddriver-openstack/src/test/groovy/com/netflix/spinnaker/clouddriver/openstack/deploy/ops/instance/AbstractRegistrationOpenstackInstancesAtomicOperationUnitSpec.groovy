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

package com.netflix.spinnaker.clouddriver.openstack.deploy.ops.instance

import com.netflix.spinnaker.clouddriver.consul.config.ConsulConfig
import com.netflix.spinnaker.clouddriver.data.task.Task
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository
import com.netflix.spinnaker.clouddriver.openstack.client.OpenstackClientProvider
import com.netflix.spinnaker.clouddriver.openstack.client.OpenstackProviderFactory
import com.netflix.spinnaker.clouddriver.openstack.config.OpenstackConfigurationProperties
import com.netflix.spinnaker.clouddriver.openstack.deploy.description.instance.OpenstackInstancesRegistrationDescription
import com.netflix.spinnaker.clouddriver.openstack.deploy.exception.OpenstackOperationException
import com.netflix.spinnaker.clouddriver.openstack.deploy.exception.OpenstackProviderException
import com.netflix.spinnaker.clouddriver.openstack.security.OpenstackCredentials
import com.netflix.spinnaker.clouddriver.openstack.security.OpenstackNamedAccountCredentials
import org.openstack4j.model.network.ext.LbProvisioningStatus
import org.openstack4j.model.network.ext.ListenerV2
import org.openstack4j.model.network.ext.LoadBalancerV2
import org.openstack4j.openstack.networking.domain.ext.ListItem
import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Unroll

@Unroll
class AbstractRegistrationOpenstackInstancesAtomicOperationUnitSpec extends Specification {

  private static final String ACCOUNT_NAME = 'myaccount'
  private static final INSTANCE_IDS = ['instance1','instance2','instance3']
  private static final LB_IDS = ['lb1','lb2','lb3']

  def credentials
  def description
  LoadBalancerV2 loadBalancer
  LoadBalancerV2 mockLB
  Map<String, ListenerV2> listenerMap
  List<ListItem> listeners = [new ListItem(id: UUID.randomUUID().toString()), new ListItem(id: UUID.randomUUID().toString())]
  String ip = '1.2.3.4'
  Integer port = 8100
  String subnetId = UUID.randomUUID().toString()
  String memberId = UUID.randomUUID().toString()
  String region = 'region1'

  def setupSpec() {
    TaskRepository.threadLocalTask.set(Mock(Task))
  }

  def setup() {
    OpenstackClientProvider provider = Mock(OpenstackClientProvider)
    GroovyMock(OpenstackProviderFactory, global: true)
    OpenstackNamedAccountCredentials credz = new OpenstackNamedAccountCredentials("name", "test", "main", "user", "pw", "tenant", "domain", "endpoint", [], false, "", new OpenstackConfigurationProperties.LbaasConfig(pollTimeout: 60, pollInterval: 5), new ConsulConfig(), null)
    OpenstackProviderFactory.createProvider(credz) >> { provider }
    credentials = new OpenstackCredentials(credz)
    description = new OpenstackInstancesRegistrationDescription(region: region, loadBalancerIds: LB_IDS, instanceIds: INSTANCE_IDS, weight: 1, account: ACCOUNT_NAME, credentials: credentials)
    loadBalancer = Mock(LoadBalancerV2) {
      it.listeners >> { listeners }
      it.vipSubnetId >> { subnetId }
    }
    mockLB = Mock(LoadBalancerV2) {
      it.id >> { _ }
      it.provisioningStatus >> { LbProvisioningStatus.ACTIVE }
    }
    listenerMap = (0..1).collectEntries { i ->
      [(listeners[i].id):Mock(ListenerV2) {
        it.defaultPoolId >> { 'poo' }
      }]
    }
  }

  def "should perform #method"() {
    given:
    @Subject def operation = opClass.newInstance(description)
    OpenstackClientProvider provider = credentials.provider

    when:
    operation.operate([])

    then:
    LB_IDS.each { lbid ->
      1 * provider.getLoadBalancer(region, lbid) >> loadBalancer
      INSTANCE_IDS.each { id ->
        1 * provider.getIpForInstance(region, id) >> ip
        loadBalancer.listeners.each { listItem ->
          1 * provider.getListener(region, listItem.id) >> listenerMap[listItem.id]
          if (method == 'registerInstancesWithLoadBalancer') {
            1 * provider.getInternalLoadBalancerPort(region, listItem.id) >> port
            1 * provider.addMemberToLoadBalancerPool(region, ip, listenerMap[listItem.id].defaultPoolId, subnetId, port, description.weight)
            _ * provider.getLoadBalancer(region, lbid) >> mockLB
          } else {
            1 * provider.getMemberIdForInstance(region, ip, listenerMap[listItem.id].defaultPoolId) >> memberId
            1 * provider.removeMemberFromLoadBalancerPool(region, listenerMap[listItem.id].defaultPoolId, memberId)
            _ * provider.getLoadBalancer(region, lbid) >> mockLB
          }
        }
      }
    }
    noExceptionThrown()

    where:
    opClass << [RegisterOpenstackInstancesAtomicOperation, DeregisterOpenstackInstancesAtomicOperation]
    method << ['registerInstancesWithLoadBalancer','deregisterInstancesFromLoadBalancer']
  }

  def "should throw exception when load balancer not found"() {
    given:
    @Subject def operation = opClass.newInstance(description)
    OpenstackClientProvider provider = credentials.provider

    when:
    operation.operate([])

    then:
    LB_IDS.each { lbid ->
      provider.getLoadBalancer(region, lbid) >> { throw new OpenstackProviderException("foobar") }
    }
    Exception ex = thrown(OpenstackOperationException)
    ex.cause.message == "foobar"

    where:
    opClass << [RegisterOpenstackInstancesAtomicOperation, DeregisterOpenstackInstancesAtomicOperation]
  }

  def "should throw exception when server has no IP"() {
    given:
    @Subject def operation = opClass.newInstance(description)
    OpenstackClientProvider provider = credentials.provider

    when:
    operation.operate([])

    then:
    LB_IDS.each { lbid ->
      provider.getLoadBalancer(region, lbid) >> loadBalancer
      INSTANCE_IDS.each { id ->
        provider.getIpForInstance(region, id) >> { throw new OpenstackProviderException("foobar") }
      }
    }
    Exception ex = thrown(OpenstackOperationException)
    ex.cause.message == "foobar"

    where:
    opClass << [RegisterOpenstackInstancesAtomicOperation, DeregisterOpenstackInstancesAtomicOperation]
  }

  def "should throw exception when internal port is not found"() {
    given:
    @Subject def operation = opClass.newInstance(description)
    OpenstackClientProvider provider = credentials.provider

    when:
    operation.operate([])

    then:
    LB_IDS.each { lbid ->
      provider.getLoadBalancer(region, lbid) >> loadBalancer
      INSTANCE_IDS.each { id ->
        provider.getIpForInstance(region, id) >> ip
        loadBalancer.listeners.each { listItem ->
          provider.getInternalLoadBalancerPort(region, listItem.id) >> { throw new OpenstackProviderException("foobar") }
        }
      }
    }
    Exception ex = thrown(OpenstackOperationException)
    ex.cause.message == "foobar"

    where:
    opClass << [RegisterOpenstackInstancesAtomicOperation]
  }

  def "should throw exception when failing to add member"() {
    given:
    @Subject def operation = opClass.newInstance(description)
    OpenstackClientProvider provider = credentials.provider

    when:
    operation.operate([])

    then:
    LB_IDS.each { lbid ->
      provider.getLoadBalancer(region, lbid) >> loadBalancer
      INSTANCE_IDS.each { id ->
        provider.getIpForInstance(region, id) >> ip
        loadBalancer.listeners.each { listItem ->
          provider.getListener(region, listItem.id) >> listenerMap[listItem.id]
          provider.getInternalLoadBalancerPort(region, listItem.id) >> port
          provider.addMemberToLoadBalancerPool(region, ip, listenerMap[listItem.id].defaultPoolId, subnetId, port, description.weight) >> { throw new OpenstackProviderException("foobar") }
        }
      }
    }
    Exception ex = thrown(OpenstackOperationException)
    ex.cause.message == "foobar"

    where:
    opClass << [RegisterOpenstackInstancesAtomicOperation]
  }

  def "should throw exception when member id cannot be found for server instance"() {
    given:
    @Subject def operation = opClass.newInstance(description)
    OpenstackClientProvider provider = credentials.provider

    when:
    operation.operate([])

    then:
    LB_IDS.each { lbid ->
      provider.getLoadBalancer(region, lbid) >> loadBalancer
      INSTANCE_IDS.each { id ->
        provider.getIpForInstance(region, id) >> ip
        loadBalancer.listeners.each { listItem ->
          provider.getListener(region, listItem.id) >> listenerMap[listItem.id]
          provider.getMemberIdForInstance(region, ip, listenerMap[listItem.id].defaultPoolId) >> {
            throw new OpenstackProviderException("foobar")
          }
        }
      }
    }
    Exception ex = thrown(OpenstackOperationException)
    ex.cause.message == "foobar"

    where:
    opClass << [DeregisterOpenstackInstancesAtomicOperation]
  }

  def "should throw exception when failing to remove member"() {
    given:
    @Subject def operation = opClass.newInstance(description)
    OpenstackClientProvider provider = credentials.provider

    when:
    operation.operate([])

    then:
    LB_IDS.each { lbid ->
      provider.getLoadBalancer(region, lbid) >> loadBalancer
      INSTANCE_IDS.each { id ->
        provider.getIpForInstance(region, id) >> ip
        loadBalancer.listeners.each { listItem ->
          provider.getListener(region, listItem.id) >> listenerMap[listItem.id]
          provider.getMemberIdForInstance(region, ip, listenerMap[listItem.id].defaultPoolId) >> memberId
          provider.removeMemberFromLoadBalancerPool(region, listenerMap[listItem.id].defaultPoolId, memberId) >> { throw new OpenstackProviderException("foobar") }
        }
      }
    }
    Exception ex = thrown(OpenstackOperationException)
    ex.cause.message == "foobar"

    where:
    opClass << [DeregisterOpenstackInstancesAtomicOperation]
  }

}
