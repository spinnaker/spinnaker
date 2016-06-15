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
import com.netflix.spinnaker.clouddriver.openstack.deploy.description.servergroup.DeployOpenstackAtomicOperationDescription
import com.netflix.spinnaker.clouddriver.openstack.security.OpenstackCredentials
import com.netflix.spinnaker.clouddriver.openstack.security.OpenstackNamedAccountCredentials
import org.openstack4j.openstack.heat.domain.HeatStack
import spock.lang.Specification
import spock.lang.Subject

class CloneOpenstackAtomicOperationSpec extends Specification {
  private static final String ACCOUNT_NAME = 'myaccount'
  private static final APPLICATION = "app"
  private static final STACK = "stack"
  private static final DETAILS = "details"
  private static final REGION = "region"
  private static final String HEAT_TEMPLATE = '{"heat_template_version":"2013-05-23",' +
    '"description":"Simple template to test heat commands",' +
    '"parameters":{"flavor":{"default":"m1.nano","type":"string"}},' +
    '"resources":{"hello_world":{"type":"OS::Nova::Server",' +
    'properties":{"flavor":{"get_param":"flavor"},' +
    '"image":"cirros-0.3.4-x86_64-uec","user_data":""}}}}'
  private static final Integer TIMEOUT_MINS = 5
  private static final Map<String,String> PARAMS_MAP = Collections.emptyMap()
  private static final Boolean DISABLE_ROLLBACK = false

  private static final SEQUENCE = "v000"
  private static final ANCESTOR_STACK_NAME = "$APPLICATION-$STACK-$DETAILS-$SEQUENCE"
  private static final ANCESTOR_STACK_ID = "978f378f-0b98-469d-90dd-f61a73f8703a"

  // Changed Parameters
  private static final STACK_N = "stackn"
  private static final APPLICATION_N = "appn"
  private static final DETAILS_N = "detailn"
  private static final REGION_N = "regionn"
  private static final String HEAT_TEMPLATE_N = '{"heat_template_version":"2013-06-10",' +
    '"description":"Simple template to test heat commands",' +
    '"parameters":{"flavor":{"default":"m1.nano","type":"string"}},' +
    '"resources":{"hello_world":{"type":"OS::Nova::Server",' +
    'properties":{"flavor":{"get_param":"flavor"},' +
    '"image":"cirros-0.3.4-x86_64-uec","user_data":""}}}}'
  private static final Integer TIMEOUT_MINS_N = 6
  private static final Boolean DISABLE_ROLLBACK_N = true

  def credentials
  def provider

  HeatStack createHeatStack() {
    return new HeatStack(
      id: ANCESTOR_STACK_ID,
      name: ANCESTOR_STACK_NAME,
      status: "CREATE_COMPLETE",
      timeoutMins: 5
    )
  }

  DeployOpenstackAtomicOperation createDeployOpenstackAO() {
    return new DeployOpenstackAtomicOperation(createAncestorDeployAtomicOperationDescription())
  }
  DeployOpenstackAtomicOperationDescription createAncestorDeployAtomicOperationDescription() {
    return new DeployOpenstackAtomicOperationDescription(
      stack: STACK,
      application: APPLICATION,
      freeFormDetails: DETAILS,
      region: REGION,
      heatTemplate: HEAT_TEMPLATE,
      timeoutMins: TIMEOUT_MINS,
      parameters: PARAMS_MAP,
      disableRollback: DISABLE_ROLLBACK,
      account: ACCOUNT_NAME,
      credentials: credentials
    )
  }

  DeployOpenstackAtomicOperationDescription createNewDeployAtomicOperationDescription() {
    return new DeployOpenstackAtomicOperationDescription(
      stack: STACK_N,
      application: APPLICATION_N,
      freeFormDetails: DETAILS_N,
      region: REGION_N,
      heatTemplate: HEAT_TEMPLATE_N,
      timeoutMins: TIMEOUT_MINS_N,
      parameters: PARAMS_MAP,
      disableRollback: DISABLE_ROLLBACK_N,
      account: ACCOUNT_NAME,
      credentials: credentials
    )
  }

