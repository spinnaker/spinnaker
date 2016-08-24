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

import com.netflix.spinnaker.clouddriver.data.task.Task
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository
import com.netflix.spinnaker.clouddriver.openstack.client.OpenstackClientProvider
import com.netflix.spinnaker.clouddriver.openstack.client.OpenstackProviderFactory
import com.netflix.spinnaker.clouddriver.openstack.deploy.description.instance.OpenstackInstancesRegistrationDescription
import com.netflix.spinnaker.clouddriver.openstack.deploy.exception.OpenstackOperationException
import com.netflix.spinnaker.clouddriver.openstack.deploy.exception.OpenstackProviderException
import com.netflix.spinnaker.clouddriver.openstack.security.OpenstackCredentials
import com.netflix.spinnaker.clouddriver.openstack.security.OpenstackNamedAccountCredentials
import org.openstack4j.model.network.ext.LbPool
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
  LbPool pool
  String ip = '1.2.3.4'
  int port = 8100
  String memberId = UUID.randomUUID().toString()
  String region = 'region1'

  def setupSpec() {
    TaskRepository.threadLocalTask.set(Mock(Task))
  }

  def setup() {
    OpenstackClientProvider provider = Mock(OpenstackClientProvider)
    GroovyMock(OpenstackProviderFactory, global: true)
    OpenstackNamedAccountCredentials credz = Mock(OpenstackNamedAccountCredentials)
    OpenstackProviderFactory.createProvider(credz) >> { provider }
    credentials = new OpenstackCredentials(credz)
    description = new OpenstackInstancesRegistrationDescription(region: region, loadBalancerIds: LB_IDS, instanceIds: INSTANCE_IDS, weight: 1, account: ACCOUNT_NAME, credentials: credentials)
    pool = Mock(LbPool)
  }

  def "should perform registration operation with load balancers"() {
    given:
    @Subject def operation = opClass.newInstance(description)
    OpenstackClientProvider provider = credentials.provider

    when:
    operation.operate([])

    then:
    LB_IDS.each { lbid ->
      INSTANCE_IDS.each { id ->
        1 * provider.getLoadBalancerPool(region, lbid) >> pool
        1 * provider.getIpForInstance(region, id) >> ip
        if (method == 'registerInstancesWithLoadBalancer') {
          1 * provider.getInternalLoadBalancerPort(pool) >> port
          1 * provider.addMemberToLoadBalancerPool(region, ip, lbid, port, description.weight)
        } else {
          1 * provider.getMemberIdForInstance(region, ip, pool) >> memberId
          1 * provider.removeMemberFromLoadBalancerPool(region, memberId)
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
      INSTANCE_IDS.each { id ->
        provider.getLoadBalancerPool(region, lbid) >> { throw new OpenstackProviderException("foobar") }
      }
    }
    Exception ex = thrown(OpenstackProviderException)
    ex.message == "foobar"

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
      INSTANCE_IDS.each { id ->
        _ * provider.getLoadBalancerPool(region, lbid) >> pool
        provider.getIpForInstance(region, id) >> { throw new OpenstackProviderException("foobar") }
      }
    }
    Exception ex = thrown(OpenstackProviderException)
    ex.message == "foobar"

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
      INSTANCE_IDS.each { id ->
        _ * provider.getLoadBalancerPool(region, lbid) >> pool
        _ * provider.getIpForInstance(region, id) >> ip
        provider.getInternalLoadBalancerPort(pool) >> { throw new OpenstackProviderException("foobar") }
      }
    }
    Exception ex = thrown(OpenstackProviderException)
    ex.message == "foobar"

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
      INSTANCE_IDS.each { id ->
        _ * provider.getLoadBalancerPool(region, lbid) >> pool
        _ * provider.getIpForInstance(region, id) >> ip
        _ * provider.getInternalLoadBalancerPort(pool) >> port
        provider.addMemberToLoadBalancerPool(region, ip, lbid, port, description.weight) >> { throw new OpenstackProviderException("foobar") }
      }
    }
    Exception ex = thrown(OpenstackProviderException)
    ex.message == "foobar"

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
      INSTANCE_IDS.each { id ->
        _ * provider.getLoadBalancerPool(region, lbid) >> pool
        _ * provider.getIpForInstance(region, id) >> ip
        provider.getMemberIdForInstance(region, ip, pool) >> { throw new OpenstackProviderException("foobar") }
      }
    }
    Exception ex = thrown(OpenstackProviderException)
    ex.message == "foobar"

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
      INSTANCE_IDS.each { id ->
        _ * provider.getLoadBalancerPool(region, lbid) >> pool
        _ * provider.getIpForInstance(region, id) >> ip
        _ * provider.getMemberIdForInstance(region, ip, pool) >> memberId
        provider.removeMemberFromLoadBalancerPool(region, memberId) >> { throw new OpenstackProviderException("foobar") }
      }
    }
    Exception ex = thrown(OpenstackProviderException)
    ex.message == "foobar"

    where:
    opClass << [DeregisterOpenstackInstancesAtomicOperation]
  }

}
