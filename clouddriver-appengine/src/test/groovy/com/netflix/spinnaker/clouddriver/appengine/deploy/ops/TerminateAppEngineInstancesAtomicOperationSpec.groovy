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
import com.netflix.spinnaker.clouddriver.appengine.deploy.description.TerminateAppEngineInstancesDescription
import com.netflix.spinnaker.clouddriver.appengine.deploy.exception.AppEngineResourceNotFoundException
import com.netflix.spinnaker.clouddriver.appengine.model.AppEngineInstance
import com.netflix.spinnaker.clouddriver.appengine.provider.view.AppEngineInstanceProvider
import com.netflix.spinnaker.clouddriver.appengine.security.AppEngineCredentials
import com.netflix.spinnaker.clouddriver.appengine.security.AppEngineNamedAccountCredentials
import com.netflix.spinnaker.clouddriver.data.task.Task
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository
import spock.lang.Specification
import spock.lang.Subject

class TerminateAppEngineInstancesAtomicOperationSpec extends Specification {
  private static final ACCOUNT_NAME = 'my-appengine-account'
  private static final REGION = 'us-central'
  private static final PROJECT = 'my-gcp-project'
  private static final INSTANCE_IDS = ["instance-1"]
  private static final INSTANCE_API_ID = "instance-api-id-1"
  private static final LOAD_BALANCER_NAME = "default"
  private static final SERVER_GROUP_NAME = "app-stack-detail-v000"

  private static final INSTANCE = new AppEngineInstance(
    name: "instance-1",
    id: "instance-api-id-1",
    loadBalancers: [LOAD_BALANCER_NAME],
    serverGroup: SERVER_GROUP_NAME
  )

  def setupSpec() {
    TaskRepository.threadLocalTask.set(Mock(Task))
  }

  void "can delete an AppEngine instance"() {
    setup:
      def appEngineMock = Mock(Appengine)
      def appsMock = Mock(Appengine.Apps)
      def servicesMock = Mock(Appengine.Apps.Services)
      def versionsMock = Mock(Appengine.Apps.Services.Versions)
      def instancesMock = Mock(Appengine.Apps.Services.Versions.Instances)
      def deleteMock = Mock(Appengine.Apps.Services.Versions.Instances.Delete)

      def credentials = new AppEngineNamedAccountCredentials.Builder()
        .credentials(Mock(AppEngineCredentials))
        .name(ACCOUNT_NAME)
        .region(REGION)
        .project(PROJECT)
        .appengine(appEngineMock)
        .build()

      def description = new TerminateAppEngineInstancesDescription(
        accountName: ACCOUNT_NAME,
        instanceIds: INSTANCE_IDS,
        credentials: credentials
      )

      @Subject def operation = new TerminateAppEngineInstancesAtomicOperation(description)
      def instanceProviderMock = Mock(AppEngineInstanceProvider)
      operation.appEngineInstanceProvider = instanceProviderMock

    when:
      operation.operate([])

    then:
      1 * instanceProviderMock.getInstance(ACCOUNT_NAME, REGION, INSTANCE_IDS[0]) >> INSTANCE

      1 * appEngineMock.apps() >> appsMock
      1 * appsMock.services() >> servicesMock
      1 * servicesMock.versions() >> versionsMock
      1 * versionsMock.instances() >> instancesMock
      1 * instancesMock.delete(PROJECT, LOAD_BALANCER_NAME, SERVER_GROUP_NAME, INSTANCE_API_ID) >> deleteMock
      1 * deleteMock.execute()
  }
}
