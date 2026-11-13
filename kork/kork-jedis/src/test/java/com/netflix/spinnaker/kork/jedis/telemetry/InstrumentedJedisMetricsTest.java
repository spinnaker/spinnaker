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

package com.netflix.spinnaker.kork.jedis.telemetry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.netflix.spectator.api.DefaultRegistry;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import redis.clients.jedis.Connection;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.Pipeline;
import redis.clients.jedis.Response;
import redis.clients.jedis.exceptions.JedisConnectionException;
import redis.clients.jedis.exceptions.JedisException;

/** Pins the metrics {@link InstrumentedJedis} and {@link InstrumentedPipeline} publish. */
class InstrumentedJedisMetricsTest {

  private static final String POOL = "test-pool";

  private final SimpleMeterRegistry meters = new SimpleMeterRegistry();
  private final MeterRegistry registry = meters;
  private final Jedis delegate = mock(Jedis.class);

  @Test
  void writeRecordsLatencyPayloadSizeAndSuccessfulInvocation() {
    when(delegate.set("test-key", "value")).thenReturn("OK");

    new InstrumentedJedis(registry, delegate, POOL).set("test-key", "value");

    Tags pool = Tags.of("poolName", POOL, "pipelined", "false");
    assertThat(onlyTimer("redis.command.latency.set", pool).count()).isEqualTo(1);
    DistributionSummary payload = onlySummary("redis.command.payloadSize.set", pool);
    assertThat(payload.count()).isEqualTo(1);
    assertThat(payload.totalAmount()).isEqualTo(5);
    assertThat(onlyCounter("redis.command.invocation.set", pool.and("success", "true")).count())
        .isEqualTo(1);
  }

  @Test
  void readRecordsLatencyAndInvocationButNoPayloadSize() {
    when(delegate.get("test-key")).thenReturn("value");

    new InstrumentedJedis(registry, delegate, POOL).get("test-key");

    Tags pool = Tags.of("poolName", POOL, "pipelined", "false");
    assertThat(onlyTimer("redis.command.latency.get", pool).count()).isEqualTo(1);
    assertThat(meters.find("redis.command.payloadSize.get").summaries()).isEmpty();
    assertThat(onlyCounter("redis.command.invocation.get", pool.and("success", "true")).count())
        .isEqualTo(1);
  }

  @Test
  void failedCommandCountsAFailedInvocationAndStillRecordsLatency() {
    JedisConnectionException failure = new JedisConnectionException("connection refused");
    when(delegate.get("test-key")).thenThrow(failure);

    InstrumentedJedis jedis = new InstrumentedJedis(registry, delegate, POOL);

    assertThatThrownBy(() -> jedis.get("test-key"))
        .isInstanceOf(JedisException.class)
        .hasCause(failure);
    Tags pool = Tags.of("poolName", POOL, "pipelined", "false");
    assertThat(onlyTimer("redis.command.latency.get", pool).count()).isEqualTo(1);
    assertThat(onlyCounter("redis.command.invocation.get", pool.and("success", "false")).count())
        .isEqualTo(1);
    assertThat(meters.find("redis.command.invocation.get").tags("success", "true").counters())
        .isEmpty();
  }

  @Test
  void defaultPoolNameIsUnnamed() {
    when(delegate.get("test-key")).thenReturn("value");

    new InstrumentedJedis(registry, delegate).get("test-key");

    assertThat(
            onlyTimer(
                    "redis.command.latency.get",
                    Tags.of("poolName", "unnamed", "pipelined", "false"))
                .count())
        .isEqualTo(1);
  }

