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
import com.google.api.services.compute.model.RegionInstanceGroupManagersSetAutoHealingRequest
import com.netflix.spectator.api.DefaultRegistry
import com.netflix.spinnaker.clouddriver.data.task.Task
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository
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

  def setupSpec() {
    TaskRepository.threadLocalTask.set(Mock(Task))
  }

  @Unroll
  void "should delete zonal and regional autoscaling policy"() {
    setup:
    def registry = new DefaultRegistry()
    def googleClusterProviderMock = Mock(GoogleClusterProvider)
    def computeMock = Mock(Compute)

    // zonal setup
    def autoscalersMock = Mock(Compute.Autoscalers)
    def deleteMock = Mock(Compute.Autoscalers.Delete)

    // regional setup
    def regionAutoscalersMock = Mock(Compute.RegionAutoscalers)
    def regionDeleteMock = Mock(Compute.RegionAutoscalers.Delete)

    def serverGroup = new GoogleServerGroup(zone: ZONE, regional: isRegional).view
    def credentials = new GoogleNamedAccountCredentials.Builder().project(PROJECT_NAME).compute(computeMock).build()
    def description = new DeleteGoogleAutoscalingPolicyDescription(serverGroupName: SERVER_GROUP_NAME,
      region: REGION,
      accountName: ACCOUNT_NAME,
      credentials: credentials)
    @Subject def operation = new DeleteGoogleAutoscalingPolicyAtomicOperation(description)
    operation.registry = registry
    operation.googleClusterProvider = googleClusterProviderMock

    when:
    operation.operate([])

    then:
    1 * googleClusterProviderMock.getServerGroup(ACCOUNT_NAME, REGION, SERVER_GROUP_NAME) >> serverGroup

    if (isRegional) {
      1 * computeMock.regionAutoscalers() >> regionAutoscalersMock
      1 * regionAutoscalersMock.delete(PROJECT_NAME, REGION, SERVER_GROUP_NAME) >> regionDeleteMock
      1 * regionDeleteMock.execute()
      registry.timer(
          registry.createId("google.api",
                [api: "compute.regionAutoscalers.delete",
                 scope: "regional", region: REGION,
                 success: "true", statusCode: "0"])  // See GoogleExecutorTraitsSpec
      ).count() == 1
    } else {
      1 * computeMock.autoscalers() >> autoscalersMock
      1 * autoscalersMock.delete(PROJECT_NAME, ZONE, SERVER_GROUP_NAME) >> deleteMock
      1 * deleteMock.execute()
      registry.timer(
          registry.createId("google.api",
                [api: "compute.autoscalers.delete",
                 scope: "zonal", zone: ZONE,
                 success: "true", statusCode: "0"])  // See GoogleExecutorTraitsSpec
      ).count() == 1
    }

    where:
    isRegional << [true, false]
  }

  @Unroll
  void "should delete zonal and regional autoHealing policy"() {
    setup:
    def registry = new DefaultRegistry()
    def googleClusterProviderMock = Mock(GoogleClusterProvider)
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

    // regional setup
    def regionalRequest = new RegionInstanceGroupManagersSetAutoHealingRequest().setAutoHealingPolicies([])
    def regionalManagerMock = Mock(Compute.RegionInstanceGroupManagers)
    def regionalSetAutoHealingPolicyMock = Mock(Compute.RegionInstanceGroupManagers.SetAutoHealingPolicies)

    @Subject def operation = new DeleteGoogleAutoscalingPolicyAtomicOperation(description)
    operation.registry = registry
    operation.googleClusterProvider = googleClusterProviderMock

    when:
    operation.operate([])

    then:
    1 * googleClusterProviderMock.getServerGroup(ACCOUNT_NAME, REGION, SERVER_GROUP_NAME) >> serverGroup

    if (isRegional) {
      computeMock.regionInstanceGroupManagers() >> regionalManagerMock
      regionalManagerMock.setAutoHealingPolicies(PROJECT_NAME, REGION, SERVER_GROUP_NAME, regionalRequest) >> regionalSetAutoHealingPolicyMock
      registry.timer(
        registry.createId("google.api",
          [api: "compute.instanceGroupManagers.setAutoHealingPolicies",
           scope: "zone", region: REGION,
           success: "true", statusCode: "0"]
        )).count() == 1
    } else {
      computeMock.instanceGroupManagers() >> zonalManagerMock
      zonalManagerMock.setAutoHealingPolicies(PROJECT_NAME, ZONE, SERVER_GROUP_NAME, zonalRequest) >> zonalSetAutoHealingPolicyMock
      registry.timer(
        registry.createId("google.api",
          [api: "compute.regionInstanceGroupManagers.setAutoHealingPolicies",
           scope: "regional", zone: ZONE,
           success: "true", statusCode: "0"]
      )).count() == 1
    }

    where:
    isRegional << [true, false]
  }
}
