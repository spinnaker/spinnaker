/*
 * Copyright 2016 The original authors.
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
import com.netflix.spinnaker.clouddriver.openstack.deploy.description.servergroup.DeployOpenstackAtomicOperationDescription
import com.netflix.spinnaker.clouddriver.openstack.deploy.exception.OpenstackOperationException
import com.netflix.spinnaker.clouddriver.openstack.domain.ServerGroupParameters
import com.netflix.spinnaker.clouddriver.openstack.security.OpenstackCredentials
import com.netflix.spinnaker.clouddriver.openstack.security.OpenstackNamedAccountCredentials
import org.openstack4j.model.heat.Stack
import org.openstack4j.model.network.ext.LbPool
import spock.lang.Specification
import spock.lang.Subject

class DeployOpenstackAtomicOperationSpec extends Specification {
  String accountName = 'myaccount'
  String application = "app"
  String stack = "stack"
  String details = "details"
  String region = "region"
  Integer timeoutMins = 5
  Map<String,String> params = [:]
  Boolean disableRollback = false
  String instanceType = 'm1.small'
  int internalPort = 8100
  String image = 'ubuntu-latest'
  int maxSize = 5
  int minSize = 3
  String networkId = '1234'
  String poolId = '5678'
  List<String> securityGroups = ['sg1']

  def credentials
  def serverGroupParams
  def description
  def provider
  def mockPool

  def setupSpec() {
    TaskRepository.threadLocalTask.set(Mock(Task))
  }

  def setup() {
    provider = Mock(OpenstackClientProvider)
    GroovyMock(OpenstackProviderFactory, global : true)
    OpenstackNamedAccountCredentials creds = Mock(OpenstackNamedAccountCredentials)
    OpenstackProviderFactory.createProvider(creds) >> { provider }
    credentials = new OpenstackCredentials(creds)
    serverGroupParams = new ServerGroupParameters(instanceType: instanceType, image:image, maxSize: maxSize, minSize: minSize, networkId: networkId, poolId: poolId, securityGroups: securityGroups)
    description = new DeployOpenstackAtomicOperationDescription(stack: stack, application: application, freeFormDetails: details, region: region, serverGroupParameters: serverGroupParams, timeoutMins: timeoutMins, disableRollback: disableRollback, account: accountName, credentials: credentials)
    mockPool = Mock(LbPool)
    mockPool.name >> { 'mockpool' }
  }

  def "should deploy a heat stack"() {
    given:
    @Subject def operation = new DeployOpenstackAtomicOperation(description)
    String createdStackName = 'app-stack-details-v000'

    when:
    operation.operate([])

    then:
    1 * provider.listStacks(region) >> []
    1 * provider.getLoadBalancerPool(region, poolId) >> mockPool
    1 * provider.getInternalLoadBalancerPort(mockPool) >> internalPort
    1 * provider.deploy(region, createdStackName, _ as String, _ as Map<String,String>, serverGroupParams, _ as Boolean, _ as Long)
    noExceptionThrown()
  }

  def "should deploy a heat stack even when stack exists"() {
    given:
    @Subject def operation = new DeployOpenstackAtomicOperation(description)
    Stack stack = Mock(Stack)
    String createdStackName = 'app-stack-details-v000'
    stack.name >> { createdStackName }
    stack.creationTime >> { '2014-06-03T20:59:46Z' }
    String newStackName = 'app-stack-details-v001'

    when:
    operation.operate([])

    then:
    1 * provider.listStacks(_) >> [stack]
    1 * provider.getLoadBalancerPool(region, poolId) >> mockPool
    1 * provider.getInternalLoadBalancerPort(mockPool) >> internalPort
    1 * provider.deploy(region, newStackName, _ as String, _ as Map<String,String>, serverGroupParams, _ as Boolean, _ as Long)
    noExceptionThrown()
  }

  def "should not deploy a stack when exception thrown"() {
    //TODO
  }
}
