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

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.LinkedList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.NonNull;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "The canary analysis execution results.")
public class CanaryAnalysisExecutionResult {

  @Schema(description = "This boolean represents whether the canary passed the defined thresholds.")
  protected boolean didPassThresholds;

  @Schema(description = "This boolean is set to true if any of the judgements had warnings.")
  protected boolean hasWarnings;

  @Schema(description = "This string describes the aggregated judgement results.")
  protected String canaryScoreMessage;

  @NonNull
  @Builder.Default
  @Schema(
      description =
          "This is an ordered list of the individual judgement scores. "
              + "The last score is used for determining the final result.")
  protected List<Double> canaryScores = new LinkedList<>();

  @NonNull
  @Builder.Default
  @Schema(description = "This is a list of canary execution summaries.")
  protected List<CanaryExecutionResult> canaryExecutionResults = new LinkedList<>();

  @Schema(
      description =
          "buildTimeIso is an ISO 8061 string and refers to the time the pipeline was first created.")
  protected String buildTimeIso;

  @Schema(
      description =
          "startTimeIso is an ISO 8061 string and refers to the time the pipeline started running.")
  protected String startTimeIso;

  @Schema(
      description =
          "endTimeIso is an ISO 8061 string and refers to the time the pipeline ended, either successfully or unsuccessfully.")
  protected String endTimeIso;
}
