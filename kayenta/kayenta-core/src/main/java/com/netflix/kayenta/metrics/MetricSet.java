/*
 * Copyright 2017 Google, Inc.
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

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;
import lombok.*;
import org.springframework.util.StringUtils;

@Builder
@ToString
@NoArgsConstructor
public class MetricSet {

  @NotNull @Getter @Setter private String name;

  @NotNull @Singular @Getter private Map<String, String> tags;

  @NotNull @Getter private long startTimeMillis;

  @NotNull @Getter private String startTimeIso;

  @NotNull @Getter private long endTimeMillis;

  @NotNull @Getter private String endTimeIso;

  @NotNull @Getter private long stepMillis;

  @NotNull @Singular @Getter private List<Double> values;

  @Singular @Getter private Map<String, String> attributes;

  @JsonIgnore private String metricSetKey;

  public MetricSet(
      String name,
      Map<String, String> tags,
      long startTimeMillis,
      String startTimeIso,
      long endTimeMillis,
      String endTimeIso,
      long stepMillis,
      List<Double> values,
      Map<String, String> attributes,
      String metricSetKey) {
    this.name = name;
    this.tags = tags;
    this.startTimeMillis = startTimeMillis;
    this.startTimeIso = startTimeIso;
    this.endTimeMillis = endTimeMillis;
    this.endTimeIso = endTimeIso;
    this.stepMillis = stepMillis;
    this.values = values;
    this.attributes = attributes;
    this.metricSetKey = metricSetKey;
  }

  public String getMetricSetKey() {
    // Only need to generate the key once since MetricSet is immutable.
    if (metricSetKey == null) {
      if (StringUtils.isEmpty(name)) {
        throw new IllegalArgumentException("Metric set name was not set.");
      }

      TreeMap<String, String> tagMap = new TreeMap<>(tags);

      metricSetKey =
          name
              + " -> {"
              + tagMap.entrySet().stream()
                  .map(entry -> entry.getKey() + ":" + entry.getValue())
                  .collect(Collectors.joining(", "))
              + "}";
    }

    return metricSetKey;
  }

  public long expectedDataPoints() {
    if (stepMillis == 0) {
      return 0;
    }
    return (endTimeMillis - startTimeMillis) / stepMillis;
  }
}
