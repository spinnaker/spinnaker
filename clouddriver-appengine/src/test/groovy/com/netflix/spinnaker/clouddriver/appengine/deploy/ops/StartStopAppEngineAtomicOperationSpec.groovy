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

package com.netflix.spinnaker.clouddriver.appengine.deploy.ops

import com.google.api.services.appengine.v1.Appengine
import com.google.api.services.appengine.v1.model.Version
import com.netflix.spinnaker.clouddriver.appengine.deploy.description.StartStopAppEngineDescription
import com.netflix.spinnaker.clouddriver.appengine.model.AppEngineServerGroup
import com.netflix.spinnaker.clouddriver.appengine.provider.view.AppEngineClusterProvider
import com.netflix.spinnaker.clouddriver.appengine.security.AppEngineCredentials
import com.netflix.spinnaker.clouddriver.appengine.security.AppEngineNamedAccountCredentials
import com.netflix.spinnaker.clouddriver.data.task.Task
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository
import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Unroll

class StartStopAppEngineAtomicOperationSpec extends Specification {
  private static final ACCOUNT_NAME = 'my-appengine-account'
  private static final SERVER_GROUP_NAME = 'app-stack-detail-v000'
  private static final REGION = 'us-central'
  private static final LOAD_BALANCER_NAME = 'default'
  private static final PROJECT = 'my-gcp-project'

  def setupSpec() {
    TaskRepository.threadLocalTask.set(Mock(Task))
  }

  @Unroll
  void "start and stop operations should call version.patch API with appropriate versions"() {
    setup:
      def clusterProviderMock = Mock(AppEngineClusterProvider)

      def appEngineMock = Mock(Appengine)
      def appsMock = Mock(Appengine.Apps)
      def servicesMock = Mock(Appengine.Apps.Services)
      def versionsMock = Mock(Appengine.Apps.Services.Versions)
      def patchMock = Mock(Appengine.Apps.Services.Versions.Patch)

      def credentials = new AppEngineNamedAccountCredentials.Builder()
        .credentials(Mock(AppEngineCredentials))
        .name(ACCOUNT_NAME)
        .region(REGION)
        .project(PROJECT)
        .appengine(appEngineMock)
        .build()

      def description = new StartStopAppEngineDescription(
        accountName: ACCOUNT_NAME,
        serverGroupName: SERVER_GROUP_NAME,
        credentials: credentials,
      )

      def serverGroup = new AppEngineServerGroup(name: SERVER_GROUP_NAME, loadBalancers: [LOAD_BALANCER_NAME])

      @Subject def operation = start ?
        new StartAppEngineAtomicOperation(description) :
        new StopAppEngineAtomicOperation(description)
      operation.appEngineClusterProvider = clusterProviderMock

    when:
      operation.operate([])

    then:
      1 * clusterProviderMock.getServerGroup(ACCOUNT_NAME, REGION, SERVER_GROUP_NAME) >> serverGroup

      1 * appEngineMock.apps() >> appsMock
      1 * appsMock.services() >> servicesMock
      1 * servicesMock.versions() >> versionsMock
      1 * versionsMock.patch(PROJECT, LOAD_BALANCER_NAME, SERVER_GROUP_NAME, expectedVersion) >> patchMock
      1 * patchMock.setUpdateMask("servingStatus") >> patchMock
      1 * patchMock.execute()

    where:
      start | expectedVersion
      true  | new Version(servingStatus: "SERVING")
      false | new Version(servingStatus: "STOPPED")
  }
}
