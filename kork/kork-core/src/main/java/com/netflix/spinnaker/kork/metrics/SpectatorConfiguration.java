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
 * Bridges Spectator's {@link Registry} to the application's Micrometer {@link MeterRegistry}.
 *
 * <p>JVM metrics come from Spring Boot's Micrometer binders, not from Spectator.
 *
 * <p>TODO: remove with the rest of Spectator (deprecated, removal planned for 2027.0.0) once all
 * call sites use Micrometer directly.
 */
@Configuration
@ConditionalOnClass(Registry.class)
public class SpectatorConfiguration {

  @Bean
  @ConditionalOnMissingBean(Registry.class)
  Registry registry(MeterRegistry meterRegistry) {
    return new MicrometerRegistry(meterRegistry);
  }

  @Bean
  GlobalRegistryRegistration globalRegistryRegistration(Registry registry) {
    return new GlobalRegistryRegistration(registry);
  }

  /** Makes the bridge available to code that records through {@link Spectator#globalRegistry()}. */
  private static class GlobalRegistryRegistration {
    private final Registry registry;

    GlobalRegistryRegistration(Registry registry) {
      this.registry = registry;
      Spectator.globalRegistry().add(registry);
    }

    @PreDestroy
    public void destroy() {
      Spectator.globalRegistry().remove(registry);
    }
  }
}
