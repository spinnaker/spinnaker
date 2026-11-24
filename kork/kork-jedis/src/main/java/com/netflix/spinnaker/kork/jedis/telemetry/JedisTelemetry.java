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

import com.netflix.spectator.api.Registry;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.Timer;
import java.util.concurrent.Callable;

/** Records the Redis command metrics shared by the instrumented Jedis client and pipeline. */
interface JedisTelemetry {

  void recordPayloadSize(String poolName, String command, boolean pipelined, long size);

  <T> T time(String poolName, String command, boolean pipelined, Callable<T> action)
      throws Exception;

  void time(String poolName, String command, boolean pipelined, Runnable action);

  void countInvocation(String poolName, String command, boolean pipelined, boolean success);

  static JedisTelemetry micrometer(MeterRegistry registry) {
    return new MicrometerJedisTelemetry(registry);
  }

  /** TODO: remove with the deprecated Spectator constructors (Spectator removal, 2027.0.0). */
  static JedisTelemetry spectator(Registry registry) {
    return new SpectatorJedisTelemetry(registry);
  }

  class MicrometerJedisTelemetry implements JedisTelemetry {
    private static final String PREFIX = "redis.command";

    private final MeterRegistry registry;

    MicrometerJedisTelemetry(MeterRegistry registry) {
      this.registry = registry;
    }

    @Override
    public void recordPayloadSize(String poolName, String command, boolean pipelined, long size) {
      DistributionSummary.builder(PREFIX + ".payloadSize." + command)
          .tags(tags(poolName, pipelined))
          .register(registry)
          .record(size);
    }

    @Override
    public <T> T time(String poolName, String command, boolean pipelined, Callable<T> action)
        throws Exception {
      return timer(poolName, command, pipelined).recordCallable(action);
    }

    @Override
    public void time(String poolName, String command, boolean pipelined, Runnable action) {
      timer(poolName, command, pipelined).record(action);
    }

    @Override
    public void countInvocation(
        String poolName, String command, boolean pipelined, boolean success) {
      Counter.builder(PREFIX + ".invocation." + command)
          .tags(tags(poolName, pipelined).and("success", String.valueOf(success)))
          .register(registry)
          .increment();
    }

    private Timer timer(String poolName, String command, boolean pipelined) {
      return Timer.builder(PREFIX + ".latency." + command)
          .tags(tags(poolName, pipelined))
          .register(registry);
    }

    private static Tags tags(String poolName, boolean pipelined) {
      return Tags.of("poolName", poolName, "pipelined", String.valueOf(pipelined));
    }
  }

  class SpectatorJedisTelemetry implements JedisTelemetry {
    private final Registry registry;

    SpectatorJedisTelemetry(Registry registry) {
      this.registry = registry;
    }

    @Override
    public void recordPayloadSize(String poolName, String command, boolean pipelined, long size) {
      registry
          .distributionSummary(
              TelemetryHelper.payloadSizeId(registry, poolName, command, pipelined))
          .record(size);
    }

    @Override
    public <T> T time(String poolName, String command, boolean pipelined, Callable<T> action)
        throws Exception {
      return registry
          .timer(TelemetryHelper.timerId(registry, poolName, command, pipelined))
          .recordCallable(action);
    }

    @Override
    public void time(String poolName, String command, boolean pipelined, Runnable action) {
      registry
          .timer(TelemetryHelper.timerId(registry, poolName, command, pipelined))
          .recordRunnable(action);
    }

    @Override
    public void countInvocation(
        String poolName, String command, boolean pipelined, boolean success) {
      registry
          .counter(TelemetryHelper.invocationId(registry, poolName, command, pipelined, success))
          .increment();
    }
  }
}
