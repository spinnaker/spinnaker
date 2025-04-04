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

import com.fasterxml.jackson.annotation.JsonProperty;
import com.netflix.kayenta.canary.CanaryConfig;
import com.netflix.spinnaker.orca.api.pipeline.models.ExecutionStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "The canary analysis execution status response.")
public class CanaryAnalysisExecutionStatusResponse {

  @NotNull
  @Schema(description = "The application under test.")
  protected String application;

  @NotNull
  @Schema(description = "This is the initiating user. If none was supplied this will be anonymous.")
  protected String user;

  @Schema(description = "This is the parent pipeline execution id if one was provided.")
  protected String parentPipelineExecutionId;

  @NotNull
  @Schema(description = "This is the pipeline id of this execution.")
  protected String pipelineId;

  @NotNull
  @Schema(
      description =
          "This is a map of StageExecution statuses which is useful for gaining insight into progress of the execution.")
  protected List<StageMetadata> stageStatus;

  @NotNull
  @Schema(
      description =
          "This indicates that the task/stage/pipeline has finished its work, independent of whether it was successful.")
  protected Boolean complete;

  @NotNull
  @Schema(
      description = "This is the Orca Execution Status for the Canary Analysis Pipeline Execution.")
  protected ExecutionStatus executionStatus;

  @JsonProperty("status")
  @Schema(
      description =
          "This is the lowercased serialized Orca status which is similar to the status in the /canary endpoints.")
  public String status() {
    return executionStatus.toString().toLowerCase();
  }

  @Schema(description = "This shows the first exception if any occurred.")
  protected Object exception;

  @Schema(
      description =
          "This is the actual result of the canary analysis execution which will be present when complete is true.")
  protected CanaryAnalysisExecutionResult canaryAnalysisExecutionResult;

  @Schema(description = "The supplied request configuration.")
  protected CanaryAnalysisExecutionRequest canaryAnalysisExecutionRequest;

  @Schema(description = "The supplied or retrieved canary configuration used.")
  protected CanaryConfig canaryConfig;

  @Schema(description = "This is the supplied canary config id if one was provided.")
  protected String canaryConfigId;

  // (startTime - buildTime) should indicate the time it was in the queue before starting.
  // (endTime - buildTime) should indicate the total time it took from request to result.
  // (endTime - startTime) should be the amount of time the canary was actually running.
  @Schema(
      description =
          "buildTimeMillis is in epoch millis time and refers to the time the pipeline was first created.")
  protected Long buildTimeMillis;

  @Schema(
      description =
          "buildTimeIso is an ISO 8061 string and refers to the time the pipeline was first created.")
  protected String buildTimeIso;

  @Schema(
      description =
          "startTimeIso is an ISO 8061 string and refers to the time the pipeline started running.")
  protected Long startTimeMillis;

  @Schema(
      description =
          "startTimeIso is an ISO 8061 string and refers to the time the pipeline started running.")
  protected String startTimeIso;

  @Schema(
      description =
          "endTimeMillis is in epoch millis time and refers to the time the pipeline ended, either successfully or unsuccessfully.")
  protected Long endTimeMillis;

  @Schema(
      description =
          "endTimeIso is an ISO 8061 string and refers to the time the pipeline ended, either successfully or unsuccessfully.")
  protected String endTimeIso;

  @NotNull
  @Schema(description = "The resolved storage account name.")
  protected String storageAccountName;

  @NotNull
  @Schema(description = "The resolved metrics account name.")
  protected String metricsAccountName;
}
