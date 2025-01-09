/*
 * Copyright 2025 Netflix, Inc.
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

package com.netflix.spinnaker.kork.observability.registry;

import static java.util.Optional.ofNullable;

import com.netflix.spinnaker.kork.observability.model.MeterRegistryConfig;
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
 * This is the registry that Micrometer/Spectator will use. It will collect all of the enabled
 * registries, if none are enabled it will default to a Simple Registry w/ default settings.
 */
@Slf4j
public class ArmoryObservabilityCompositeRegistry extends CompositeMeterRegistry {
  public ArmoryObservabilityCompositeRegistry(
      Clock clock,
      Collection<Supplier<RegistryConfigWrapper>> registrySuppliers,
      Collection<RegistryCustomizer> meterRegistryCustomizers) {
    this(
        clock,
        ((Supplier<List<MeterRegistry>>)
                () -> {
                  List<MeterRegistry> enabledRegistries =
                      registrySuppliers.stream()
                          .map(Supplier::get)
                          .filter(Objects::nonNull)
                          .map(RegistryConfigWrapper::getMeterRegistry)
                          .collect(Collectors.toList());

                  // If none of the registries that this plugin provides are enabled, we will
                  // default to the simple registry and assume that Spectator with the spinnaker
                  // monitoring daemon will be used.
                  if (enabledRegistries.size() == 0) {
                    log.warn(
                        "None of the supported Armory Observability Plugin registries where enabled defaulting a Simple Meter Registry which Spectator will use.");
                    enabledRegistries =
                        List.of(new SimpleMeterRegistry(SimpleConfig.DEFAULT, clock));
                  }
                  return enabledRegistries;
                })
            .get());

    // Create map of registries to config
    var registryToConfigMap =
        registrySuppliers.stream()
            .map(Supplier::get)
            .filter(Objects::nonNull)
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

  public ArmoryObservabilityCompositeRegistry(Clock clock, Iterable<MeterRegistry> registries) {
    super(clock, registries);
  }
}
