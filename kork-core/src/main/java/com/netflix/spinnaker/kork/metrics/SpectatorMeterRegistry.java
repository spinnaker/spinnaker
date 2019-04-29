/*
 * Copyright 2019 Netflix, Inc.
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

import com.netflix.spectator.api.Registry;
import io.micrometer.core.instrument.*;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.Iterator;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

public class SpectatorMeterRegistry extends SimpleMeterRegistry {

  private Registry spectatorRegistry;

  public SpectatorMeterRegistry(Registry spectatorRegistry) {
    this.spectatorRegistry = spectatorRegistry;
  }

  @Override
  public List<Meter> getMeters() {
    return Stream.concat(
            spectatorRegistry.stream().map(this::convertMeter), super.getMeters().stream())
        .collect(Collectors.toList());
  }

  @Override
  public void forEachMeter(Consumer<? super Meter> consumer) {
    Stream.concat(spectatorRegistry.stream().map(this::convertMeter), super.getMeters().stream())
        .forEach(consumer);
  }

  public Meter convertMeter(com.netflix.spectator.api.Meter meter) {
    final Meter.Id id = getId(meter);
    return new Meter() {

      @Override
      public Iterable<Measurement> measure() {
        Iterator<Measurement> measurements =
            StreamSupport.stream(meter.measure().spliterator(), false)
                .map(m -> new Measurement(m::value, Statistic.UNKNOWN))
                .iterator();
        return () -> measurements;
      }

      @Override
      public Id getId() {
        return id;
      }
    };
  }

  private Meter.Id getId(com.netflix.spectator.api.Meter meter) {
    Iterator<Tag> tags =
        StreamSupport.stream(meter.id().tags().spliterator(), false)
            .map(t -> Tag.of(t.key(), t.value()))
            .iterator();
    return new Meter.Id(meter.id().name(), Tags.of(() -> tags), null, "", getMeterType(meter));
  }

  private Meter.Type getMeterType(com.netflix.spectator.api.Meter meter) {
    if (meter instanceof Counter) {
      return Meter.Type.COUNTER;
    } else if (meter instanceof Gauge) {
      return Meter.Type.GAUGE;
    } else if (meter instanceof LongTaskTimer) {
      return Meter.Type.LONG_TASK_TIMER;
    } else if (meter instanceof Timer) {
      return Meter.Type.TIMER;
    } else if (meter instanceof DistributionSummary) {
      return Meter.Type.DISTRIBUTION_SUMMARY;
    } else {
      return Meter.Type.OTHER;
    }
  }
}
