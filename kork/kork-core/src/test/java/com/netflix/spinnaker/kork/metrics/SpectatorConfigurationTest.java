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

import com.netflix.spectator.api.Registry;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.micrometer.metrics.autoconfigure.CompositeMeterRegistryAutoConfiguration;
import org.springframework.boot.micrometer.metrics.autoconfigure.MetricsAutoConfiguration;
import org.springframework.boot.micrometer.metrics.autoconfigure.export.simple.SimpleMetricsExportAutoConfiguration;
import org.springframework.boot.micrometer.metrics.autoconfigure.jvm.JvmMetricsAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class SpectatorConfigurationTest {

  private final ApplicationContextRunner runner =
      new ApplicationContextRunner()
          .withConfiguration(
              AutoConfigurations.of(
                  MetricsAutoConfiguration.class,
                  CompositeMeterRegistryAutoConfiguration.class,
                  SimpleMetricsExportAutoConfiguration.class,
                  JvmMetricsAutoConfiguration.class))
          .withUserConfiguration(SpectatorConfiguration.class);

  @Test
  void spectatorRegistryWritesToTheMicrometerRegistry() {
    runner.run(
        context -> {
          context.getBean(Registry.class).counter("test.counter").increment();

          assertThat(context.getBean(MeterRegistry.class).find("test.counter").counter().count())
              .isEqualTo(1);
        });
  }

  @Test
  void jvmMetricsComeOnlyFromSpringBoot() {
    runner.run(
        context -> {
          List<Meter> meters = context.getBean(MeterRegistry.class).getMeters();

          assertThat(meters)
              .extracting(meter -> meter.getId().getName())
              .contains("jvm.memory.used", "jvm.buffer.count", "jvm.threads.live")
              .doesNotContain(
                  "jvm.classloading.classesLoaded",
                  "jvm.thread.threadCount",
                  "jvm.compilation.compilationTime",
                  "jvm.buffer.memoryUsed");
          assertThat(meters)
              .filteredOn(meter -> meter.getId().getName().startsWith("jvm.memory."))
              .allSatisfy(meter -> assertThat(meter.getId().getTag("memtype")).isNull());
        });
  }
}