  @Test
  @SuppressWarnings("unchecked")
  void pipelinedWriteIsTaggedPipelined() {
    Pipeline delegatePipeline = mock(Pipeline.class);
    when(delegate.getConnection()).thenReturn(mock(Connection.class));
    when(delegate.pipelined()).thenReturn(delegatePipeline);
    when(delegatePipeline.set("test-key", "value")).thenReturn(mock(Response.class));

    new InstrumentedPipeline(registry, delegate, POOL).set("test-key", "value");

    Tags pool = Tags.of("poolName", POOL, "pipelined", "true");
    assertThat(onlyTimer("redis.command.latency.set", pool).count()).isEqualTo(1);
    assertThat(onlySummary("redis.command.payloadSize.set", pool).totalAmount()).isEqualTo(5);
    assertThat(onlyCounter("redis.command.invocation.set", pool.and("success", "true")).count())
        .isEqualTo(1);
  }

  @Test
  @SuppressWarnings("unchecked")
  void pipelineFromTheClientRecordsIntoTheSameRegistry() {
    Pipeline delegatePipeline = mock(Pipeline.class);
    when(delegate.getConnection()).thenReturn(mock(Connection.class));
    when(delegate.pipelined()).thenReturn(delegatePipeline);
    when(delegatePipeline.set("test-key", "value")).thenReturn(mock(Response.class));

    new InstrumentedJedis(registry, delegate, POOL).pipelined().set("test-key", "value");

    assertThat(
            onlyTimer(
                    "redis.command.latency.pipelined",
                    Tags.of("poolName", POOL, "pipelined", "false"))
                .count())
        .isEqualTo(1);
    assertThat(
            onlyTimer("redis.command.latency.set", Tags.of("poolName", POOL, "pipelined", "true"))
                .count())
        .isEqualTo(1);
  }

  @Test
  void recordsNoPercentileCounters() {
    when(delegate.set("test-key", "value")).thenReturn("OK");

    new InstrumentedJedis(registry, delegate, POOL).set("test-key", "value");

    assertThat(meters.getMeters())
        .allSatisfy(meter -> assertThat(meter.getId().getTag("statistic")).isNull());
    assertThat(meters.find("redis.command.latency.set").counters()).isEmpty();
    assertThat(meters.find("redis.command.payloadSize.set").counters()).isEmpty();
  }

  @Test
  @SuppressWarnings("removal")
  void deprecatedSpectatorConstructorRecordsTheSameMetrics() {
    DefaultRegistry spectator = new DefaultRegistry();
    when(delegate.set("test-key", "value")).thenReturn("OK");

    new InstrumentedJedis(spectator, delegate, POOL).set("test-key", "value");

    String[] pool = {"poolName", POOL, "pipelined", "false"};
    assertThat(
            spectator.timer(spectator.createId("redis.command.latency.set").withTags(pool)).count())
        .isEqualTo(1);
    assertThat(
            spectator
                .distributionSummary(
                    spectator.createId("redis.command.payloadSize.set").withTags(pool))
                .totalAmount())
        .isEqualTo(5);
    assertThat(
            spectator
                .counter(
                    spectator
                        .createId("redis.command.invocation.set")
                        .withTags(pool)
                        .withTag("success", "true"))
                .count())
        .isEqualTo(1);
    assertThat(spectator.stream())
        .noneMatch(meter -> meter.id().tags().toString().contains("percentile"));
  }

  private Timer onlyTimer(String name, Tags expectedTags) {
    assertThat(meters.find(name).timers()).hasSize(1);
    Timer timer = meters.find(name).timer();
    assertThat(Tags.of(timer.getId().getTags())).isEqualTo(expectedTags);
    return timer;
  }

  private DistributionSummary onlySummary(String name, Tags expectedTags) {
    assertThat(meters.find(name).summaries()).hasSize(1);
    DistributionSummary summary = meters.find(name).summary();
    assertThat(Tags.of(summary.getId().getTags())).isEqualTo(expectedTags);
    return summary;
  }

  private Counter onlyCounter(String name, Tags expectedTags) {
    assertThat(meters.find(name).counters()).hasSize(1);
    Counter counter = meters.find(name).counter();
    assertThat(Tags.of(counter.getId().getTags())).isEqualTo(expectedTags);
    return counter;
  }
}
