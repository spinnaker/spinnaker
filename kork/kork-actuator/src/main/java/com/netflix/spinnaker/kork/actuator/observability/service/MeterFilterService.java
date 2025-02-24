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

package com.netflix.spinnaker.kork.actuator.observability.service;

import static com.netflix.spinnaker.kork.actuator.observability.filters.ArmoryRecommendedFilters.ARMORY_RECOMMENDED_FILTERS;

import com.netflix.spinnaker.kork.actuator.observability.model.MeterRegistryConfig;
import io.micrometer.core.instrument.config.MeterFilter;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.CollectionUtils;

/**
 * https://micrometer.io/docs/concepts#_denyaccept_meters
 *
 * <p>A service that will provide our filters and transformers to configure / customize metrics to
 * be more efficient for metrics platforms that care about the number of unique MTS and or DPM.
 */
@Slf4j
public class MeterFilterService {

  /**
   * TODO, pattern for impl TBD.
   *
   * @return The list of enabled filters.
   * @param meterRegistryConfig
   */
  public List<MeterFilter> getMeterFilters(MeterRegistryConfig meterRegistryConfig) {
    List<MeterFilter> meterFilters = new ArrayList<>();

    if (meterRegistryConfig.isArmoryRecommendedFiltersEnabled()) {
      log.info("Armory Recommended filters are enabled returning those");
      meterFilters.addAll(ARMORY_RECOMMENDED_FILTERS);
    }

    if (!CollectionUtils.isEmpty(meterRegistryConfig.getExcludedMetricsPrefix())) {
      // Explicitly ensure that the lambda parameter is treated as a String
      meterRegistryConfig
          .getExcludedMetricsPrefix()
          .forEach(
              (String metricName) -> meterFilters.add(MeterFilter.denyNameStartsWith(metricName)));
    }

    return meterFilters;
  }
}
