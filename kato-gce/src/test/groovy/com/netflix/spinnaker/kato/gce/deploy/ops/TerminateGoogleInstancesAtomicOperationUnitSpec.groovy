/*
 * Copyright 2014 Netflix, Inc.
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


package com.netflix.spinnaker.kato.gce.deploy.ops

import com.google.api.services.compute.Compute
import com.netflix.spinnaker.amos.gce.GoogleCredentials
import com.netflix.spinnaker.kato.data.task.Task
import com.netflix.spinnaker.kato.data.task.TaskRepository
import com.netflix.spinnaker.kato.gce.deploy.description.TerminateGoogleInstancesDescription
import spock.lang.Specification
import spock.lang.Subject

class TerminateGoogleInstancesAtomicOperationUnitSpec extends Specification {
  private static final ACCOUNT_NAME = "auto"
  private static final PROJECT_NAME = "my_project"
  private static final ZONE = "us-central1-b"
  private static final INSTANCE_IDS = ["my-app7-dev-v000-instance1", "my-app7-dev-v000-instance2"]

  def setupSpec() {
    TaskRepository.threadLocalTask.set(Mock(Task))
  }

  void "should terminate instances"() {
    setup:
      def computeMock = Mock(Compute)
      def instancesMock = Mock(Compute.Instances)
      def deleteMock = Mock(Compute.Instances.Delete)
      def credentials = new GoogleCredentials(PROJECT_NAME, computeMock)
      def description = new TerminateGoogleInstancesDescription(zone: ZONE,
                                                                instanceIds: INSTANCE_IDS,
                                                                accountName: ACCOUNT_NAME,
                                                                credentials: credentials)
      @Subject def operation = new TerminateGoogleInstancesAtomicOperation(description)

    when:
      operation.operate([])

    then:
      0 * computeMock._
      INSTANCE_IDS.each {
        1 * computeMock.instances() >> instancesMock
        1 * instancesMock.delete(PROJECT_NAME, ZONE, it) >> deleteMock
        1 * deleteMock.execute()
      }
  }
}
