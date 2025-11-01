/*
 * Copyright (c) 2018 Nike, inc.
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

package com.netflix.kayenta.standalonecanaryanalysis.domain;

import com.netflix.kayenta.canary.CanaryConfig;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
@Schema(
    description =
        "Wrapper object around canary config and execution request for the ad-hoc endpoint.")
public class CanaryAnalysisAdhocExecutionRequest {

  @NotNull
  @Schema(description = "The canary configuration to use for the canary analysis execution.")
  protected CanaryConfig canaryConfig;

  @NotNull
  @Schema(description = "The canary analysis configuration request object.")
  protected CanaryAnalysisExecutionRequest executionRequest;
}
