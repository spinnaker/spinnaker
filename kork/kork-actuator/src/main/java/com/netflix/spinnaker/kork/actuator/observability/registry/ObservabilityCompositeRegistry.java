/*
 * Copyright 2025 Harness, Inc.
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

package com.netflix.spinnaker.kork.actuator.observability.registry;

import static java.util.Optional.ofNullable;

import com.netflix.spinnaker.kork.actuator.observability.model.MeterRegistryConfig;
import io.micrometer.core.instrument.Clock;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.composite.CompositeMeterRegistry;
import io.micrometer.core.instrument.simple.SimpleConfig;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;

/**
 * Composite meter registry that collects all enabled metric registries (Prometheus, Datadog, New
 * Relic). If no registries are enabled, it falls back to a SimpleMeterRegistry for Spectator
 * compatibility.
 */
@Slf4j
public class ObservabilityCompositeRegistry extends CompositeMeterRegistry {

  public ObservabilityCompositeRegistry(
      Clock clock,
      Collection<Supplier<RegistryConfigWrapper>> registrySuppliers,
      Collection<RegistryCustomizer> meterRegistryCustomizers) {
    // Collect wrappers once to avoid invoking suppliers multiple times.
    // Each supplier may create new registries and start background threads on each call.
    this(clock, registrySuppliers, meterRegistryCustomizers, collectWrappers(registrySuppliers));
  }

  private ObservabilityCompositeRegistry(
      Clock clock,
      Collection<Supplier<RegistryConfigWrapper>> registrySuppliers,
      Collection<RegistryCustomizer> meterRegistryCustomizers,
      List<RegistryConfigWrapper> wrappers) {
    super(clock, extractRegistries(wrappers, clock));

    // Build config map from cached wrappers (not re-invoking suppliers)
    var registryToConfigMap =
        wrappers.stream()
            .collect(
                Collectors.toMap(
                    w -> w.getMeterRegistry().getClass().getSimpleName(),
                    RegistryConfigWrapper::getMeterRegistryConfig));

    this.getRegistries()
        .forEach(
            meterRegistry ->
                meterRegistryCustomizers.forEach(
                    registryCustomizer -> {
                      var config =
                          ofNullable(
                                  registryToConfigMap.get(meterRegistry.getClass().getSimpleName()))
                              .orElse(new MeterRegistryConfig());
                      registryCustomizer.customize(meterRegistry, config);
                    }));
  }

  private static List<RegistryConfigWrapper> collectWrappers(
      Collection<Supplier<RegistryConfigWrapper>> registrySuppliers) {
    return registrySuppliers.stream()
        .map(Supplier::get)
        .filter(Objects::nonNull)
        .collect(Collectors.toList());
  }

  private static List<MeterRegistry> extractRegistries(
      List<RegistryConfigWrapper> wrappers, Clock clock) {
    if (wrappers.isEmpty()) {
      log.warn(
          "No observability registries enabled, defaulting to SimpleMeterRegistry "
              + "for Spectator compatibility.");
      return List.of(new SimpleMeterRegistry(SimpleConfig.DEFAULT, clock));
    }
    return wrappers.stream()
        .map(RegistryConfigWrapper::getMeterRegistry)
        .collect(Collectors.toList());
  }

  public ObservabilityCompositeRegistry(Clock clock, Iterable<MeterRegistry> registries) {
    super(clock, registries);
  }
}
