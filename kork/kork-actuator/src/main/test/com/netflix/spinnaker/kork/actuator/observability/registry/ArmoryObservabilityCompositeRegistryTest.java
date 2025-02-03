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

package com.netflix.spinnaker.kork.actuator.observability.registry;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.MockitoAnnotations.initMocks;

import io.micrometer.core.instrument.Clock;
import io.micrometer.core.instrument.logging.LoggingMeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.List;
import org.junit.Before;
import org.junit.Test;

public class ArmoryObservabilityCompositeRegistryTest {

  @Before
  public void before() {
    initMocks(this);
  }

  @Test
  public void test_that_ArmoryObservabilityCompositeRegistry_uses_supplied_registries() {
    var registry = new LoggingMeterRegistry();
    var sut =
        new ArmoryObservabilityCompositeRegistry(
            Clock.SYSTEM,
            List.of(() -> RegistryConfigWrapper.builder().meterRegistry(registry).build()),
            List.of());

    assertEquals(1, sut.getRegistries().size());
    assertEquals(registry, sut.getRegistries().toArray()[0]);
  }

  @Test
  public void
      test_that_ArmoryObservabilityCompositeRegistry_uses_a_simple_registry_when_no_registries_are_enabled() {
    var sut =
        new ArmoryObservabilityCompositeRegistry(Clock.SYSTEM, List.of(() -> null), List.of());
    assertEquals(1, sut.getRegistries().size());
    assertEquals(SimpleMeterRegistry.class, sut.getRegistries().toArray()[0].getClass());
  }

  @Test
  public void
      test_that_ArmoryObservabilityCompositeRegistry_calls_the_registry_customizers_for_each_enabled_registry() {
    var customizer = mock(RegistryCustomizer.class);
    var registry = new LoggingMeterRegistry();
    var registry2 = new SimpleMeterRegistry();
    new ArmoryObservabilityCompositeRegistry(
        Clock.SYSTEM,
        List.of(
            () -> RegistryConfigWrapper.builder().meterRegistry(registry).build(),
            () -> RegistryConfigWrapper.builder().meterRegistry(registry2).build()),
        List.of(customizer));

    verify(customizer, times(2)).customize(any(), any());
  }
}
