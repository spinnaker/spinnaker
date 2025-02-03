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

package com.netflix.spinnaker.kork.actuator.observability.service;

import static org.junit.Assert.*;

import com.netflix.spinnaker.kork.actuator.observability.model.MeterRegistryConfig;
import java.util.List;
import org.junit.Before;
import org.junit.Test;

public class MeterFilterServiceTest {

  MeterFilterService sut;

  @Before
  public void before() {
    sut = new MeterFilterService();
  }

  @Test
  public void test_that_getMeterFilters_returns_emtpy_list_by_default() {
    var filters = sut.getMeterFilters(MeterRegistryConfig.builder().build());
    assertNotNull(filters);
    assertEquals(0, filters.size());
  }

  @Test
  public void
      test_that_getMeterFilters_returns_a_non_empty_list_of_filters_when_armory_recommendations_are_enabled() {
    var filters =
        sut.getMeterFilters(
            MeterRegistryConfig.builder().armoryRecommendedFiltersEnabled(true).build());
    assertNotNull(filters);
    assertTrue(filters.size() > 0);
  }

  @Test
  public void test_that_getMeterFilters_adds_deny_filters_for_excluded_metrics_prefix() {
    List<String> excludedPrefixes = List.of("metric1", "metric2", "metric3");
    var filters =
        sut.getMeterFilters(
            MeterRegistryConfig.builder().excludedMetricsPrefix(excludedPrefixes).build());
    assertNotNull(filters);
    System.out.println(filters.size());
    assertTrue(filters.size() > 2);
  }

  @Test
  public void test_that_getMeterFilters_combines_armory_recommendations_with_excluded_metrics() {
    List<String> excludedPrefixes = List.of("metric1", "metric2", "metric3");
    var filters =
        sut.getMeterFilters(
            MeterRegistryConfig.builder()
                .armoryRecommendedFiltersEnabled(true)
                .excludedMetricsPrefix(excludedPrefixes)
                .build());
    assertNotNull(filters);
    System.out.println(filters.size());
    assertTrue(filters.size() > 3);
  }
}