  def ancestorNames = [
  "app": APPLICATION,
  "stack": STACK,
  "detail": DETAILS
  ]

  def ancestorDeployAtomicOperationDescription = createAncestorDeployAtomicOperationDescription()
  def newDeployAtomicOperationDescription = createNewDeployAtomicOperationDescription()

  def setupSpec() {
    TaskRepository.threadLocalTask.set(Mock(Task))
  }

  def setup() {
    provider = Mock(OpenstackClientProvider)
    GroovyMock(OpenstackProviderFactory, global : true)
    OpenstackNamedAccountCredentials creds = Mock(OpenstackNamedAccountCredentials)
    OpenstackProviderFactory.createProvider(creds) >> { provider }
    credentials = new OpenstackCredentials(creds)

  }

  def "builds a description based on ancestor server group, overrides nothing"() {
    given:
    def inputDescription = new CloneOpenstackAtomicOperationDescription(
      source: [stackName: ANCESTOR_STACK_NAME, region: REGION],
      account: ACCOUNT_NAME,
      credentials: credentials
    )

    @Subject def operation = new CloneOpenstackAtomicOperation(inputDescription)

    when:
    def resultDescription = operation.cloneAndOverrideDescription()

    then:
    1 * inputDescription.credentials.provider.getStack(REGION, ANCESTOR_STACK_NAME) >> createHeatStack()
    1 * inputDescription.credentials.provider.getHeatTemplate(REGION, ANCESTOR_STACK_NAME, ANCESTOR_STACK_ID) >> HEAT_TEMPLATE

    resultDescription.application == ancestorDeployAtomicOperationDescription.application
    resultDescription.stack == ancestorDeployAtomicOperationDescription.stack
    resultDescription.timeoutMins == ancestorDeployAtomicOperationDescription.timeoutMins
    resultDescription.heatTemplate == ancestorDeployAtomicOperationDescription.heatTemplate
    resultDescription.freeFormDetails == ancestorDeployAtomicOperationDescription.freeFormDetails
    resultDescription.parameters == ancestorDeployAtomicOperationDescription.parameters
    resultDescription.disableRollback == ancestorDeployAtomicOperationDescription.disableRollback
    resultDescription.account == ancestorDeployAtomicOperationDescription.account
    resultDescription.region == ancestorDeployAtomicOperationDescription.region
  }

  def "builds a description based on ancestor server group, overrides everything"() {
    given:
    def inputDescription = new CloneOpenstackAtomicOperationDescription(
      stack: STACK_N,
      application: APPLICATION_N,
      freeFormDetails: DETAILS_N,
      region: REGION_N,
      heatTemplate: HEAT_TEMPLATE_N,
      timeoutMins: TIMEOUT_MINS_N,
      parameters: PARAMS_MAP,
      disableRollback: DISABLE_ROLLBACK_N,
      source: [stackName: ANCESTOR_STACK_NAME, region: REGION],
      credentials: credentials,
      account: ACCOUNT_NAME
    )

    @Subject def operation = new CloneOpenstackAtomicOperation(inputDescription)

    when:
    def resultDescription = operation.cloneAndOverrideDescription()

    then:
    1 * inputDescription.credentials.provider.getStack(REGION, ANCESTOR_STACK_NAME) >> createHeatStack()

    resultDescription.application == newDeployAtomicOperationDescription.application
    resultDescription.stack == newDeployAtomicOperationDescription.stack
    resultDescription.heatTemplate == newDeployAtomicOperationDescription.heatTemplate
    resultDescription.timeoutMins == newDeployAtomicOperationDescription.timeoutMins
    resultDescription.freeFormDetails == newDeployAtomicOperationDescription.freeFormDetails
    resultDescription.parameters == newDeployAtomicOperationDescription.parameters
    resultDescription.disableRollback == newDeployAtomicOperationDescription.disableRollback
    resultDescription.account == newDeployAtomicOperationDescription.account
    resultDescription.region == newDeployAtomicOperationDescription.region
  }
}
