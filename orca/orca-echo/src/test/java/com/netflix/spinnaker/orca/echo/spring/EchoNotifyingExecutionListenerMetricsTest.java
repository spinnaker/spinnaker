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

package com.netflix.spinnaker.orca.echo.spring;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spectator.micrometer.MicrometerRegistry;
import com.netflix.spinnaker.orca.api.pipeline.models.ExecutionStatus;
import com.netflix.spinnaker.orca.echo.EchoService;
import com.netflix.spinnaker.orca.front50.Front50Service;
import com.netflix.spinnaker.orca.pipeline.model.PipelineExecutionImpl;
import com.netflix.spinnaker.orca.pipeline.util.ContextParameterProcessor;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import retrofit2.mock.Calls;

/** Pins the Echo event metrics {@link EchoNotifyingExecutionListener} publishes. */
class EchoNotifyingExecutionListenerMetricsTest {

  private final SimpleMeterRegistry meters = new SimpleMeterRegistry();
  private final EchoService echoService = mock(EchoService.class);
  private final EchoNotifyingExecutionListener listener =
      new EchoNotifyingExecutionListener(
          echoService,
          mock(Front50Service.class),
          new ObjectMapper(),
          new ContextParameterProcessor(),
          new MicrometerRegistry(meters));
  private final PipelineExecutionImpl execution =
      PipelineExecutionImpl.newOrchestration("test-app");

  @Test
  void successfulEventsAreCountedAndTimedPerPhase() {
    when(echoService.recordEvent(anyMap())).thenAnswer(invocation -> Calls.response((Void) null));

    listener.beforeExecution(null, execution);
    listener.afterExecution(null, execution, ExecutionStatus.SUCCEEDED, true);

    for (String phase : new String[] {"before", "after"}) {
      assertThat(eventCounter("echo.events.count", phase).count()).isEqualTo(1);
      Timer duration = meters.find("echo.events.duration").tags("execution", phase).timer();
      assertThat(duration).isNotNull();
      assertThat(Tags.of(duration.getId().getTags())).isEqualTo(Tags.of("execution", phase));
      assertThat(duration.count()).isEqualTo(1);
    }
    assertThat(meters.find("echo.events.error").counters()).isEmpty();
  }

  @Test
  void failedEventIsCountedAsAnErrorAndNotTimed() {
    when(echoService.recordEvent(anyMap())).thenThrow(new IllegalStateException("boom"));

    listener.beforeExecution(null, execution);

    assertThat(eventCounter("echo.events.error", "before").count()).isEqualTo(1);
    assertThat(meters.find("echo.events.count").counters()).isEmpty();
    assertThat(meters.find("echo.events.duration").timers()).isEmpty();
  }

  /**
   * The counters come from Spectator's {@code IntervalCounter}, which tags them {@code
   * statistic=count}.
   */
  private Counter eventCounter(String name, String phase) {
    Counter counter = meters.find(name).tags("execution", phase).counter();
    assertThat(counter).isNotNull();
    assertThat(Tags.of(counter.getId().getTags()))
        .isEqualTo(Tags.of("execution", phase, "statistic", "count"));
    return counter;
  }
}
