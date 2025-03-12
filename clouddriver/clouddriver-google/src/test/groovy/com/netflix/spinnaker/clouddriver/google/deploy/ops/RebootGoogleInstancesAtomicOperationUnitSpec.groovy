/*
 * Copyright 2015 Google, Inc.
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
import com.netflix.spectator.api.DefaultRegistry
import com.netflix.spinnaker.clouddriver.data.task.Task
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository
import com.netflix.spinnaker.clouddriver.google.deploy.description.RebootGoogleInstancesDescription
import com.netflix.spinnaker.clouddriver.google.names.GoogleLabeledResourceNamer
import com.netflix.spinnaker.clouddriver.google.security.GoogleNamedAccountCredentials
import spock.lang.Specification
import spock.lang.Subject

class RebootGoogleInstancesAtomicOperationUnitSpec extends Specification {
  private static final ID_GOOD_PREFIX = "my-app7-dev-v000-good";
  private static final ID_BAD_PREFIX = "my-app7-dev-v000-bad";
  private static final ACCOUNT_NAME = "auto"
  private static final PROJECT_NAME = "my_project"
  private static final ZONE = "us-central1-b"
  private static final GOOD_INSTANCE_IDS = ["${ID_GOOD_PREFIX}1", "${ID_GOOD_PREFIX}2"]
  private static final BAD_INSTANCE_IDS = ["${ID_BAD_PREFIX}1", "${ID_BAD_PREFIX}2"]
  private static final ALL_INSTANCE_IDS = ["${ID_GOOD_PREFIX}1", "${ID_BAD_PREFIX}1",
                                           "${ID_GOOD_PREFIX}2", "${ID_BAD_PREFIX}2"]
  def registry = new DefaultRegistry()
  Compute computeMock
  GoogleNamedAccountCredentials credentials

  def setupSpec() {
    TaskRepository.threadLocalTask.set(Mock(Task))
  }

  def setup() {
    computeMock = Mock(Compute)
    credentials = new GoogleNamedAccountCredentials.Builder()
      .project(PROJECT_NAME)
      .compute(computeMock)
      .name("gce")
      .namer(new GoogleLabeledResourceNamer())
      .build()
  }

  void "should reset all instances"() {
    setup:
      def instancesMock = Mock(Compute.Instances)
      def resetMock = Mock(Compute.Instances.Reset)
      def description = new RebootGoogleInstancesDescription(zone: ZONE,
                                                            instanceIds: GOOD_INSTANCE_IDS,
                                                            accountName: ACCOUNT_NAME,
                                                            credentials: credentials)
      @Subject def operation = new RebootGoogleInstancesAtomicOperation(description)
      operation.registry = registry

    when:
      operation.operate([])

    then:
      0 * computeMock._
      GOOD_INSTANCE_IDS.each {
        1 * computeMock.instances() >> instancesMock
        1 * instancesMock.reset(PROJECT_NAME, ZONE, it) >> resetMock
        1 * resetMock.execute()
      }
  }

  void "should reset all known instances and fail on all unknown instances"() {
    setup:
      def instancesMock = Mock(Compute.Instances)
      def resetMock = Mock(Compute.Instances.Reset)
      def description = new RebootGoogleInstancesDescription(zone: ZONE,
                                                            instanceIds: ALL_INSTANCE_IDS,
                                                            accountName: ACCOUNT_NAME,
                                                            credentials: credentials)
      @Subject def operation = new RebootGoogleInstancesAtomicOperation(description)
      operation.registry = registry

    when:
      operation.operate([])

    then:
      0 * computeMock._
      GOOD_INSTANCE_IDS.each {
        1 * computeMock.instances() >> instancesMock
        1 * instancesMock.reset(PROJECT_NAME, ZONE, it) >> resetMock
        1 * resetMock.execute()
      }
      BAD_INSTANCE_IDS.each {
        1 * computeMock.instances() >> instancesMock
        1 * instancesMock.reset(PROJECT_NAME, ZONE, it) >> resetMock
        1 * resetMock.execute() >> { throw new IOException() }
      }
      thrown IOException
  }
}
