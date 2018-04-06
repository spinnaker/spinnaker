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

package com.netflix.spinnaker.clouddriver.openstack.deploy.ops.servergroup

import com.netflix.spinnaker.clouddriver.data.task.Task
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository
import com.netflix.spinnaker.clouddriver.openstack.client.OpenstackClientProvider
import com.netflix.spinnaker.clouddriver.openstack.client.OpenstackProviderFactory
import com.netflix.spinnaker.clouddriver.openstack.config.OpenstackConfigurationProperties
import com.netflix.spinnaker.clouddriver.openstack.deploy.description.servergroup.OpenstackServerGroupAtomicOperationDescription
import com.netflix.spinnaker.clouddriver.openstack.deploy.description.servergroup.ServerGroupParameters
import com.netflix.spinnaker.clouddriver.openstack.deploy.exception.OpenstackOperationException
import com.netflix.spinnaker.clouddriver.openstack.security.OpenstackCredentials
import com.netflix.spinnaker.clouddriver.openstack.security.OpenstackNamedAccountCredentials
import org.openstack4j.model.compute.Server
import org.openstack4j.model.heat.Stack
import spock.lang.Specification
import spock.lang.Subject

class AbstractStackUpdateOpenstackAtomicOperationSpec extends Specification {

  String ACCOUNT_NAME = 'myaccount'

  def credentials
  def description

  String region = 'r1'
  String serverGroupName = 'asg1'

  Server server
  Stack stack

  String yaml = "foo: bar"
  List<String> tags = ["t1","t2"]

  def setupSpec() {
    TaskRepository.threadLocalTask.set(Mock(Task))
  }

  def setup() {
    OpenstackClientProvider provider = Mock(OpenstackClientProvider)
    GroovyMock(OpenstackProviderFactory, global: true)
    OpenstackNamedAccountCredentials credz = Mock(OpenstackNamedAccountCredentials)
    credz.getStackConfig() >> new OpenstackConfigurationProperties.StackConfig(pollInterval: 0, pollTimeout: 1)

    OpenstackProviderFactory.createProvider(credz) >> { provider }
    credentials = new OpenstackCredentials(credz)
    description = new OpenstackServerGroupAtomicOperationDescription(serverGroupName: serverGroupName, account: ACCOUNT_NAME, credentials: credentials, region: region)
    stack = Mock(Stack) {
      it.id >> { serverGroupName }
      it.name >> { serverGroupName }
      it.parameters >> { [:] }
      it.outputs >> { [[output_key: ServerGroupConstants.SUBTEMPLATE_OUTPUT, output_value: yaml], [output_key: ServerGroupConstants.MEMBERTEMPLATE_OUTPUT, output_value: yaml]] }
      it.tags >> { tags }
      it.status >> "UPDATE_COMPLETE"
    }
  }

  def "should update stack"() {
    given:
    @Subject def operation = new SampleAbstractStackUpdateOpenstackAtomicOperation(description)

    when:
    operation.operate([])

    then:
    2 * credentials.provider.getStack(region, serverGroupName) >> stack
    1 * credentials.provider.getHeatTemplate(region, serverGroupName, serverGroupName) >> yaml
    1 * credentials.provider.updateStack(region, serverGroupName, serverGroupName, yaml, _ as Map, _ as ServerGroupParameters, stack.tags)
    noExceptionThrown()
  }

  def "should throw exception"() {
    given:
    @Subject def operation = new SampleAbstractStackUpdateOpenstackAtomicOperation(description)

    when:
    operation.operate([])

    then:
    1 * credentials.provider.getStack(region, serverGroupName) >> stack
    1 * credentials.provider.getHeatTemplate(region, serverGroupName, serverGroupName) >> yaml
    1 * credentials.provider.updateStack(region, serverGroupName, serverGroupName, yaml, _ as Map, _ as ServerGroupParameters, stack.tags) >> { throw new OpenstackOperationException("foobar") }
    OpenstackOperationException ex = thrown(OpenstackOperationException)
    ex.message == "operation failed: foobar"
  }

  static class SampleAbstractStackUpdateOpenstackAtomicOperation extends AbstractStackUpdateOpenstackAtomicOperation {
    String phaseName = 'phase'
    String operation = 'operation'
    SampleAbstractStackUpdateOpenstackAtomicOperation(OpenstackServerGroupAtomicOperationDescription description) {
      super(description)
    }
  }

}
