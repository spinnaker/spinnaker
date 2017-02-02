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
import com.netflix.spinnaker.clouddriver.appengine.deploy.description.DeleteAppengineLoadBalancerDescription
import com.netflix.spinnaker.clouddriver.appengine.security.AppengineCredentials
import com.netflix.spinnaker.clouddriver.appengine.security.AppengineNamedAccountCredentials
import com.netflix.spinnaker.clouddriver.data.task.Task
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository
import spock.lang.Specification
import spock.lang.Subject

class DeleteAppengineLoadBalancerAtomicOperationSpec extends Specification {
  private static final ACCOUNT_NAME = 'my-appengine-account'
  private static final REGION = 'us-central'
  private static final LOAD_BALANCER_NAME = 'mobile'
  private static final PROJECT = 'my-gcp-project'

  def setupSpec() {
    TaskRepository.threadLocalTask.set(Mock(Task))
  }

  void "can delete an Appengine load balancer (service)"() {
    setup:
      def appengineMock = Mock(Appengine)
      def appsMock = Mock(Appengine.Apps)
      def servicesMock = Mock(Appengine.Apps.Services)
      def deleteMock = Mock(Appengine.Apps.Services.Delete)

      def credentials = new AppengineNamedAccountCredentials.Builder()
        .credentials(Mock(AppengineCredentials))
        .name(ACCOUNT_NAME)
        .region(REGION)
        .project(PROJECT)
        .appengine(appengineMock)
        .build()

      def description = new DeleteAppengineLoadBalancerDescription(
        accountName: ACCOUNT_NAME,
        loadBalancerName: LOAD_BALANCER_NAME,
        credentials: credentials
      )

      @Subject def operation = new DeleteAppengineLoadBalancerAtomicOperation(description)

    when:
      operation.operate([])

    then:
      1 * appengineMock.apps() >> appsMock
      1 * appsMock.services() >> servicesMock
      1 * servicesMock.delete(PROJECT, LOAD_BALANCER_NAME) >> deleteMock
      1 * deleteMock.execute()
  }
}
