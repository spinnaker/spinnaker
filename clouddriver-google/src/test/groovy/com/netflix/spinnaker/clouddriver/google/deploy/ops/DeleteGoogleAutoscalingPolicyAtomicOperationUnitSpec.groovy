/*
 * Copyright 2016 Google, Inc.
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

package com.netflix.spinnaker.clouddriver.google.deploy.ops

import com.google.api.services.compute.Compute
import com.google.api.services.compute.model.InstanceGroupManagersSetAutoHealingRequest
import com.google.api.services.compute.model.InstanceTemplate
import com.google.api.services.compute.model.RegionInstanceGroupManagersSetAutoHealingRequest
import com.netflix.spectator.api.DefaultRegistry
import com.netflix.spinnaker.clouddriver.data.task.Task
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository
import com.netflix.spinnaker.clouddriver.google.GoogleApiTestUtils
import com.netflix.spinnaker.clouddriver.google.deploy.GoogleOperationPoller
import com.netflix.spinnaker.clouddriver.google.deploy.description.DeleteGoogleAutoscalingPolicyDescription
import com.netflix.spinnaker.clouddriver.google.model.GoogleServerGroup
import com.netflix.spinnaker.clouddriver.google.provider.view.GoogleClusterProvider
import com.netflix.spinnaker.clouddriver.google.security.GoogleNamedAccountCredentials
import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Unroll

class DeleteGoogleAutoscalingPolicyAtomicOperationUnitSpec extends Specification {
  private static final SERVER_GROUP_NAME = "my-server-group"
  private static final ACCOUNT_NAME = "my-account-name"
  private static final REGION = "us-central1"
  private static final PROJECT_NAME = "my-project"
  private static final ZONE = "us-central1-f"

  def googleClusterProviderMock = Mock(GoogleClusterProvider)
  def computeMock = Mock(Compute)
  def operationPollerMock = Mock(GoogleOperationPoller)

  def setupSpec() {
    TaskRepository.threadLocalTask.set(Mock(Task))
  }

  @Unroll
  void "should delete zonal and regional autoscaling policy"() {
    setup:
    def registry = new DefaultRegistry()

    // zonal setup
    def autoscalersMock = Mock(Compute.Autoscalers)
    def deleteMock = Mock(Compute.Autoscalers.Delete)
    def zonalTimerId = GoogleApiTestUtils.makeOkId(
          registry, "compute.autoscalers.delete",
          [scope: "zonal", zone: ZONE])

    // regional setup
    def regionAutoscalersMock = Mock(Compute.RegionAutoscalers)
    def regionDeleteMock = Mock(Compute.RegionAutoscalers.Delete)
    def regionalTimerId = GoogleApiTestUtils.makeOkId(
          registry, "compute.regionAutoscalers.delete",
          [scope: "regional", region: REGION])

    def serverGroup = new GoogleServerGroup(zone: ZONE, regional: isRegional).view
    def credentials = new GoogleNamedAccountCredentials.Builder().project(PROJECT_NAME).compute(computeMock).build()
    def description = new DeleteGoogleAutoscalingPolicyDescription(serverGroupName: SERVER_GROUP_NAME,
      region: REGION,
      accountName: ACCOUNT_NAME,
      credentials: credentials)
    @Subject def operation = Spy(DeleteGoogleAutoscalingPolicyAtomicOperation, constructorArgs: [description])
    operation.registry = registry
    operation.googleClusterProvider = googleClusterProviderMock
    operation.googleOperationPoller = operationPollerMock

    when:
    operation.operate([])

    then:
    1 * operation.deletePolicyMetadata(computeMock, credentials, PROJECT_NAME, _) >> null // Tested separately.
    1 * googleClusterProviderMock.getServerGroup(ACCOUNT_NAME, REGION, SERVER_GROUP_NAME) >> serverGroup

    if (isRegional) {
      1 * computeMock.regionAutoscalers() >> regionAutoscalersMock
      1 * regionAutoscalersMock.delete(PROJECT_NAME, REGION, SERVER_GROUP_NAME) >> regionDeleteMock
      1 * regionDeleteMock.execute() >> [name: 'deleteOp']
    } else {
      1 * computeMock.autoscalers() >> autoscalersMock
      1 * autoscalersMock.delete(PROJECT_NAME, ZONE, SERVER_GROUP_NAME) >> deleteMock
      1 * deleteMock.execute() >> [name: 'deleteOp']
    }
    registry.timer(regionalTimerId).count() == (isRegional ? 1 : 0)
    registry.timer(zonalTimerId).count() == (isRegional ? 0 : 1)

    where:
    isRegional << [true, false]
  }

  @Unroll
  void "should delete zonal and regional autoHealing policy"() {
    setup:
    def registry = new DefaultRegistry()
    def computeMock = Mock(Compute)
    def credentials = new GoogleNamedAccountCredentials.Builder().project(PROJECT_NAME).compute(computeMock).build()
    def description = new DeleteGoogleAutoscalingPolicyDescription(
      serverGroupName: SERVER_GROUP_NAME,
      region: REGION,
      accountName: ACCOUNT_NAME,
      credentials: credentials,
      deleteAutoHealingPolicy: true
    )
    def serverGroup = new GoogleServerGroup(zone: ZONE, regional: isRegional).view

    // zonal setup
    def zonalRequest = new InstanceGroupManagersSetAutoHealingRequest().setAutoHealingPolicies([])
    def zonalManagerMock = Mock(Compute.InstanceGroupManagers)
    def zonalSetAutoHealingPolicyMock = Mock(Compute.InstanceGroupManagers.SetAutoHealingPolicies)
    def zonalTimerId = GoogleApiTestUtils.makeOkId(
          registry,
          "compute.instanceGroupManagers.setAutoHealingPolicies",
          [scope: "zonal", zone: ZONE])

    // regional setup
    def regionalRequest = new RegionInstanceGroupManagersSetAutoHealingRequest().setAutoHealingPolicies([])
    def regionalManagerMock = Mock(Compute.RegionInstanceGroupManagers)
    def regionalSetAutoHealingPolicyMock = Mock(Compute.RegionInstanceGroupManagers.SetAutoHealingPolicies)
    def regionalTimerId = GoogleApiTestUtils.makeOkId(
          registry,
          "compute.regionInstanceGroupManagers.setAutoHealingPolicies",
          [scope: "regional", region: REGION])

    @Subject def operation = Spy(DeleteGoogleAutoscalingPolicyAtomicOperation, constructorArgs: [description])
    operation.registry = registry
    operation.googleClusterProvider = googleClusterProviderMock
    operation.googleOperationPoller = operationPollerMock

    when:
    operation.operate([])

    then:
    1 * operation.deletePolicyMetadata(computeMock, credentials, PROJECT_NAME, _) >> null // Tested separately.
    1 * googleClusterProviderMock.getServerGroup(ACCOUNT_NAME, REGION, SERVER_GROUP_NAME) >> serverGroup

    if (isRegional) {
      computeMock.regionInstanceGroupManagers() >> regionalManagerMock
      regionalManagerMock.setAutoHealingPolicies(PROJECT_NAME, REGION, SERVER_GROUP_NAME, regionalRequest) >> regionalSetAutoHealingPolicyMock
      regionalSetAutoHealingPolicyMock.execute() >> [name: 'autoHealingOp']
    } else {
      computeMock.instanceGroupManagers() >> zonalManagerMock
      zonalManagerMock.setAutoHealingPolicies(PROJECT_NAME, ZONE, SERVER_GROUP_NAME, zonalRequest) >> zonalSetAutoHealingPolicyMock
      zonalSetAutoHealingPolicyMock.execute() >> [name: 'autoHealingOp']
    }
    registry.timer(regionalTimerId).count() == (isRegional ? 1 : 0)
    registry.timer(zonalTimerId).count() == (isRegional ? 0 : 1)

    where:
    isRegional << [true, false]
  }

  void "delete the instance template when deletePolicyMetadata is called"() {
    given:
    def registry = new DefaultRegistry()
    def computeMock = Mock(Compute)
    def autoscaler = [:]

    def credentials = new GoogleNamedAccountCredentials.Builder().project(PROJECT_NAME).compute(computeMock).build()
    def description = new DeleteGoogleAutoscalingPolicyDescription(serverGroupName: SERVER_GROUP_NAME,
      region: REGION,
      accountName: ACCOUNT_NAME,
      credentials: credentials)

    // Instance Template Update setup
    def igm = Mock(Compute.InstanceGroupManagers)
    def igmGet = Mock(Compute.InstanceGroupManagers.Get)
    def regionIgm = Mock(Compute.RegionInstanceGroupManagers)
    def regionIgmGet = Mock(Compute.RegionInstanceGroupManagers.Get)
    def groupManager = [instanceTemplate: 'templates/template']
    def instanceTemplates = Mock(Compute.InstanceTemplates)
    def instanceTemplatesGet = Mock(Compute.InstanceTemplates.Get)
    // TODO(jacobkiefer): The following is very change detector-y. Consider a refactor so we can just mock this function.
    def template = new InstanceTemplate(properties: [
      disks: [[getBoot: { return [initializeParams: [sourceImage: 'images/sourceImage']] }, initializeParams: [diskType: 'huge', diskSizeGb: 42], autoDelete: false]],
      name: 'template',
      networkInterfaces: [[network: "projects/$PROJECT_NAME/networks/my-network"]],
      serviceAccounts: [[email: 'serviceAccount@google.com']]
    ])

    @Subject def operation = Spy(DeleteGoogleAutoscalingPolicyAtomicOperation, constructorArgs: [description])
    operation.registry = registry
    operation.googleClusterProvider = googleClusterProviderMock
    operation.googleOperationPoller = operationPollerMock

    when:
    operation.deletePolicyMetadata(computeMock, credentials, PROJECT_NAME, groupUrl)

    then:
    if (isRegional) {
      1 * computeMock.regionInstanceGroupManagers() >> regionIgm
      1 * regionIgm.get(PROJECT_NAME, location, _ ) >> regionIgmGet
      1 * regionIgmGet.execute() >> groupManager
    } else {
      1 * computeMock.instanceGroupManagers() >> igm
      1 * igm.get(PROJECT_NAME, location, _ ) >> igmGet
      1 * igmGet.execute() >> groupManager
    }
    1 * computeMock.instanceTemplates() >> instanceTemplates
    1 * instanceTemplates.get(PROJECT_NAME, _) >> instanceTemplatesGet
    1 * instanceTemplatesGet.execute() >> template

    where:
    isRegional | location | groupUrl
    false      | ZONE     | "https://compute.googleapis.com/compute/v1/projects/spinnaker-jtk54/zones/us-central1-f/autoscalers/okra-auto-v005"
    true       | REGION   | "https://compute.googleapis.com/compute/v1/projects/spinnaker-jtk54/regions/us-central1/autoscalers/okra-auto-v005"
  }
}
