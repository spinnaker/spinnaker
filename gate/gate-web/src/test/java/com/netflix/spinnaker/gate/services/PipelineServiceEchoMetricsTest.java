/*
 * Copyright 2026 Harness, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.gate.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.netflix.spinnaker.gate.services.internal.EchoService;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.HashMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import retrofit2.mock.Calls;

/** Pins the Echo event metrics {@link PipelineService#triggerViaEcho} publishes. */
class PipelineServiceEchoMetricsTest {

  private final SimpleMeterRegistry meters = new SimpleMeterRegistry();
  private final EchoService echoService = mock(EchoService.class);
  private final PipelineService pipelineService = new PipelineService();

  @BeforeEach
  void setUp() {
    pipelineService.setEchoService(echoService);
    pipelineService.setRegistry(meters);
    pipelineService.postConstruct();
  }

  @Test
  void successfulEventCountsAndTimesTheEvent() {
    when(echoService.postEvent(anyMap())).thenReturn(Calls.response((Void) null));

    pipelineService.triggerViaEcho("test-app", "test-pipeline", new HashMap<>());

    assertThat(eventCounter("echo.events.count").count()).isEqualTo(1);
    assertThat(eventCounter("echo.events.error").count()).isZero();
    Timer duration = meters.find("echo.events.duration").timer();
    assertThat(meters.find("echo.events.duration").timers()).hasSize(1);
    assertThat(duration.getId().getTags()).isEmpty();
    assertThat(duration.count()).isEqualTo(1);
  }

  @Test
  void failedEventCountsAnErrorAndRecordsNoDuration() {
    when(echoService.postEvent(anyMap())).thenThrow(new IllegalStateException("boom"));

    assertThatThrownBy(
            () -> pipelineService.triggerViaEcho("test-app", "test-pipeline", new HashMap<>()))
        .isInstanceOf(IllegalStateException.class);

    assertThat(eventCounter("echo.events.error").count()).isEqualTo(1);
    assertThat(eventCounter("echo.events.count").count()).isZero();
    assertThat(meters.find("echo.events.duration").timer().count()).isZero();
  }

  @Test
  void eventCountsArePlainCountersWithoutAgeGauges() {
    assertThat(meters.find("echo.events.count").meters()).hasSize(1);
    assertThat(meters.find("echo.events.error").meters()).hasSize(1);
    assertThat(meters.find("echo.events.count").gauges()).isEmpty();
    assertThat(meters.find("echo.events.error").gauges()).isEmpty();
  }

  private Counter eventCounter(String name) {
    assertThat(meters.find(name).counters()).hasSize(1);
    Counter counter = meters.find(name).counter();
    assertThat(counter.getId().getTags()).isEmpty();
    return counter;
  }
}
