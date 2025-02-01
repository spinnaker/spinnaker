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

import com.netflix.spinnaker.kork.actuator.observability.model.MeterRegistryConfig;
import io.micrometer.core.instrument.MeterRegistry;

@FunctionalInterface
public interface RegistryCustomizer {

  /**
   * Customize the given {@code registry}.
   *
   * @param registry the registry to customize
   * @param meterRegistryConfig the filter/tag config for the registry/integration being customized.
   */
  void customize(MeterRegistry registry, MeterRegistryConfig meterRegistryConfig);
}
