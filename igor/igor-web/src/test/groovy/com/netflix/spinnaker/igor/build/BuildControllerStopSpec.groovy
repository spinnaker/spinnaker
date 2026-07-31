/*
 * Copyright 2025 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.netflix.spinnaker.igor.build

import com.netflix.spinnaker.igor.PendingOperationsCache
import com.netflix.spinnaker.igor.helpers.TestUtils
import com.netflix.spinnaker.igor.service.BuildOperations
import com.netflix.spinnaker.igor.service.BuildServices
import com.netflix.spinnaker.igor.service.StoppableBuildService
import com.netflix.spinnaker.kork.retrofit.exceptions.SpinnakerHttpException
import com.netflix.spinnaker.kork.web.exceptions.InvalidRequestException
import com.netflix.spinnaker.kork.web.exceptions.NotFoundException
import okhttp3.MediaType
import okhttp3.ResponseBody
import spock.lang.Specification

class BuildControllerStopSpec extends Specification {

  BuildServices buildServices
  PendingOperationsCache pendingOperationsCache
  BuildController controller

  void setup() {
    buildServices = Mock(BuildServices)
    pendingOperationsCache = Mock(PendingOperationsCache)
    controller = new BuildController(
      buildServices,
      pendingOperationsCache,
      Optional.empty(),
      Optional.empty(),
      Optional.empty()
    )
  }

  def "stopJob calls stopRunningBuild when buildNumber is non-zero"() {
    given:
    def stoppableService = Mock(StoppableBuildOperations)
    buildServices.getService("my-master") >> stoppableService

    when:
    controller.stopJob("my-master", 42L, "my-job", "queue-123")

    then:
    1 * stoppableService.stopRunningBuild("my-job", 42L)
    0 * stoppableService.stopQueuedBuild(_)
  }

  def "stopJob calls stopQueuedBuild when buildNumber is zero"() {
    given:
    def stoppableService = Mock(StoppableBuildOperations)
    buildServices.getService("my-master") >> stoppableService

    when:
    controller.stopJob("my-master", 0L, "my-job", "queue-123")

    then:
    0 * stoppableService.stopRunningBuild(_, _)
    1 * stoppableService.stopQueuedBuild("queue-123")
  }

  def "stopJob swallows 404 from stopQueuedBuild"() {
    given:
    def stoppableService = Mock(StoppableBuildOperations)
    buildServices.getService("my-master") >> stoppableService
    stoppableService.stopQueuedBuild(_) >> {
      throw TestUtils.makeSpinnakerHttpException("http://test.net", 404,
        ResponseBody.create("not found", MediaType.parse("text/plain")))
    }

    when:
    controller.stopJob("my-master", 0L, "my-job", "queue-123")

    then:
    noExceptionThrown()
  }

  def "stopJob propagates non-404 errors from stopQueuedBuild"() {
    given:
    def stoppableService = Mock(StoppableBuildOperations)
    buildServices.getService("my-master") >> stoppableService
    stoppableService.stopQueuedBuild(_) >> {
      throw TestUtils.makeSpinnakerHttpException("http://test.net", 500,
        ResponseBody.create("server error", MediaType.parse("text/plain")))
    }

    when:
    controller.stopJob("my-master", 0L, "my-job", "queue-123")

    then:
    thrown SpinnakerHttpException
  }

  def "stopJob handles UnsupportedOperationException from stopQueuedBuild gracefully"() {
    given:
    def stoppableService = Mock(StoppableBuildOperations)
    buildServices.getService("my-master") >> stoppableService
    stoppableService.stopQueuedBuild(_) >> { throw new UnsupportedOperationException("not supported") }

    when:
    controller.stopJob("my-master", 0L, "my-job", "queue-123")

    then:
    noExceptionThrown()
  }

  def "stopJob throws InvalidRequestException for non-stoppable services"() {
    given:
    def nonStoppableService = Mock(BuildOperations)
    buildServices.getService("my-master") >> nonStoppableService

    when:
    controller.stopJob("my-master", 42L, "my-job", "queue-123")

    then:
    thrown InvalidRequestException
  }

  def "stopJob throws NotFoundException for unknown master"() {
    given:
    buildServices.getService("unknown") >> null

    when:
    controller.stopJob("unknown", 42L, "my-job", "queue-123")

    then:
    thrown NotFoundException
  }

  /**
   * Helper interface that combines BuildOperations and StoppableBuildService
   * so Spock can mock both on one object.
   */
  static interface StoppableBuildOperations extends BuildOperations, StoppableBuildService {}
}
