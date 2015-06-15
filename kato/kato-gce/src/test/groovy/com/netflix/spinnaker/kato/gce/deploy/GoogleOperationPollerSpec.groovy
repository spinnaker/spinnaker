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

package com.netflix.spinnaker.kato.gce.deploy

import com.google.api.services.compute.model.Operation
import com.netflix.spinnaker.kato.gce.deploy.config.GoogleConfig
import spock.lang.Specification

class GoogleOperationPollerSpec extends Specification {

  void "waitForOperation should query the operation at least once"() {
    setup:
      def googleOperationPoller =
        new GoogleOperationPoller(googleConfigurationProperties: new GoogleConfig.GoogleConfigurationProperties())

    expect:
      googleOperationPoller.waitForOperation({return new Operation(status: "DONE")}, 0) == new Operation(status: "DONE")
  }

  void "waitForOperation should return null on timeout"() {
    setup:
      def googleOperationPoller =
        new GoogleOperationPoller(googleConfigurationProperties: new GoogleConfig.GoogleConfigurationProperties())

    expect:
      googleOperationPoller.waitForOperation({return new Operation(status: "PENDING")}, 0) == null
  }

  void "waitForOperation should retry until timeout"() {
    setup:
      def googleOperationPoller =
        new GoogleOperationPoller(googleConfigurationProperties: new GoogleConfig.GoogleConfigurationProperties())
      def getOperationMock = Mock(Closure)

    when:
      Operation operation = googleOperationPoller.waitForOperation(getOperationMock, 10)

    then:
      (2.._) * getOperationMock.call() >> new Operation(status: "PENDING")
      operation == null
  }
}
