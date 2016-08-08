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
import com.netflix.spinnaker.clouddriver.openstack.deploy.exception.OpenstackOperationException
import com.netflix.spinnaker.clouddriver.openstack.deploy.exception.OpenstackProviderException
import com.netflix.spinnaker.clouddriver.openstack.deploy.description.servergroup.ServerGroupParameters
import com.netflix.spinnaker.clouddriver.openstack.security.OpenstackCredentials
import com.netflix.spinnaker.clouddriver.openstack.security.OpenstackNamedAccountCredentials
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperations
import org.openstack4j.model.heat.Stack
import spock.lang.Specification
import spock.lang.Subject

class CloneOpenstackAtomicOperationSpec extends Specification {

  private static final String ACCOUNT_NAME = 'myaccount'

  private static final STACK = "stack"
  private static final APPLICATION = "app"
  private static final DETAILS = "details"
  private static final REGION = "region"
  private static final Integer TIMEOUT_MINS = 5
  private static final Boolean DISABLE_ROLLBACK = false
  private static final String INSTANCE_TYPE = 'm1.medium'
  private static final String IMAGE = 'ubuntu-latest-orig'
  private static final int MAX_SIZE = 6
  private static final int MIN_SIZE = 4
  private static final String SUBNET_ID = '12356'
  private static final String POOL_ID = '47890'
  private static final List<String> SECURITY_GROUPS = ['sg99','sg3434']

  private static final SEQUENCE = "v000"
  private static final ANCESTOR_STACK_NAME = "$APPLICATION-$STACK-$DETAILS-$SEQUENCE"

  // Changed Parameters
  private static final STACK_N = "stackn"
  private static final APPLICATION_N = "appn"
  private static final DETAILS_N = "detailn"
  private static final REGION_N = "regionn"
  private static final Integer TIMEOUT_MINS_N = 6
  private static final Boolean DISABLE_ROLLBACK_N = true
  private static final String INSTANCE_TYPE_N = 'm1.small'
  private static final String IMAGE_N = 'ubuntu-latest'
  private static final int MAX_SIZE_N = 5
  private static final int MIN_SIZE_N = 3
  private static final String SUBNET_ID_N = '1234'
  private static final String POOL_ID_N = '5678'
  private static final List<String> SECURITY_GROUPS_N = ['sg1']

  def credentials
  def provider

  DeployOpenstackAtomicOperation createDeployOpenstackAO() {
    new DeployOpenstackAtomicOperation(createAncestorDeployAtomicOperationDescription())
  }

  DeployOpenstackAtomicOperationDescription createAncestorDeployAtomicOperationDescription() {
    def scaleup = new ServerGroupParameters.Scaler(cooldown: 60, adjustment: 1, period: 60, threshold: 50)
    def scaledown = new ServerGroupParameters.Scaler(cooldown: 60, adjustment: -1, period: 600, threshold: 15)
    def params = new ServerGroupParameters(
      instanceType: INSTANCE_TYPE,
      image:IMAGE,
      maxSize: MAX_SIZE,
      minSize: MIN_SIZE,
      subnetId: SUBNET_ID,
      poolId: POOL_ID,
      securityGroups: SECURITY_GROUPS,
      autoscalingType: ServerGroupParameters.AutoscalingType.CPU,
      scaleup: scaleup,
      scaledown: scaledown
    )
    new DeployOpenstackAtomicOperationDescription(
      stack: STACK,
      application: APPLICATION,
      freeFormDetails: DETAILS,
      region: REGION,
      serverGroupParameters: params,
      timeoutMins: TIMEOUT_MINS,
      disableRollback: DISABLE_ROLLBACK,
      account: ACCOUNT_NAME,
      credentials: credentials
    )
  }

