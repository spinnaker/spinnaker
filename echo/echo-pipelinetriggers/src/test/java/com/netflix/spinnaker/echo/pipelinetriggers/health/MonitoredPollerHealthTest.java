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

package com.netflix.spinnaker.echo.pipelinetriggers.health;

import static java.time.Instant.now;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.boot.actuate.health.Status.DOWN;
import static org.springframework.boot.actuate.health.Status.UP;

import com.netflix.spinnaker.echo.pipelinetriggers.MonitoredPoller;
import java.time.Instant;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.boot.actuate.health.Status;

class MonitoredPollerHealthTest {
  private static final Instant GOOD_TIMESTAMP = now().minusSeconds(30);
  private static final Instant BAD_TIMESTAMP = now().minusSeconds(61);

  @ParameterizedTest
  @MethodSource("healthScenarios")
  void healthReflectsPollerState(
      boolean running, Instant lastPollTimestamp, Status expectedStatus) {
    MonitoredPoller poller = mock(MonitoredPoller.class);
    when(poller.isRunning()).thenReturn(running);
    when(poller.getLastPollTimestamp()).thenReturn(lastPollTimestamp);
    when(poller.isInitialized()).thenReturn(lastPollTimestamp != null);

    MonitoredPollerHealth health = new MonitoredPollerHealth(poller);

    assertThat(health.health().getStatus()).isEqualTo(expectedStatus);
  }

  private static Stream<Arguments> healthScenarios() {
    return Stream.of(
        Arguments.of(false, null, DOWN),
        Arguments.of(false, GOOD_TIMESTAMP, UP),
        Arguments.of(true, null, DOWN),
        Arguments.of(true, GOOD_TIMESTAMP, UP),
        Arguments.of(true, BAD_TIMESTAMP, UP));
  }
}
