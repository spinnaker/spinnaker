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
import com.netflix.spinnaker.clouddriver.data.task.Task
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository
import com.netflix.spinnaker.clouddriver.google.deploy.description.DeleteGoogleScalingPolicyDescription
import com.netflix.spinnaker.clouddriver.google.model.GoogleServerGroup
import com.netflix.spinnaker.clouddriver.google.provider.view.GoogleClusterProvider
import com.netflix.spinnaker.clouddriver.google.security.GoogleNamedAccountCredentials
import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Unroll

class DeleteGoogleScalingPolicyAtomicOperationUnitSpec extends Specification {
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
    def description = new DeleteGoogleScalingPolicyDescription(serverGroupName: SERVER_GROUP_NAME,
      region: REGION,
      accountName: ACCOUNT_NAME,
      credentials: credentials)
    @Subject def operation = new DeleteGoogleScalingPolicyAtomicOperation(description)
    operation.googleClusterProvider = googleClusterProviderMock

    when:
    operation.operate([])

    then:
    1 * googleClusterProviderMock.getServerGroup(ACCOUNT_NAME, REGION, SERVER_GROUP_NAME) >> serverGroup

    if (isRegional) {
      1 * computeMock.regionAutoscalers() >> regionAutoscalersMock
      1 * regionAutoscalersMock.delete(PROJECT_NAME, REGION, SERVER_GROUP_NAME) >> regionDeleteMock
      1 * regionDeleteMock.execute()
    } else {
      1 * computeMock.autoscalers() >> autoscalersMock
      1 * autoscalersMock.delete(PROJECT_NAME, ZONE, SERVER_GROUP_NAME) >> deleteMock
      1 * deleteMock.execute()
    }

    where:
    isRegional << [ true, false ]
  }
}
