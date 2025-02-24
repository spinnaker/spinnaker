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

public class ArmoryRecommendedFilters {

  /*
   * Curated list of filters/transformations that Armory uses internally to reduce DPM / metrics TS cardinality.
   * This list is not guaranteed to have backwards compatibility with Sym versioning of the plugin.
   * What I mean by that is that I will add and remove things to this list that you might rely on without major versioning the plugin.
   *
   * TODO implement pattern that allows for users to pick and choose named filters and not rely on the curated list.
   */
  public static List<MeterFilter> ARMORY_RECOMMENDED_FILTERS =
      List.of(DENY_CONTROLLER_INVOCATIONS_METRICS);
}
