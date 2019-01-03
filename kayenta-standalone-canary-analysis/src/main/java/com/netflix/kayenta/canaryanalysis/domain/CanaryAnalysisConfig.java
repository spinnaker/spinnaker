/*
 * Copyright (c) 2018 Nike, inc.
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

package com.netflix.kayenta.canaryanalysis.domain;

import com.netflix.kayenta.canary.CanaryConfig;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.validation.constraints.NotNull;

/**
 * Internal wrapper object for passing all the data received from the canary analysis endpoints as a single object for
 * cleaner method signatures and Orca stage contexts.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CanaryAnalysisConfig {

  @NotNull
  @Builder.Default
  private String user = "anonymous";

  @NotNull
  private String application;

  private String parentPipelineExecutionId;

  private String canaryConfigId;

  @NotNull
  private String metricsAccountName;

  @NotNull
  private String storageAccountName;

  private String configurationAccountName;

  @NotNull
  private CanaryAnalysisExecutionRequest executionRequest;

  @NotNull
  private CanaryConfig canaryConfig;
}
