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
import lombok.Builder;
import lombok.Getter;
import lombok.Singular;
import lombok.ToString;
import org.springframework.util.StringUtils;

import javax.validation.constraints.NotNull;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

@Builder
@ToString
public class MetricSet {

  @NotNull
  @Getter
  private String name;

  @NotNull
  @Singular
  @Getter
  private Map<String, String> tags;

  @NotNull
  @Getter
  private long startTimeMillis;

  @NotNull
  @Getter
  private String startTimeIso;

  @NotNull
  @Getter
  private long stepMillis;

  @NotNull
  @Singular
  @Getter
  private List<Double> values;

  @JsonIgnore
  private String metricSetKey;

  public String getMetricSetKey() {
    // Only need to generate the key once since MetricSet is immutable.
    if (metricSetKey == null) {
      if (StringUtils.isEmpty(name)) {
        throw new IllegalArgumentException("Metric set name was not set.");
      }

      TreeMap<String, String> tagMap = new TreeMap<>(tags);

      metricSetKey = name + " -> {" +
        tagMap
          .entrySet()
          .stream()
          .map(entry -> entry.getKey() + ":" + entry.getValue())
          .collect(Collectors.joining(", ")) + "}";
    }

    return metricSetKey;
  }
}