  DeployOpenstackAtomicOperationDescription createNewDeployAtomicOperationDescription() {
    def scaleup = new ServerGroupParameters.Scaler(cooldown: 61, adjustment: 2, period: 61, threshold: 51)
    def scaledown = new ServerGroupParameters.Scaler(cooldown: 61, adjustment: -2, period: 601, threshold: 16)
    def params = new ServerGroupParameters(
      instanceType: INSTANCE_TYPE_N,
      image:IMAGE_N,
      maxSize: MAX_SIZE_N,
      minSize: MIN_SIZE_N,
      subnetId: SUBNET_ID_N,
      poolId: POOL_ID_N,
      securityGroups: SECURITY_GROUPS_N,
      autoscalingType: ServerGroupParameters.AutoscalingType.NETWORK_INCOMING,
      scaleup: scaleup,
      scaledown: scaledown
    )
    new DeployOpenstackAtomicOperationDescription(
      stack: STACK_N,
      application: APPLICATION_N,
      freeFormDetails: DETAILS_N,
      region: REGION_N,
      serverGroupParameters: params,
      timeoutMins: TIMEOUT_MINS_N,
      disableRollback: DISABLE_ROLLBACK_N,
      account: ACCOUNT_NAME,
      credentials: credentials
    )
  }

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
      source: new CloneOpenstackAtomicOperationDescription.OpenstackCloneSource(
        serverGroupName: ANCESTOR_STACK_NAME,
        region: REGION
      ),
      region: REGION,
      account: ACCOUNT_NAME,
      credentials: credentials
    )
    Stack mockStack = Mock(Stack)
    mockStack.parameters >> { ancestorDeployAtomicOperationDescription.serverGroupParameters.toParamsMap() }
    mockStack.timeoutMins >> { ancestorDeployAtomicOperationDescription.timeoutMins }

    @Subject def operation = new CloneOpenstackAtomicOperation(inputDescription)

    when:
    def resultDescription = operation.cloneAndOverrideDescription()

    then:
    1 * inputDescription.credentials.provider.getStack(REGION, ANCESTOR_STACK_NAME) >> mockStack
    resultDescription.application == ancestorDeployAtomicOperationDescription.application
    resultDescription.stack == ancestorDeployAtomicOperationDescription.stack
    resultDescription.timeoutMins == ancestorDeployAtomicOperationDescription.timeoutMins
    resultDescription.serverGroupParameters == ancestorDeployAtomicOperationDescription.serverGroupParameters
    resultDescription.freeFormDetails == ancestorDeployAtomicOperationDescription.freeFormDetails
    resultDescription.disableRollback == ancestorDeployAtomicOperationDescription.disableRollback
    resultDescription.account == ancestorDeployAtomicOperationDescription.account
    resultDescription.region == ancestorDeployAtomicOperationDescription.region
  }

  def "builds a description based on ancestor server group, overrides everything"() {
    given:
    def scaleup = new ServerGroupParameters.Scaler(cooldown: 61, adjustment: 2, period: 61, threshold: 51)
    def scaledown = new ServerGroupParameters.Scaler(cooldown: 61, adjustment: -2, period: 601, threshold: 16)
    def params = new ServerGroupParameters(
      instanceType: INSTANCE_TYPE_N,
      image:IMAGE_N,
      maxSize: MAX_SIZE_N,
      minSize: MIN_SIZE_N,
      subnetId: SUBNET_ID_N,
      poolId: POOL_ID_N,
      securityGroups: SECURITY_GROUPS_N,
      autoscalingType: ServerGroupParameters.AutoscalingType.NETWORK_INCOMING,
      scaleup: scaleup,
      scaledown: scaledown
    )
    def inputDescription = new CloneOpenstackAtomicOperationDescription(
      stack: STACK_N,
      application: APPLICATION_N,
      freeFormDetails: DETAILS_N,
      region: REGION_N,
      timeoutMins: TIMEOUT_MINS_N,
      disableRollback: DISABLE_ROLLBACK_N,
      source: new CloneOpenstackAtomicOperationDescription.OpenstackCloneSource(
        serverGroupName: ANCESTOR_STACK_NAME,
        region: REGION
      ),
      credentials: credentials,
      account: ACCOUNT_NAME,
      serverGroupParameters: params
    )
    Stack mockStack = Mock(Stack)
    mockStack.parameters >> { ancestorDeployAtomicOperationDescription.serverGroupParameters.toParamsMap() }
    mockStack.timeoutMins >> { ancestorDeployAtomicOperationDescription.timeoutMins }

    @Subject def operation = new CloneOpenstackAtomicOperation(inputDescription)

    when:
    def resultDescription = operation.cloneAndOverrideDescription()

    then:
    1 * inputDescription.credentials.provider.getStack(REGION, ANCESTOR_STACK_NAME) >> mockStack

    resultDescription.application == newDeployAtomicOperationDescription.application
    resultDescription.stack == newDeployAtomicOperationDescription.stack
    resultDescription.serverGroupParameters == newDeployAtomicOperationDescription.serverGroupParameters
    resultDescription.timeoutMins == newDeployAtomicOperationDescription.timeoutMins
    resultDescription.freeFormDetails == newDeployAtomicOperationDescription.freeFormDetails
    resultDescription.disableRollback == newDeployAtomicOperationDescription.disableRollback
    resultDescription.account == newDeployAtomicOperationDescription.account
    resultDescription.region == newDeployAtomicOperationDescription.region
  }

  def "ancestor stack not found throws operation exception"() {
    given:
    def stackName = 'app-stack-details-v000'
    def notFound = new OpenstackProviderException("foo")
    def inputDescription = new CloneOpenstackAtomicOperationDescription(
      source: new CloneOpenstackAtomicOperationDescription.OpenstackCloneSource(serverGroupName: stackName, region: REGION),
      region: REGION,
      account: ACCOUNT_NAME,
      credentials: credentials
    )

    @Subject def operation = new CloneOpenstackAtomicOperation(inputDescription)

    when:
    operation.operate([])

    then:
    1 * provider.getStack(REGION, stackName) >> { throw notFound }
    def ex = thrown(OpenstackOperationException)
    ex.message.contains(AtomicOperations.CLONE_SERVER_GROUP)
    ex.cause == notFound
  }

}
