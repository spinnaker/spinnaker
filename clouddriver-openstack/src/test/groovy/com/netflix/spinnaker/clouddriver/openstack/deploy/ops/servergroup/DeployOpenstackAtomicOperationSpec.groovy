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
import com.netflix.spinnaker.clouddriver.openstack.deploy.description.servergroup.DeployOpenstackAtomicOperationDescription
import com.netflix.spinnaker.clouddriver.openstack.deploy.exception.OpenstackOperationException
import com.netflix.spinnaker.clouddriver.openstack.security.OpenstackNamedAccountCredentials
import com.netflix.spinnaker.clouddriver.openstack.security.OpenstackCredentials
import spock.lang.Specification
import spock.lang.Subject

class DeployOpenstackAtomicOperationSpec extends Specification {
  private static final String ACCOUNT_NAME = 'myaccount'
  private static final APPLICATION = "app"
  private static final STACK = "stack"
  private static final DETAILS = "details"
  private static final REGION = "region"
  private static final String HEAT_TEMPLATE = "{\"heat_template_version\":\"2013-05-23\"," +
                                              "\"description\":\"Simple template to test heat commands\"," +
                                              "\"parameters\":{\"flavor\":{\"default\":\"m1.nano\",\"type\":\"string\"}}," +
                                              "\"resources\":{\"hello_world\":{\"type\":\"OS::Nova::Server\"," +
                                              "\"properties\":{\"flavor\":{\"get_param\":\"flavor\"}," +
                                              "\"image\":\"cirros-0.3.4-x86_64-uec\",\"user_data\":\"\"}}}}"
  private static final Integer TIMEOUT_MINS = 5
  private static final Map<String,String> PARAMS_MAP = Collections.emptyMap()
  private static final Boolean DISABLE_ROLLBACK = false

  def credentials
  def description
  def provider

  def setupSpec() {
    TaskRepository.threadLocalTask.set(Mock(Task))
  }

  def setup() {
    provider = Mock(OpenstackClientProvider)
    GroovyMock(OpenstackProviderFactory, global : true)
    OpenstackNamedAccountCredentials creds = Mock(OpenstackNamedAccountCredentials)
    OpenstackProviderFactory.createProvider(creds) >> { provider }
    credentials = new OpenstackCredentials(creds)
    description = new DeployOpenstackAtomicOperationDescription(stack: STACK, application: APPLICATION, freeFormDetails: DETAILS, region: REGION, heatTemplate: HEAT_TEMPLATE, timeoutMins: TIMEOUT_MINS, parameters: PARAMS_MAP, disableRollback: DISABLE_ROLLBACK, account: ACCOUNT_NAME, credentials: credentials)
  }

  def "should deploy a heat stack"() {
    given:
    @Subject def operation = new DeployOpenstackAtomicOperation(description)

    when:
    operation.operate([])

    then:
    1 * provider.listStacks(_) >> []
    1 * provider.deploy(_ as String, _ as String, _ as String, _ as Map<String,String>, _ as Boolean, _ as Long)
    noExceptionThrown()
  }

  def "should throw an exception"() {
    given:
    @Subject def operation = new DeployOpenstackAtomicOperation(description)

    when:
    operation.operate([])

    then:
    1 * provider.listStacks(_) >> []
    1 * provider.deploy(_ as String, _ as String, _ as String, _ as Map<String,String>, _ as Boolean, _ as Long) >> { throw new OpenstackOperationException("foobar") }
    OpenstackOperationException ex = thrown(OpenstackOperationException)
    ex.message == "foobar"
  }
}
