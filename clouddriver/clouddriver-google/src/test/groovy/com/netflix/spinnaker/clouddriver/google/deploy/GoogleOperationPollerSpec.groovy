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

import com.netflix.spectator.api.DefaultRegistry
import com.netflix.spectator.api.ManualClock
import com.netflix.spinnaker.clouddriver.google.config.GoogleConfigurationProperties

import spock.lang.Shared
import spock.lang.Specification

class GoogleOperationPollerSpec extends Specification {
  private static final String METRIC_NAME = GoogleOperationPoller.METRIC_NAME
  def TEST_TAGS = [randomTag: "randomValue", anotherTag: "anotherValue"]
  def BASE_PHASE = "TestPhase"

  @Shared SafeRetry safeRetry

  def setupSpec() {
    safeRetry = SafeRetry.withoutDelay()
  }

  void "waitForOperation should query the operation at least once"() {
    setup:
      // In this simple test we'll show a non-trival metricId and clock.
      // For the remaining tests in this module, we'll use simple ones having proved here it doesnt matter.
      def clock = new ManualClock(777, 100)  // walltime=777 isnt used.
      def registry = new DefaultRegistry(clock)
      def metricId = registry.createId(METRIC_NAME, TEST_TAGS)
      def actualMetricId = metricId.withTag("status", "DONE")
      def threadSleeperMock = Mock(GoogleOperationPoller.ThreadSleeper)
      def googleOperationPoller =
        new GoogleOperationPoller(
          googleConfigurationProperties: new GoogleConfigurationProperties(),
          threadSleeper: threadSleeperMock,
          registry: registry,
          safeRetry: safeRetry
        )

    expect:
      googleOperationPoller.waitForOperation({clock.setMonotonicTime(123 + 100); return new Operation(status: "DONE")}, TEST_TAGS, BASE_PHASE, 0) == new Operation(status: "DONE")
      registry.timer(actualMetricId).count() == 1
      registry.timer(actualMetricId).totalTime() == 123
  }

  void "waitForOperation should return null on timeout"() {
    setup:
      def clock = new ManualClock()
      def registry = new DefaultRegistry(clock)
      def metricId = registry.createId(METRIC_NAME, TEST_TAGS)
      def actualMetricId = metricId.withTag("status", "TIMEOUT")
      def threadSleeperMock = Mock(GoogleOperationPoller.ThreadSleeper)
      def googleOperationPoller =
        new GoogleOperationPoller(
          googleConfigurationProperties: new GoogleConfigurationProperties(),
          threadSleeper: threadSleeperMock,
          registry: registry,
          safeRetry: safeRetry
        )

    expect:
      googleOperationPoller.waitForOperation({clock.setMonotonicTime(123); return new Operation(status: "PENDING")}, TEST_TAGS, BASE_PHASE, 0) == null
  }

  void "waitForOperation should increment poll interval properly and retry until timeout"() {
    setup:
      def clock = new ManualClock()
      def registry = new DefaultRegistry(clock)
      def metricId = registry.createId(METRIC_NAME, TEST_TAGS)
      def actualMetricId = metricId.withTag("status", "TIMEOUT")
      def threadSleeperMock = Mock(GoogleOperationPoller.ThreadSleeper)
      def googleOperationPoller =
        new GoogleOperationPoller(
          googleConfigurationProperties: new GoogleConfigurationProperties(),
          threadSleeper: threadSleeperMock,
          registry: registry,
          safeRetry: safeRetry
        )

    when:
      // Even though the timeout is set to 10 seconds, it will poll for 12 seconds.
      googleOperationPoller.waitForOperation({clock.setMonotonicTime(123); return new Operation(status: "PENDING")}, TEST_TAGS, BASE_PHASE, 10)

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
      registry.timer(actualMetricId).count() == 1
      registry.timer(actualMetricId).totalTime() == 123
  }

  void "waitForOperation should respect asyncOperationMaxPollingIntervalSeconds"() {
    setup:
      def clock = new ManualClock()
      def registry = new DefaultRegistry(clock)
      def metricId = registry.createId(METRIC_NAME, TEST_TAGS)
      def actualMetricId = metricId.withTag("status", "TIMEOUT")
      def threadSleeperMock = Mock(GoogleOperationPoller.ThreadSleeper)
      def googleOperationPoller =
        new GoogleOperationPoller(
          googleConfigurationProperties: new GoogleConfigurationProperties(asyncOperationMaxPollingIntervalSeconds: 3),
          threadSleeper: threadSleeperMock,
          registry: registry,
          safeRetry: safeRetry
        )

    when:
      // Even though the timeout is set to 10 seconds, it will poll for 13 seconds.
      googleOperationPoller.waitForOperation({clock.setMonotonicTime(123); return new Operation(status: "PENDING")}, TEST_TAGS, BASE_PHASE, 10)

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
      registry.timer(actualMetricId).count() == 1
      registry.timer(actualMetricId).totalTime() == 123
  }

  void "waitForOperation should retry on SocketTimeoutException"() {
    setup:
      def clock = new ManualClock()
      def registry = new DefaultRegistry(clock)
      def metricId = registry.createId(METRIC_NAME, TEST_TAGS)
      def actualMetricId = metricId.withTags("status", "DONE")
      def threadSleeperMock = Mock(GoogleOperationPoller.ThreadSleeper)
      def closure = Mock(Closure)
      def googleOperationPoller =
        new GoogleOperationPoller(
          googleConfigurationProperties: new GoogleConfigurationProperties(asyncOperationMaxPollingIntervalSeconds: 3),
          threadSleeper: threadSleeperMock,
          registry: registry,
          safeRetry: safeRetry
        )

    when:
      googleOperationPoller.waitForOperation(closure, TEST_TAGS, BASE_PHASE, 10)

    then:
      1 * closure() >> {throw new SocketTimeoutException("Read timed out")}
      1 * threadSleeperMock.sleep(1)

    then:
      1 * closure() >> {return new Operation(status: "PENDING")}
      1 * threadSleeperMock.sleep(1)

    then:
      1 * closure() >> {clock.setMonotonicTime(321); return new Operation(status: "DONE")}
      registry.timer(actualMetricId).count() == 1
      registry.timer(actualMetricId).totalTime() == 321
  }
}
