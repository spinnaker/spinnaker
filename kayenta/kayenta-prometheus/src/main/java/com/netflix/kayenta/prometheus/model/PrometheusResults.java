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

package com.netflix.kayenta.prometheus.model;

import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.Map;
import lombok.*;

@Builder
@ToString
@EqualsAndHashCode
@NoArgsConstructor
@AllArgsConstructor
public class PrometheusResults {

  @NotNull @Getter private String id;

  @NotNull @Getter private long startTimeMillis;

  @NotNull @Getter private long stepSecs;

  @NotNull @Getter private long endTimeMillis;

  @NotNull @Getter private Map<String, String> tags;

  @NotNull @Getter private List<Double> values;
}
