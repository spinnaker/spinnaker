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

import com.netflix.spectator.api.Registry;
import com.netflix.spectator.api.Spectator;
import com.netflix.spectator.micrometer.MicrometerRegistry;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PreDestroy;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Bridges the Spring-managed Micrometer {@link MeterRegistry} to a Spectator {@link Registry}, for
 * the benefit of services that have not yet migrated their own metric recording off Spectator. This
 * is a deliberate, temporary compatibility shim for Spinnaker's staged Spectator removal (kork,
 * then rosco/echo/igor, then the rest); it should be deleted once no service in the monorepo
 * depends on {@code com.netflix.spectator.api.Registry} anymore. New code in kork itself records
 * metrics directly through {@link MeterRegistry} and does not need this bridge.
 */
@Configuration
@ConditionalOnClass(Registry.class)
public class SpectatorBridgeConfiguration {

  @Bean
  @ConditionalOnMissingBean(Registry.class)
  Registry registry(MeterRegistry meterRegistry) {
    return new MicrometerRegistry(meterRegistry);
  }

  @Bean
  RegistryInitializer registryInitializer(Registry registry) {
    return new RegistryInitializer(registry);
  }

  private static class RegistryInitializer {
    private final Registry registry;

    RegistryInitializer(Registry registry) {
      this.registry = registry;
      Spectator.globalRegistry().add(registry);
    }

    @PreDestroy
    public void destroy() {
      Spectator.globalRegistry().remove(registry);
    }
  }
}
