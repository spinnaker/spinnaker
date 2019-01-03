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

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.NonNull;

import java.util.LinkedList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ApiModel(description="The canary analysis execution results")
public class CanaryAnalysisExecutionResult {

  @ApiModelProperty(value = "This is a boolean that represents if the canary was determined to pass the defined thresholds or not")
  protected boolean didPassThresholds;

  @ApiModelProperty(value = "This will be set to true, if any of the judgements had warnings")
  protected boolean hasWarnings;

  @ApiModelProperty(value = "This will be a string that describes the aggregated judgement results")
  protected String canaryScoreMessage;

  @NonNull
  @Builder.Default
  @ApiModelProperty(value = "This will be an ordered list of the individual judgement scores. " +
      "The last score is used for determining the final result")
  protected List<Double> canaryScores = new LinkedList<>();

  @NonNull
  @Builder.Default
  @ApiModelProperty(value = "List of canary execution summaries.")
  protected List<CanaryExecutionResult> canaryExecutionResults = new LinkedList<>();

  @ApiModelProperty(value = "buildTime is when the pipeline was first created.")
  protected String buildTimeIso;

  @ApiModelProperty(value = "startTime refers to the time the pipeline started running.")
  protected String startTimeIso;

  @ApiModelProperty(value = "endTime refers to the time the pipeline ended, either successfully or unsuccessfully.")
  protected String endTimeIso;
}
