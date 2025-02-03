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

package com.netflix.spinnaker.kork.actuator.observability.prometheus;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import com.netflix.spinnaker.kork.actuator.observability.model.MetricsPrometheusConfig;
import java.time.Duration;
import org.junit.Before;
import org.junit.Test;

public class PrometheusRegistryConfigTest {

  MetricsPrometheusConfig prometheusConfig;

  PrometheusRegistryConfig sut;

  @Before
  public void before() {
    prometheusConfig = new MetricsPrometheusConfig();
    sut = new PrometheusRegistryConfig(prometheusConfig);
  }

  @Test
  public void test_that_PrometheusRegistryConfig_proxies_PluginMetricsPrometheusConfig() {
    var step = Duration.ofSeconds(30);
    prometheusConfig.setDescriptions(true);
    prometheusConfig.setStepInSeconds((int) step.toSeconds());
    assertEquals(step, sut.step());
    assertEquals(prometheusConfig.isDescriptions(), sut.descriptions());
    assertNull(sut.get("foo"));
  }
}
