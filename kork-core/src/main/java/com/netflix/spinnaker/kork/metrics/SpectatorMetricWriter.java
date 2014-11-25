/*
 * Copyright 2014 Netflix, Inc.
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

package com.netflix.spinnaker.kork.metrics;

import com.google.common.util.concurrent.AtomicDouble;
import com.netflix.spectator.api.ExtendedRegistry;
import com.netflix.spectator.api.Id;
import org.springframework.boot.actuate.metrics.Metric;
import org.springframework.boot.actuate.metrics.writer.Delta;
import org.springframework.boot.actuate.metrics.writer.MetricWriter;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

public class SpectatorMetricWriter implements MetricWriter {

  private final ExtendedRegistry registry;
  private final ConcurrentMap<Id, AtomicLong> counters = new ConcurrentHashMap<>();
  private final ConcurrentMap<Id, AtomicDouble> gauges = new ConcurrentHashMap<>();

  public SpectatorMetricWriter(ExtendedRegistry registry) {
    this.registry = registry;
  }

  @Override
  public void increment(Delta<?> delta) {
    if (delta.getName().startsWith("meter.")) {
      registry.counter(delta.getName()).increment(delta.getValue().longValue());
    } else {
      final Id id = registry.createId(delta.getName());
      final AtomicLong gauge = getCounterStorage(id);

      gauge.addAndGet(delta.getValue().longValue());
      registry.gauge(delta.getName(), gauge);
    }
  }

  @Override
  public void set(Metric<?> value) {
    if (value.getName().startsWith("histogram.")) {
      registry.distributionSummary(value.getName()).record(value.getValue().longValue());
    } else if (value.getName().startsWith("timer.")) {
      registry.timer(value.getName()).record(value.getValue().longValue(), TimeUnit.MILLISECONDS);
    } else {
      final Id id = registry.createId(value.getName());
      final AtomicDouble gauge = getGaugeStorage(id);
      gauge.set(value.getValue().doubleValue());

      registry.gauge(id, gauge);
    }

  }

  @Override
  public void reset(String metricName) {
    final Id id = registry.createId(metricName);
    counters.remove(id);
    gauges.remove(id);
  }


  private AtomicDouble getGaugeStorage(Id id) {
    final AtomicDouble newGauge = new AtomicDouble(0);
    final AtomicDouble existingGauge = gauges.putIfAbsent(id, newGauge);
    if (existingGauge == null) {
      return newGauge;
    }

    return existingGauge;
  }

  private AtomicLong getCounterStorage(Id id) {
    final AtomicLong newCounter= new AtomicLong(0);
    final AtomicLong existingCounter= counters.putIfAbsent(id, newCounter);
    if (existingCounter == null) {
      return newCounter;
    }

    return existingCounter;
  }
}
