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

package com.netflix.spinnaker.kork.metrics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.netflix.spectator.api.histogram.PercentileTimer;
import com.netflix.spectator.micrometer.MicrometerRegistry;
import io.micrometer.core.instrument.Clock;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.registry.otlp.OtlpConfig;
import io.micrometer.registry.otlp.OtlpMeterRegistry;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.StaticApplicationContext;

class MeterRegistryAssertionsTest {

  private final SimpleMeterRegistry registry = new SimpleMeterRegistry();

  @Test
  void passesForPlainMicrometerMeters() {
    registry.timer("test.duration", "type", "a").record(Duration.ofMillis(5));
    registry.counter("test.count", "type", "a").increment();

    assertThatCode(() -> MeterRegistryAssertions.assertExportableByDefault(context(registry)))
        .doesNotThrowAnyException();
  }

  @Test
  void failsWhenOneNameHasMoreThanOneMeterType() {
    registry.timer("test.duration", "type", "a").record(Duration.ofMillis(5));
    registry.counter("test.duration", "type", "b").increment();

    assertThatThrownBy(() -> MeterRegistryAssertions.assertExportableByDefault(context(registry)))
        .isInstanceOf(AssertionError.class)
        .hasMessageContaining("test.duration has more than one meter type");
  }

  @Test
  void failsOnSpectatorPercentileCounters() {
    MicrometerRegistry spectator = new MicrometerRegistry(registry);
    PercentileTimer.get(spectator, spectator.createId("test.duration"))
        .record(Duration.ofMillis(5));

    assertThat(registry.find("test.duration").tag("statistic", "percentile").counters())
        .isNotEmpty();
    assertThatThrownBy(() -> MeterRegistryAssertions.assertExportableByDefault(context(registry)))
        .isInstanceOf(AssertionError.class)
        .hasMessageContaining("test.duration has Spectator percentile counters");
  }

  @Test
  void failsWhenAnOtlpRegistryIsPresent() {
    OtlpMeterRegistry otlp = new OtlpMeterRegistry(OtlpConfig.DEFAULT, Clock.SYSTEM);
    try {
      assertThatThrownBy(() -> MeterRegistryAssertions.assertExportableByDefault(context(otlp)))
          .isInstanceOf(AssertionError.class)
          .hasMessageContaining("OTLP export is on by default");
    } finally {
      otlp.close();
    }
  }

  private static StaticApplicationContext context(MeterRegistry registry) {
    StaticApplicationContext context = new StaticApplicationContext();
    context.getBeanFactory().registerSingleton("meterRegistry", registry);
    context.refresh();
    return context;
  }
}
