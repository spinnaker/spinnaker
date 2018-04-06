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
import com.netflix.spinnaker.clouddriver.openstack.deploy.exception.OpenstackOperationException
import com.netflix.spinnaker.clouddriver.openstack.deploy.exception.OpenstackProviderException
import com.netflix.spinnaker.clouddriver.openstack.security.OpenstackCredentials
import com.netflix.spinnaker.clouddriver.openstack.security.OpenstackNamedAccountCredentials
import org.openstack4j.model.heat.Stack
import spock.lang.Specification
import spock.lang.Subject

class DestroyOpenstackAtomicOperationSpec extends Specification {

  private static final String ACCOUNT_NAME = 'myaccount'
  private static final STACK = "stack"
  private static final REGION = "region"

  def credentials
  def description
  def provider

  def setupSpec() {
    TaskRepository.threadLocalTask.set(Mock(Task))
  }

  def setup() {
    provider = Mock(OpenstackClientProvider)
    GroovyMock(OpenstackProviderFactory, global: true)
    OpenstackNamedAccountCredentials creds = Mock(OpenstackNamedAccountCredentials)
    creds.getStackConfig() >> new OpenstackConfigurationProperties.StackConfig(pollInterval: 0, pollTimeout: 1)

    OpenstackProviderFactory.createProvider(creds) >> { provider }
    credentials = new OpenstackCredentials(creds)
    description = new OpenstackServerGroupAtomicOperationDescription(serverGroupName: STACK, region: REGION, credentials: credentials)
  }

  def "destroy stack succeeds"() {
    given:
    @Subject def operation = new DestroyOpenstackAtomicOperation(description)
    Stack mockStack = Mock(Stack)

    when:
    operation.operate([])

    then:
    2 * provider.getStack(description.region, description.serverGroupName) >>> [mockStack, null]
    1 * provider.destroy(description.region, mockStack)
    noExceptionThrown()
  }

  def "destroy stack throws an exception when unable to delete stack"() {
    given:
    @Subject def operation = new DestroyOpenstackAtomicOperation(description)
    Stack mockStack = Mock(Stack)

    when:
    operation.operate([])

    then:
    1 * provider.getStack(description.region, description.serverGroupName) >> mockStack
    1 * provider.destroy(description.region, mockStack) >> { throw new OpenstackProviderException('foo') }
    Exception e = thrown(OpenstackOperationException)
    e.cause.message == 'foo'
  }

}
