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

package com.netflix.kayenta.canary.providers;

import com.netflix.kayenta.canary.CanaryMetricSetQueryConfig;
import lombok.*;

import javax.validation.constraints.NotNull;
import java.util.List;

@Builder
@ToString
@NoArgsConstructor
@AllArgsConstructor
// TODO(duftler): Figure out how to move this into the kayenta-stackdriver module? Doing so as-is would introduce a circular dependency.
public class StackdriverCanaryMetricSetQueryConfig implements CanaryMetricSetQueryConfig {

  @NotNull
  @Getter
  private String metricType;

  @NotNull
  @Getter
  private List<String> groupByFields;

  // Optionally defines an explicit stackdriver filter to be used when composing the query. Takes precedence over
  // customFilterTemplate.
  @Getter
  private String customFilter;

  // Optionally refers by name to a FreeMarker template defined in the canary config top-level 'templates' map. It is
  // expanded by using the key/value pairs in extendedScopeParams as the variable bindings. Once expanded, the
  // resulting stackdriver filter is used when composing the query.
  @Getter
  private String customFilterTemplate;
}
