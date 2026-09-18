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

import io.micrometer.core.instrument.MockClock
import io.micrometer.core.instrument.Tag
import io.micrometer.core.instrument.Tags
import io.micrometer.core.instrument.simple.SimpleConfig
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import com.netflix.spinnaker.clouddriver.google.config.GoogleConfigurationProperties

import spock.lang.Shared
import spock.lang.Specification

import java.util.concurrent.TimeUnit

class GoogleOperationPollerSpec extends Specification {
  private static final String METRIC_NAME = GoogleOperationPoller.METRIC_NAME
  def TEST_TAGS = [randomTag: "randomValue", anotherTag: "anotherValue"]
  def BASE_PHASE = "TestPhase"

  @Shared SafeRetry safeRetry

  def setupSpec() {
    safeRetry = SafeRetry.withoutDelay()
  }

  private static Tags toTags(Map<String, String> map) {
    Tags.of(map.collect { k, v -> Tag.of(k.toString(), v.toString()) })
  }

  // MockClock only supports relative advancement (add()), whereas Spectator's ManualClock
  // allowed setting an absolute monotonic time. This idempotently advances the clock to
  // targetNanos, so that repeated invocations (e.g. from a retry loop) don't cumulatively
  // over-advance it. targetNanos must be at or after the clock's current time (e.g. computed
  // as clock.monotonicTime() + delta before the clock is ever advanced), since MockClock (unlike
  // Spectator's ManualClock) doesn't support moving backwards.
  private static void setMonotonicTime(MockClock clock, long targetNanos) {
    long delta = targetNanos - clock.monotonicTime()
    if (delta > 0) {
      clock.add(delta, TimeUnit.NANOSECONDS)
    }
  }

  void "waitForOperation should query the operation at least once"() {
    setup:
      // In this simple test we'll show a non-trival tag set and clock.
      // For the remaining tests in this module, we'll use simple ones having proved here it doesnt matter.
      def clock = new MockClock()
      def registry = new SimpleMeterRegistry(SimpleConfig.DEFAULT, clock)
      def actualTags = toTags(TEST_TAGS).and("status", "DONE")
      def threadSleeperMock = Mock(GoogleOperationPoller.ThreadSleeper)
      def googleOperationPoller =
        new GoogleOperationPoller(
          googleConfigurationProperties: new GoogleConfigurationProperties(),
          threadSleeper: threadSleeperMock,
          registry: registry,
          safeRetry: safeRetry
        )

    expect:
      googleOperationPoller.waitForOperation({clock.add(123, TimeUnit.NANOSECONDS); return new Operation(status: "DONE")}, TEST_TAGS, BASE_PHASE, 0) == new Operation(status: "DONE")
      registry.timer(METRIC_NAME, actualTags).count() == 1
      registry.timer(METRIC_NAME, actualTags).totalTime(TimeUnit.NANOSECONDS) == 123
  }

  void "waitForOperation should return null on timeout"() {
    setup:
      def clock = new MockClock()
      def registry = new SimpleMeterRegistry(SimpleConfig.DEFAULT, clock)
      def threadSleeperMock = Mock(GoogleOperationPoller.ThreadSleeper)
      def googleOperationPoller =
        new GoogleOperationPoller(
          googleConfigurationProperties: new GoogleConfigurationProperties(),
          threadSleeper: threadSleeperMock,
          registry: registry,
          safeRetry: safeRetry
        )

    expect:
      googleOperationPoller.waitForOperation({clock.add(123, TimeUnit.NANOSECONDS); return new Operation(status: "PENDING")}, TEST_TAGS, BASE_PHASE, 0) == null
  }

  void "waitForOperation should increment poll interval properly and retry until timeout"() {
    setup:
      def clock = new MockClock()
      def targetNanos = clock.monotonicTime() + 123
      def registry = new SimpleMeterRegistry(SimpleConfig.DEFAULT, clock)
      def actualTags = toTags(TEST_TAGS).and("status", "TIMEOUT")
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
      googleOperationPoller.waitForOperation({setMonotonicTime(clock, targetNanos); return new Operation(status: "PENDING")}, TEST_TAGS, BASE_PHASE, 10)

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
      registry.timer(METRIC_NAME, actualTags).count() == 1
      registry.timer(METRIC_NAME, actualTags).totalTime(TimeUnit.NANOSECONDS) == 123
  }

  void "waitForOperation should respect asyncOperationMaxPollingIntervalSeconds"() {
    setup:
      def clock = new MockClock()
      def targetNanos = clock.monotonicTime() + 123
      def registry = new SimpleMeterRegistry(SimpleConfig.DEFAULT, clock)
      def actualTags = toTags(TEST_TAGS).and("status", "TIMEOUT")
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
      googleOperationPoller.waitForOperation({setMonotonicTime(clock, targetNanos); return new Operation(status: "PENDING")}, TEST_TAGS, BASE_PHASE, 10)

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
      registry.timer(METRIC_NAME, actualTags).count() == 1
      registry.timer(METRIC_NAME, actualTags).totalTime(TimeUnit.NANOSECONDS) == 123
  }

  void "waitForOperation should retry on SocketTimeoutException"() {
    setup:
      def clock = new MockClock()
      def registry = new SimpleMeterRegistry(SimpleConfig.DEFAULT, clock)
      def actualTags = toTags(TEST_TAGS).and("status", "DONE")
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
      1 * closure() >> {clock.add(321, TimeUnit.NANOSECONDS); return new Operation(status: "DONE")}
      registry.timer(METRIC_NAME, actualTags).count() == 1
      registry.timer(METRIC_NAME, actualTags).totalTime(TimeUnit.NANOSECONDS) == 321
  }
}
