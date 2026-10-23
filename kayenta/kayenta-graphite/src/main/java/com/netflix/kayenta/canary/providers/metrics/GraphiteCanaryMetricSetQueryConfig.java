/*
 * Copyright 2018 Snap Inc.
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

package com.netflix.kayenta.canary.providers.metrics;

import com.fasterxml.jackson.annotation.JsonTypeName;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

@SuperBuilder(toBuilder = true)
@ToString
@NoArgsConstructor
@JsonTypeName("graphite")
public class GraphiteCanaryMetricSetQueryConfig extends AbstractCanaryMetricSetQueryConfig {

  public static final String SERVICE_TYPE = "graphite";

  /**
   * @deprecated use {@link #getCustomInlineTemplate()} or {@link #getCustomFilterTemplate()}
   *     instead; planned for removal in a future release
   */
  @Deprecated @NotNull @Getter private String metricName;

  @Override
  public String getServiceType() {
    return SERVICE_TYPE;
  }
}
