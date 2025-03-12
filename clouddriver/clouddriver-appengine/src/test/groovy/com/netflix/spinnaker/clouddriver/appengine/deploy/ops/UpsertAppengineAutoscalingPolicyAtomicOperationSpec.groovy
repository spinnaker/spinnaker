/*
 * Copyright 2017 Google, Inc.
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

package com.netflix.spinnaker.clouddriver.appengine.deploy.ops

import com.google.api.services.appengine.v1.Appengine
import com.google.api.services.appengine.v1.model.AutomaticScaling
import com.google.api.services.appengine.v1.model.Version
import com.netflix.spectator.api.DefaultRegistry
import com.netflix.spinnaker.clouddriver.appengine.deploy.AppengineSafeRetry
import com.netflix.spinnaker.clouddriver.appengine.deploy.description.UpsertAppengineAutoscalingPolicyDescription
import com.netflix.spinnaker.clouddriver.appengine.model.AppengineScalingPolicy
import com.netflix.spinnaker.clouddriver.appengine.model.AppengineServerGroup
import com.netflix.spinnaker.clouddriver.appengine.model.ScalingPolicyType
import com.netflix.spinnaker.clouddriver.appengine.provider.view.AppengineClusterProvider
import com.netflix.spinnaker.clouddriver.appengine.security.AppengineCredentials
import com.netflix.spinnaker.clouddriver.appengine.security.AppengineNamedAccountCredentials
import com.netflix.spinnaker.clouddriver.data.task.Task
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository
import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Unroll

class UpsertAppengineAutoscalingPolicyAtomicOperationSpec extends Specification {
  private static final ACCOUNT_NAME = 'my-appengine-account'
  private static final SERVER_GROUP_NAME = 'app-stack-detail-v000'
  private static final REGION = 'us-central'
  private static final LOAD_BALANCER_NAME = 'default'
  private static final PROJECT = 'my-gcp-project'

  def setupSpec() {
    TaskRepository.threadLocalTask.set(Mock(Task))
  }

  @Unroll
  void "can upsert autoscaling policy"() {
    setup:
    def clusterProviderMock = Mock(AppengineClusterProvider)

    def appengineMock = Mock(Appengine)
    def appsMock = Mock(Appengine.Apps)
    def servicesMock = Mock(Appengine.Apps.Services)
    def versionsMock = Mock(Appengine.Apps.Services.Versions)
    def patchMock = Mock(Appengine.Apps.Services.Versions.Patch)

    def credentials = new AppengineNamedAccountCredentials.Builder()
      .credentials(Mock(AppengineCredentials))
      .name(ACCOUNT_NAME)
      .region(REGION)
      .project(PROJECT)
      .appengine(appengineMock)
      .build()

    def description = new UpsertAppengineAutoscalingPolicyDescription(
      accountName: ACCOUNT_NAME,
      serverGroupName: SERVER_GROUP_NAME,
      credentials: credentials,
      minIdleInstances: min,
      maxIdleInstances: max)

    def serverGroup = new AppengineServerGroup(
      name: SERVER_GROUP_NAME,
      loadBalancers: [LOAD_BALANCER_NAME],
      scalingPolicy: new AppengineScalingPolicy(type: ScalingPolicyType.AUTOMATIC,
                                                minIdleInstances: 1,
                                                maxIdleInstances: 10))

    @Subject def operation = new UpsertAppengineAutoscalingPolicyAtomicOperation(description)
    operation.appengineClusterProvider = clusterProviderMock
    operation.registry = new DefaultRegistry()
    operation.safeRetry = AppengineSafeRetry.withoutDelay()

    when:
    operation.operate([])

    then:
    1 * clusterProviderMock.getServerGroup(ACCOUNT_NAME, REGION, SERVER_GROUP_NAME) >> serverGroup

    1 * appengineMock.apps() >> appsMock
    1 * appsMock.services() >> servicesMock
    1 * servicesMock.versions() >> versionsMock
    1 * versionsMock.patch(PROJECT, LOAD_BALANCER_NAME, SERVER_GROUP_NAME, expectedVersion) >> patchMock
    1 * patchMock.setUpdateMask("automaticScaling.min_idle_instances,automaticScaling.max_idle_instances") >> patchMock
    1 * patchMock.execute()

    where:
    min  | max  || expectedVersion
    10   | 20   || new Version(automaticScaling: new AutomaticScaling(minIdleInstances: 10, maxIdleInstances: 20))
    null | 20   || new Version(automaticScaling: new AutomaticScaling(minIdleInstances: 1, maxIdleInstances: 20))
    5    | null || new Version(automaticScaling: new AutomaticScaling(minIdleInstances: 5, maxIdleInstances: 10))
  }
}
