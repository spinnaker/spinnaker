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

import com.netflix.spinnaker.kork.actuator.observability.model.MetricsPrometheusConfig;
import io.micrometer.prometheus.PrometheusConfig;
import java.time.Duration;

/**
 * Prometheus config wrapper that sources its config from the Spring Context Plugin Configuration.
 */
public class PrometheusRegistryConfig implements PrometheusConfig {

  private final MetricsPrometheusConfig prometheusConfig;

  public PrometheusRegistryConfig(MetricsPrometheusConfig prometheusConfig) {
    this.prometheusConfig = prometheusConfig;
  }

  @Override
  public String get(String key) {
    return null; // NOOP, source config from the PluginConfig that is injected
  }

  @Override
  public boolean descriptions() {
    return prometheusConfig.isDescriptions();
  }

  @Override
  public Duration step() {
    return Duration.ofSeconds(prometheusConfig.getStepInSeconds());
  }
}
