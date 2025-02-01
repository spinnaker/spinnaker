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
import com.netflix.spinnaker.kork.actuator.observability.service.MeterFilterService;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;

@Slf4j
@Configuration
public class AddFiltersRegistryCustomizer implements RegistryCustomizer {

  private final MeterFilterService meterFilterService;

  public AddFiltersRegistryCustomizer(MeterFilterService meterFilterService) {
    this.meterFilterService = meterFilterService;
  }

  @Override
  public void customize(MeterRegistry registry, MeterRegistryConfig meterRegistryConfig) {
    log.info("Adding Meter Filters to registry: {}", registry.getClass().getSimpleName());
    meterFilterService
        .getMeterFilters(meterRegistryConfig)
        .forEach(meterFilter -> registry.config().meterFilter(meterFilter));
  }
}
