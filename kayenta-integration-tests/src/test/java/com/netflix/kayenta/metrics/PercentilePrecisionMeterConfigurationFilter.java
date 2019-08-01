/*
 * Copyright 2019 Playtika
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
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
package com.netflix.kayenta.metrics;

import io.micrometer.core.instrument.Meter.Id;
import io.micrometer.core.instrument.config.MeterFilter;
import io.micrometer.core.instrument.distribution.DistributionStatisticConfig;
import java.time.Duration;
import lombok.Data;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class PercentilePrecisionMeterConfigurationFilter implements MeterFilter {

  private final MetricsConfigurationProperties metricsConfigurationProperties =
      new MetricsConfigurationProperties();

  public DistributionStatisticConfig configure(Id id, DistributionStatisticConfig config) {
    DistributionStatisticConfig statisticConfig =
        DistributionStatisticConfig.builder()
            .percentilePrecision(this.metricsConfigurationProperties.getPercentilePrecision())
            .expiry(this.metricsConfigurationProperties.getExpiry())
            .build();
    return statisticConfig.merge(config);
  }

  @Data
  public class MetricsConfigurationProperties {
    private int percentilePrecision = 3;
    private Duration expiry = Duration.ofMinutes(5L);
  }
}
