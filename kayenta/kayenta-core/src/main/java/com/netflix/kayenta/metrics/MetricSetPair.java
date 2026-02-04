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

import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.Map;
import lombok.*;

@Builder
@ToString
@NoArgsConstructor
public class MetricSetPair {

  @NotNull @Getter private String name;

  @NotNull @Getter private String id;

  @NotNull @Singular @Getter private Map<String, String> tags;

  @NotNull @Singular @Getter private Map<String, List<Double>> values;

  @NotNull @Singular @Getter private Map<String, MetricSetScope> scopes;

  @Getter @Singular private Map<String, Map<String, String>> attributes;

  public MetricSetPair(
      String name,
      String id,
      Map<String, String> tags,
      Map<String, List<Double>> values,
      Map<String, MetricSetScope> scopes,
      Map<String, Map<String, String>> attributes) {
    this.name = name;
    this.id = id;
    this.tags = tags;
    this.values = values;
    this.scopes = scopes;
    this.attributes = attributes;
  }

  @Builder
  @ToString
  @AllArgsConstructor
  @NoArgsConstructor
  public static class MetricSetScope {

    @NotNull @Getter private String startTimeIso;

    @NotNull @Getter private long startTimeMillis;

    @NotNull @Getter private long stepMillis;
  }
}
