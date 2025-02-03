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

import static org.mockito.Mockito.*;
import static org.mockito.MockitoAnnotations.initMocks;

import com.netflix.spinnaker.kork.actuator.observability.model.MeterRegistryConfig;
import com.netflix.spinnaker.kork.actuator.observability.service.MeterFilterService;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.config.MeterFilter;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;

public class AddFiltersRegistryCustomizerTest {

  @Mock MeterFilterService meterFilterService;

  @Mock MeterRegistry registry;

  @Mock MeterRegistry.Config config;

  AddFiltersRegistryCustomizer sut;

  @Before
  public void before() {
    initMocks(this);
    sut = new AddFiltersRegistryCustomizer(meterFilterService);
    when(registry.config()).thenReturn(config);
  }

  @Test
  public void test_that_customize_adds_the_enabled_filters_to_the_registry() throws Exception {
    var denyAllFilter = MeterFilter.deny(id -> true);
    when(meterFilterService.getMeterFilters(any())).thenReturn(List.of(denyAllFilter));
    sut.customize(registry, MeterRegistryConfig.builder().build());
    verify(config, times(1)).meterFilter(denyAllFilter);
  }
}
