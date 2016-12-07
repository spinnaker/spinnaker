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

package com.netflix.spinnaker.clouddriver.google.deploy

import com.google.api.services.compute.model.Operation
import com.netflix.spinnaker.clouddriver.google.config.GoogleConfigurationProperties
import spock.lang.Shared
import spock.lang.Specification

class GoogleOperationPollerSpec extends Specification {

  @Shared SafeRetry safeRetry

  def setupSpec() {
    safeRetry = new SafeRetry(maxRetries: 10, maxWaitInterval: 60000, retryIntervalBase: 0)
  }

  void "waitForOperation should query the operation at least once"() {
    setup:
      def threadSleeperMock = Mock(GoogleOperationPoller.ThreadSleeper)
      def googleOperationPoller =
        new GoogleOperationPoller(
          googleConfigurationProperties: new GoogleConfigurationProperties(),
          threadSleeper: threadSleeperMock,
          safeRetry: safeRetry
        )

    expect:
      googleOperationPoller.waitForOperation({return new Operation(status: "DONE")}, 0) == new Operation(status: "DONE")
  }

  void "waitForOperation should return null on timeout"() {
    setup:
      def threadSleeperMock = Mock(GoogleOperationPoller.ThreadSleeper)
      def googleOperationPoller =
        new GoogleOperationPoller(
          googleConfigurationProperties: new GoogleConfigurationProperties(),
          threadSleeper: threadSleeperMock,
          safeRetry: safeRetry
        )

    expect:
      googleOperationPoller.waitForOperation({return new Operation(status: "PENDING")}, 0) == null
  }

  void "waitForOperation should increment poll interval properly and retry until timeout"() {
    setup:
      def threadSleeperMock = Mock(GoogleOperationPoller.ThreadSleeper)
      def googleOperationPoller =
        new GoogleOperationPoller(
          googleConfigurationProperties: new GoogleConfigurationProperties(),
          threadSleeper: threadSleeperMock,
          safeRetry: safeRetry
        )

    when:
      // Even though the timeout is set to 10 seconds, it will poll for 12 seconds.
      googleOperationPoller.waitForOperation({return new Operation(status: "PENDING")}, 10)

    then:
      1 * threadSleeperMock.sleep(1)

    then:
      1 * threadSleeperMock.sleep(1)

    then:
      1 * threadSleeperMock.sleep(2)

    then:
      1 * threadSleeperMock.sleep(3)

    then:
      1 * threadSleeperMock.sleep(5)
  }

  void "waitForOperation should respect asyncOperationMaxPollingIntervalSeconds"() {
    setup:
      def threadSleeperMock = Mock(GoogleOperationPoller.ThreadSleeper)
      def googleOperationPoller =
        new GoogleOperationPoller(
          googleConfigurationProperties: new GoogleConfigurationProperties(asyncOperationMaxPollingIntervalSeconds: 3),
          threadSleeper: threadSleeperMock,
          safeRetry: safeRetry
        )

    when:
      // Even though the timeout is set to 10 seconds, it will poll for 13 seconds.
      googleOperationPoller.waitForOperation({return new Operation(status: "PENDING")}, 10)

    then:
      1 * threadSleeperMock.sleep(1)

    then:
      1 * threadSleeperMock.sleep(1)

    then:
      1 * threadSleeperMock.sleep(2)

    then:
      1 * threadSleeperMock.sleep(3)

    then:
      1 * threadSleeperMock.sleep(3)

    then:
      1 * threadSleeperMock.sleep(3)
  }

  void "waitForOperation should retry on SocketTimeoutException"() {
    setup:
      def threadSleeperMock = Mock(GoogleOperationPoller.ThreadSleeper)
      def closure = Mock(Closure)
      def googleOperationPoller =
        new GoogleOperationPoller(
          googleConfigurationProperties: new GoogleConfigurationProperties(asyncOperationMaxPollingIntervalSeconds: 3),
          threadSleeper: threadSleeperMock,
          safeRetry: safeRetry
        )

    when:
      googleOperationPoller.waitForOperation(closure, 10)

    then:
      1 * closure() >> {throw new SocketTimeoutException("Read timed out")}
      1 * threadSleeperMock.sleep(1)

    then:
      1 * closure() >> {return new Operation(status: "PENDING")}
      1 * threadSleeperMock.sleep(1)

    then:
      1 * closure() >> {return new Operation(status: "DONE")}
  }
}
