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

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.Map;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Data
@Validated
@ConfigurationProperties("canary-analysis")
public class CanaryAnalysisCasesConfigurationProperties {

  @Valid private Map<String, AnalysisConfiguration> cases;

  public AnalysisConfiguration get(String caseName) {
    AnalysisConfiguration config = cases.get(caseName);
    if (config == null) {
      throw new IllegalStateException("Case " + caseName + " not configured");
    }
    return config;
  }

  @Data
  public static class AnalysisConfiguration {

    @NotNull private Long lifetimeDurationMinutes;
    @NotNull private Long analysisIntervalMinutes;
    @NotNull private String namespace;
    @NotNull private ScopeMetricsConfiguration control;
    @NotNull private ScopeMetricsConfiguration experiment;
  }

  @Data
  public static class ScopeMetricsConfiguration {

    @NotNull private String scope;
    @Valid @NotNull private List<MetricConfiguration> metrics;
  }

  @Data
  public static class MetricConfiguration {

    @NotEmpty private String name;
    @NotNull private Integer lowerBound;
    @NotNull private Integer upperBound;
    @NotNull private String type;
  }
}
