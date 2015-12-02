/*
 * Copyright 2015 Netflix, Inc.
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

package com.netflix.spinnaker.kato.helpers

import spock.lang.Specification

class OperationPollerSpec extends Specification {

  int asyncOperationTimeoutSecondsDefault = 60
  int asyncOperationMaxPollingIntervalSeconds = 8

  void "waitForOperation should query the operation at least once"() {
    setup:
      def operationPoller =
        new OperationPoller(
            asyncOperationTimeoutSecondsDefault,
            asyncOperationMaxPollingIntervalSeconds
        )

    expect:
      operationPoller.pollOperation({[test: 'value']}, {true}, 0) == [test: 'value']
  }

  void "waitForOperation should return null on timeout"() {
    setup:
      def operationPoller =
          new OperationPoller(
              properties.asyncOperationTimeoutSecondsDefault,
              properties.asyncOperationMaxPollingIntervalSeconds
          )

    expect:
      operationPoller.pollOperation({[test: 'pending']}, {false}, 0) == null
  }

  void "waitForOperation should increment poll interval properly and retry until timeout"() {
    setup:
      def threadSleeperMock = Mock(OperationPoller.ThreadSleeper)
      def operationPoller =
        new OperationPoller(
            asyncOperationTimeoutSecondsDefault,
            asyncOperationMaxPollingIntervalSeconds,
            threadSleeperMock
        )

    when:
      // Even though the timeout is set to 10 seconds, it will poll for 12 seconds.
      operationPoller.pollOperation({[test: 'pending']}, {false}, 10)

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
      def threadSleeperMock = Mock(OperationPoller.ThreadSleeper)
      def operationPoller =
        new OperationPoller(
            asyncOperationTimeoutSecondsDefault,
            3,
            threadSleeperMock
        )

    when:
      // Even though the timeout is set to 10 seconds, it will poll for 13 seconds.
      operationPoller.pollOperation({[test: 'pending']}, {false}, 10)

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
}
