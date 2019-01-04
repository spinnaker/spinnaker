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

import com.fasterxml.jackson.annotation.JsonProperty;
import com.netflix.kayenta.canary.CanaryConfig;
import com.netflix.spinnaker.orca.ExecutionStatus;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.validation.constraints.NotNull;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ApiModel(description="The canary analysis execution status response.")
public class CanaryAnalysisExecutionStatusResponse {

  @NotNull
  @ApiModelProperty(value = "The application under test.")
  protected String application;

  @NotNull
  @ApiModelProperty(value = "This is the initiating user. If none was supplied this will be anonymous.")
  protected String user;

  @ApiModelProperty(value = "This is the parent pipeline execution id if one was provided.")
  protected String parentPipelineExecutionId;

  @NotNull
  @ApiModelProperty(value = "This is the pipeline id of this execution.")
  protected String pipelineId;

  @NotNull
  @ApiModelProperty(value = "This is a map of stage statuses which is useful for gaining insight into progress of the execution.")
  protected List<StageMetadata> stageStatus;

  @NotNull
  @ApiModelProperty(value = "This indicates that the task/stage/pipeline has finished its work, independent of whether it was successful.")
  protected Boolean complete;

  @NotNull
  @ApiModelProperty(value = "This is the Orca Execution Status for the Canary Analysis Pipeline Execution.")
  protected ExecutionStatus executionStatus;

  @JsonProperty("status")
  @ApiModelProperty(value = "This is the lowercased serialized Orca status which is similar to the status in the /canary endpoints.")
  public String status() {
    return executionStatus.toString().toLowerCase();
  }

  @ApiModelProperty(value = "This shows the first exception if any occurred.")
  protected Object exception;

  @ApiModelProperty(value = "This is the actual result of the canary analysis execution which will be present when complete is true.")
  protected CanaryAnalysisExecutionResult canaryAnalysisExecutionResult;

  @ApiModelProperty(value = "The supplied request configuration.")
  protected CanaryAnalysisExecutionRequest canaryAnalysisExecutionRequest;

  @ApiModelProperty(value = "The supplied or retrieved canary configuration used.")
  protected CanaryConfig canaryConfig;

  @ApiModelProperty(value = "This is the supplied canary config id if one was provided.")
  protected String canaryConfigId;

  // (startTime - buildTime) should indicate the time it was in the queue before starting.
  // (endTime - buildTime) should indicate the total time it took from request to result.
  // (endTime - startTime) should be the amount of time the canary was actually running.
  @ApiModelProperty(value = "buildTimeMillis is in epoch millis time and refers to the time the pipeline was first created.")
  protected Long buildTimeMillis;

  @ApiModelProperty(value = "buildTimeIso is an ISO 8061 string and refers to the time the pipeline was first created.")
  protected String buildTimeIso;

  @ApiModelProperty(value = "startTimeIso is an ISO 8061 string and refers to the time the pipeline started running.")
  protected Long startTimeMillis;

  @ApiModelProperty(value = "startTimeIso is an ISO 8061 string and refers to the time the pipeline started running.")
  protected String startTimeIso;

  @ApiModelProperty(value = "endTimeMillis is in epoch millis time and refers to the time the pipeline ended, either successfully or unsuccessfully.")
  protected Long endTimeMillis;

  @ApiModelProperty(value = "endTimeIso is an ISO 8061 string and refers to the time the pipeline ended, either successfully or unsuccessfully.")
  protected String endTimeIso;
}
