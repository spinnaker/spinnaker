/*
 * Copyright 2019 Nike, Inc.
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

package com.netflix.kayenta.newrelic.controller;

import com.netflix.kayenta.canary.CanaryConfig;
import com.netflix.kayenta.canary.CanaryMetricConfig;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import java.util.HashMap;
import java.util.Map;
import javax.annotation.Nullable;
import lombok.*;

@Data
@Builder
@ToString
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "Request body for using the New Relic Fetch controller")
class NewRelicFetchRequest {
  @NotNull
  @Schema(description = "The metric config to query New Relic insights for")
  CanaryMetricConfig canaryMetricConfig;

  @NotNull @Builder.Default Map<String, String> extendedScopeParams = new HashMap<>();

  @Nullable CanaryConfig canaryConfig;
}
