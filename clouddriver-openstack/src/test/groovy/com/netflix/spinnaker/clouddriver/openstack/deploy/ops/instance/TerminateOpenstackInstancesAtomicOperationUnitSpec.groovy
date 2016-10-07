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

package com.netflix.spinnaker.clouddriver.openstack.deploy.ops.instance

import com.netflix.spinnaker.clouddriver.data.task.Task
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository
import com.netflix.spinnaker.clouddriver.openstack.client.OpenstackClientProvider
import com.netflix.spinnaker.clouddriver.openstack.client.OpenstackProviderFactory
import com.netflix.spinnaker.clouddriver.openstack.deploy.description.instance.OpenstackInstancesDescription
import com.netflix.spinnaker.clouddriver.openstack.deploy.exception.OpenstackOperationException
import com.netflix.spinnaker.clouddriver.openstack.security.OpenstackCredentials
import com.netflix.spinnaker.clouddriver.openstack.security.OpenstackNamedAccountCredentials
import org.openstack4j.model.compute.Server
import org.openstack4j.model.heat.Resource
import org.openstack4j.model.heat.ResourceHealth
import org.openstack4j.model.heat.Stack
import spock.lang.Specification
import spock.lang.Subject

class TerminateOpenstackInstancesAtomicOperationUnitSpec extends Specification {

  private static final String ACCOUNT_NAME = 'myaccount'
  private static final INSTANCE_IDS = ['1-2-3-4','2-3-4-5','3-4-5-6']

  def credentials
  def description

  String region = 'r1'
  String serverGroupName = 'asg1'

  Map<String, Server> servers
  Stack stack
  Stack asgStack
  Resource asg
  Resource instance
  String asgId = 'asgId'
  String resourceName = 'r'

  def setupSpec() {
    TaskRepository.threadLocalTask.set(Mock(Task))
  }

  def setup() {
    OpenstackClientProvider provider = Mock(OpenstackClientProvider)
    GroovyMock(OpenstackProviderFactory, global: true)
    OpenstackNamedAccountCredentials credz = Mock(OpenstackNamedAccountCredentials)
    OpenstackProviderFactory.createProvider(credz) >> { provider }
    credentials = new OpenstackCredentials(credz)
    description = new OpenstackInstancesDescription(serverGroupName: serverGroupName, instanceIds: INSTANCE_IDS, account: ACCOUNT_NAME, credentials: credentials, region: region)
    servers = INSTANCE_IDS.collectEntries { id ->
      [(id): Mock(Server) { it.id >> { id } ; it.name >> { id } }]
    }
    stack = Mock(Stack) {
      it.name >> { 'stack' }
    }
    asg = Mock(Resource) {
      it.physicalResourceId >> { asgId }
      it.type >> { "OS::Heat::AutoScalingGroup" }
    }
    asgStack = Mock(Stack) {
      it.id >> { 'id' }
      it.name >> { 'name' }
    }
    instance = Mock(Resource) {
      it.resourceName >> { resourceName }
    }
  }

  def "test pre update"() {
    given:
    @Subject def operation = new TerminateOpenstackInstancesAtomicOperation(description)

    when:
    operation.preUpdate(stack)

    then:
    1 * credentials.provider.getAsgResourceForStack(region, stack) >> asg
    1 * credentials.provider.getStack(region, asgId) >> asgStack
    INSTANCE_IDS.each {
      1 * credentials.provider.getServerInstance(region, it) >> servers[it]
      1 * credentials.provider.getInstanceResourceForStack(region, stack, servers[it].name) >> instance
      1 * credentials.provider.markStackResourceUnhealthy(region, 'name', 'id', resourceName, _ as ResourceHealth)
    }
    noExceptionThrown()
  }

  def "should throw exception"() {
    given:
    @Subject def operation = new TerminateOpenstackInstancesAtomicOperation(description)

    when:
    operation.preUpdate(stack)

    then:
    1 * credentials.provider.getAsgResourceForStack(region, stack) >> asg
    1 * credentials.provider.getStack(region, asgId) >> asgStack
    INSTANCE_IDS.findAll { it == INSTANCE_IDS[0] }.each {
      1 * credentials.provider.getServerInstance(region, it) >> servers[it]
      1 * credentials.provider.getInstanceResourceForStack(region, stack, servers[it].name) >> instance
      1 * credentials.provider.markStackResourceUnhealthy(region, 'name', 'id', resourceName, _ as ResourceHealth) >> { throw new OpenstackOperationException("foobar") }
    }
    OpenstackOperationException ex = thrown(OpenstackOperationException)
    ex.message == "foobar"
  }

}
