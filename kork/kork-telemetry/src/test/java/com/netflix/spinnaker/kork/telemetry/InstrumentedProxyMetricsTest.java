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

package com.netflix.spinnaker.kork.telemetry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.netflix.spectator.api.DefaultRegistry;
import com.netflix.spinnaker.kork.annotations.Metered;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Pins the metric names and tags {@link InstrumentedProxy} publishes per proxied method. */
class InstrumentedProxyMetricsTest {

  private final SimpleMeterRegistry meters = new SimpleMeterRegistry();
  private final Contract proxy =
      InstrumentedProxy.proxy(meters, new ContractImpl(), "test", Map.of("env", "test"));

  @Test
  void successfulCallCountsAndTimesWithResultTag() {
    proxy.doStuff();

    // The result tag is the enum constant name, not ResultValue's lowercase value.
    Tags expected = Tags.of("env", "test", "result", "SUCCESS");
    assertThat(onlyCounter("test.doStuff.invocations", expected).count()).isEqualTo(1);
    assertThat(onlyTimer("test.doStuff.timing", expected).count()).isEqualTo(1);
  }

  @Test
  void failedCallIsTaggedFailureAndRethrowsTheOriginalException() {
    assertThatThrownBy(proxy::fail).isInstanceOf(IllegalStateException.class);

    Tags expected = Tags.of("env", "test", "result", "FAILURE");
    assertThat(onlyCounter("test.fail.invocations", expected).count()).isEqualTo(1);
    assertThat(onlyTimer("test.fail.timing", expected).count()).isEqualTo(1);
  }

  @Test
  void parameterCountIsAppendedToTheMethodName() {
    proxy.withArgument("value");

    Tags expected = Tags.of("env", "test", "result", "SUCCESS");
    assertThat(onlyCounter("test.withArgument1.invocations", expected).count()).isEqualTo(1);
    assertThat(onlyTimer("test.withArgument1.timing", expected).count()).isEqualTo(1);
  }

  @Test
  void meteredMetricNameAndTagsOverrideTheDefaults() {
    proxy.renamed();

    // The override name comes before the namespace.
    Tags expected = Tags.of("env", "test", "custom", "yes", "result", "SUCCESS");
    assertThat(onlyCounter("customName.test.invocations", expected).count()).isEqualTo(1);
    assertThat(onlyTimer("customName.test.timing", expected).count()).isEqualTo(1);
  }

  @Test
  void ignoredMethodRecordsNothing() {
    proxy.ignored();

    assertThat(meters.getMeters()).isEmpty();
  }

  @Test
  void timingHasNoPercentileCounters() {
    proxy.doStuff();
    assertThatThrownBy(proxy::fail).isInstanceOf(IllegalStateException.class);

    assertThat(meters.getMeters())
        .allSatisfy(meter -> assertThat(meter.getId().getTag("statistic")).isNull());
    assertThat(meters.find("test.doStuff.timing").counters()).isEmpty();
  }

  @Test
  @SuppressWarnings("removal")
  void deprecatedSpectatorOverloadRecordsTheSameMetrics() {
    DefaultRegistry spectator = new DefaultRegistry();
    Contract deprecated =
        InstrumentedProxy.proxy(spectator, new ContractImpl(), "test", Map.of("env", "test"));

    deprecated.doStuff();

    Map<String, String> expected = Map.of("env", "test", "result", "SUCCESS");
    assertThat(spectator.counter(spectator.createId("test.doStuff.invocations", expected)).count())
        .isEqualTo(1);
    assertThat(spectator.timer(spectator.createId("test.doStuff.timing", expected)).count())
        .isEqualTo(1);
    assertThat(spectator.stream())
        .noneMatch(meter -> meter.id().tags().toString().contains("percentile"));
  }

  private Counter onlyCounter(String name, Tags expectedTags) {
    assertThat(meters.find(name).counters()).hasSize(1);
    Counter counter = meters.find(name).counter();
    assertThat(Tags.of(counter.getId().getTags())).isEqualTo(expectedTags);
    return counter;
  }

  private Timer onlyTimer(String name, Tags expectedTags) {
    assertThat(meters.find(name).timers()).hasSize(1);
    Timer timer = meters.find(name).timer();
    assertThat(Tags.of(timer.getId().getTags())).isEqualTo(expectedTags);
    return timer;
  }

  public interface Contract {
    void doStuff();

    void fail();

    void withArgument(String value);

    @Metered(
        metricName = "customName",
        tags = {"custom", "yes"})
    void renamed();

    @Metered(ignore = true)
    void ignored();
  }

  public static class ContractImpl implements Contract {
    @Override
    public void doStuff() {}

    @Override
    public void fail() {
      throw new IllegalStateException("boom");
    }

    @Override
    public void withArgument(String value) {}

    @Override
    public void renamed() {}

    @Override
    public void ignored() {}
  }
}
