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

package com.netflix.spinnaker.kork.observability.model;

import java.util.Map;
import lombok.Data;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

@Data
public class MetricsConfig {
  private Map<String, String> additionalTags = Map.of();
  private MetricsPrometheusConfig prometheus = new MetricsPrometheusConfig();
  private MetricsNewRelicConfig newrelic = new MetricsNewRelicConfig();
  private MetricsDatadogConfig datadog = new MetricsDatadogConfig();
  private boolean armoryRecommendedFiltersEnabled = false;

  @Bean
  @ConditionalOnProperty(value = "observability.config.metrics.prometheus", matchIfMissing = true)
  public MetricsPrometheusConfig prometheus() {
    return this.prometheus;
  }

  @Bean
  @ConditionalOnProperty(value = "observability.config.metrics.newrelic", matchIfMissing = true)
  public MetricsNewRelicConfig newrelic() {
    return this.newrelic;
  }

  @ConditionalOnProperty(value = "observability.config.metrics.datadog", matchIfMissing = true)
  @Bean
  MetricsDatadogConfig datadog() {
    return this.datadog;
  }
}
