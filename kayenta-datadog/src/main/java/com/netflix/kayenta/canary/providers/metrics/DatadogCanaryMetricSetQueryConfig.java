/*
 * Copyright 2018 Armory, Inc.
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
import com.netflix.kayenta.canary.CanaryMetricSetQueryConfig;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

import javax.validation.constraints.NotNull;

@Builder
@ToString
@NoArgsConstructor
@AllArgsConstructor
@JsonTypeName("datadog")
public class DatadogCanaryMetricSetQueryConfig implements CanaryMetricSetQueryConfig {

  public static final String SERVICE_TYPE = "datadog";

  @NotNull
  @Getter
  private String metricName;

  @Override
  public String getServiceType() {
    return SERVICE_TYPE;
  }
}
