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
import com.netflix.spinnaker.clouddriver.openstack.deploy.description.servergroup.CloneOpenstackAtomicOperationDescription
import com.netflix.spinnaker.clouddriver.openstack.deploy.exception.OpenstackOperationException
import com.netflix.spinnaker.clouddriver.openstack.deploy.exception.OpenstackResourceNotFoundException
import com.netflix.spinnaker.clouddriver.openstack.security.OpenstackCredentials
import com.netflix.spinnaker.clouddriver.openstack.security.OpenstackNamedAccountCredentials
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperations
import org.openstack4j.openstack.heat.domain.HeatStack
import spock.lang.Specification
import spock.lang.Subject

class CloneOpenstackAtomicOperationSpec extends Specification {
  private static final String ACCOUNT_NAME = 'myaccount'
  private static final REGION = 'region'
  private static final String HEAT_TEMPLATE = '{"heat_template_version":"2013-05-23",' +
    '"description":"Simple template to test heat commands",' +
    '"parameters":{"flavor":{"default":"m1.nano","type":"string"}},' +
    '"resources":{"hello_world":{"type":"OS::Nova::Server",' +
    'properties":{"flavor":{"get_param":"flavor"},' +
    '"image":"cirros-0.3.4-x86_64-uec","user_data":""}}}}'

  def credentials
  def provider

  def setupSpec() {
    TaskRepository.threadLocalTask.set(Mock(Task))
  }

  def setup() {
    provider = Mock(OpenstackClientProvider)
    GroovyMock(OpenstackProviderFactory, global: true)
    OpenstackNamedAccountCredentials creds = Mock(OpenstackNamedAccountCredentials)
    OpenstackProviderFactory.createProvider(creds) >> { provider }
    credentials = new OpenstackCredentials(creds)

  }

  def "builds a description based on ancestor server group, overrides nothing"() {
    given:
    def ancestorApp = 'app'
    def ancestorStack = 'stack'
    def ancestorDetails = 'details'
    def ancestor = new HeatStack(
      id: '978f378f-0b98-469d-90dd-f61a73f8703a',
      name: "$ancestorApp-$ancestorStack-$ancestorDetails-v000",
      status: 'CREATE_COMPLETE',
      timeoutMins: 5,
      parameters: [key: 'value']
    )

    def inputDescription = new CloneOpenstackAtomicOperationDescription(
      source: [stackName: ancestor.name, region: REGION],
      account: ACCOUNT_NAME,
      credentials: credentials
    )

    @Subject def operation = new CloneOpenstackAtomicOperation(inputDescription)

    when:
    def resultDescription = operation.cloneAndOverrideDescription()

    then:
    1 * inputDescription.credentials.provider.getStack(REGION, ancestor.name) >> ancestor
    1 * inputDescription.credentials.provider.getHeatTemplate(REGION, ancestor.name, ancestor.id) >> HEAT_TEMPLATE

    resultDescription.application == ancestorApp
    resultDescription.stack == ancestorStack
    resultDescription.timeoutMins == (int) ancestor.timeoutMins
    resultDescription.heatTemplate == HEAT_TEMPLATE
    resultDescription.freeFormDetails == ancestorDetails
    resultDescription.parameters == ancestor.parameters
    resultDescription.account == ACCOUNT_NAME
    resultDescription.region == REGION
    !resultDescription.disableRollback // False is the default
  }

  def "builds a description based on ancestor server group, overrides everything"() {
    given:
    def app = 'app'
    def stack = 'stack'
    def details = 'details'
    def stackName = "$app-$stack-$details-v000"
    def inputDescription = new CloneOpenstackAtomicOperationDescription(
      stack: stack,
      application: app,
      freeFormDetails: details,
      region: REGION,
      heatTemplate: HEAT_TEMPLATE,
      timeoutMins: 5,
      parameters: [key: 'value'],
      disableRollback: true,
      source: [stackName: stackName, region: REGION],
      credentials: credentials,
      account: ACCOUNT_NAME
    )
    def ancestor = new HeatStack(
      id: '978f378f-0b98-469d-90dd-f61a73f8703a',
      name: stackName,
      status: 'CREATE_COMPLETE',
      timeoutMins: 5
    )

    @Subject def operation = new CloneOpenstackAtomicOperation(inputDescription)

    when:
    def resultDescription = operation.cloneAndOverrideDescription()

    then:
    1 * inputDescription.credentials.provider.getStack(REGION, stackName) >> ancestor

    resultDescription.application == inputDescription.application
    resultDescription.stack == inputDescription.stack
    resultDescription.heatTemplate == inputDescription.heatTemplate
    resultDescription.timeoutMins == inputDescription.timeoutMins
    resultDescription.freeFormDetails == inputDescription.freeFormDetails
    resultDescription.parameters == inputDescription.parameters
    resultDescription.disableRollback == inputDescription.disableRollback
    resultDescription.account == inputDescription.account
    resultDescription.region == inputDescription.region
  }

  def "ancestor stack not found throws operation exception"() {
    given:
    def stackName = 'app-stack-details-v000'
    def notFound = new OpenstackResourceNotFoundException("foo")
    def inputDescription = new CloneOpenstackAtomicOperationDescription(
      source: [stackName: stackName, region: REGION],
      account: ACCOUNT_NAME,
      credentials: credentials
    )

    @Subject def operation = new CloneOpenstackAtomicOperation(inputDescription)

    when:
    operation.operate([])

    then:
    1 * inputDescription.credentials.provider.getStack(REGION, stackName) >> { throw notFound }
    def ex = thrown(OpenstackOperationException)
    ex.message.contains(AtomicOperations.CLONE_SERVER_GROUP)
    ex.cause == notFound
  }
}
