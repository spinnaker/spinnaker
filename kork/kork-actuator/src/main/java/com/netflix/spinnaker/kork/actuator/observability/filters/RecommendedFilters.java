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

package com.netflix.spinnaker.kork.actuator.observability.filters;

import static com.netflix.spinnaker.kork.actuator.observability.filters.Filters.DENY_CONTROLLER_INVOCATIONS_METRICS;

import io.micrometer.core.instrument.config.MeterFilter;
import java.util.List;

/**
 * Curated list of filters/transformations to reduce DPM (data points per minute) and metrics time
 * series cardinality.
 *
 * <p>These filters are recommended for production deployments to reduce observability platform
 * costs. The list may be updated between versions without major version bumps.
 */
public class RecommendedFilters {

  /**
   * Recommended filters that reduce metric cardinality by denying legacy controller invocation
   * metrics in favor of standard Spring {@code http.server.requests} metrics.
   */
  public static List<MeterFilter> RECOMMENDED_FILTERS =
      List.of(DENY_CONTROLLER_INVOCATIONS_METRICS);
}
