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
import spock.lang.Specification
import spock.lang.Subject

class RebootOpenstackInstancesAtomicOperationUnitSpec extends Specification {

  private static final String ACCOUNT_NAME = 'myaccount'
  private static final INSTANCE_IDS = ['instance1', 'instance2', 'instance3']

  def credentials
  def description

  String region = 'r1'

  def setupSpec() {
    TaskRepository.threadLocalTask.set(Mock(Task))
  }

  def setup() {
    OpenstackClientProvider provider = Mock(OpenstackClientProvider)
    GroovyMock(OpenstackProviderFactory, global: true)
    OpenstackNamedAccountCredentials credz = Mock(OpenstackNamedAccountCredentials)
    OpenstackProviderFactory.createProvider(credz) >> { provider }
    credentials = new OpenstackCredentials(credz)
    description = new OpenstackInstancesDescription(instanceIds: INSTANCE_IDS, account: ACCOUNT_NAME, credentials: credentials, region: region)
  }

  def "should reboot instances"() {
    given:
    @Subject def operation = new RebootOpenstackInstancesAtomicOperation(description)

    when:
    operation.operate([])

    then:
    INSTANCE_IDS.each {
      1 * credentials.provider.rebootInstance(region, it)
    }
    noExceptionThrown()
  }

  def "should throw exception"() {
    given:
    @Subject def operation = new RebootOpenstackInstancesAtomicOperation(description)

    when:
    operation.operate([])

    then:
    INSTANCE_IDS.each {
      credentials.provider.rebootInstance(region, it) >> { throw new OpenstackOperationException("foobar") }
    }
    OpenstackOperationException ex = thrown(OpenstackOperationException)
    ex.message == "foobar"
  }

}
