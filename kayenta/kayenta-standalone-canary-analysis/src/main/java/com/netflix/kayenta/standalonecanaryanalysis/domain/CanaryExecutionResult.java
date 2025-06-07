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

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.netflix.kayenta.canary.results.CanaryResult;
import com.netflix.spinnaker.orca.api.pipeline.models.ExecutionStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
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
@JsonIgnoreProperties(ignoreUnknown = true)
@Schema(
    description =
        "Summary Result of the Judgement Executions. See GET /canary/${this.executionId} for full result.")
public class CanaryExecutionResult {

  @NotNull
  @Schema(
      description =
          "This is the execution id of the canary judgement which can be used in the canary controller APIs to fetch the full result.")
  protected String executionId;

  @NotNull
  @Schema(description = "This is the Orca Execution Status for the Canary Judgement Execution.")
  protected ExecutionStatus executionStatus;

  @Schema(
      description = "This shows the first exception in the Judgement Execution if any occurred.")
  protected Object exception;

  @Schema(description = "The result of the canary judgement execution.")
  protected CanaryResult result;

  @Schema(
      description =
          "judgementStartTimeIso is an ISO 8061 string and is the start time used to query the metric source for this judgement.")
  protected String judgementStartTimeIso;

  @Schema(
      description =
          "judgementStartTimeMillis is in epoch millis time and is the start time used to query the metric source for this judgement.")
  protected Long judgementStartTimeMillis;

  @Schema(
      description =
          "judgementEndTimeIso is an ISO 8061 string and is the end time used to query the metric source for this judgement.")
  protected String judgementEndTimeIso;

  @Schema(
      description =
          "judgementEndTimeMillis is in epoch millis time and is the end time used to query the metric source for this judgement.")
  protected Long judgementEndTimeMillis;

  @NonNull
  @Builder.Default
  @Schema(description = "This shows any warnings that occurred during the canary judgement.")
  List<String> warnings = new LinkedList<>();

  @Schema(
      description =
          "This is the metric set pair list id for this canary judgement execution which can be used for obtaining the raw metrics via the API.")
  protected String metricSetPairListId;

  // (startTime - buildTime) should indicate the time it was in the queue before starting.
  // (endTime - buildTime)   should indicate the total time it took from request to result.
  // (endTime - startTime)   should be the amount of time the canary was actually running.
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
          "startTimeMillis is in epoch millis time and refers to the time the pipeline started running.")
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

  @Schema(description = "If set, these are the account names used for this run.")
  protected String storageAccountName;

  @Schema(description = "If set, these are the account names used for this run.")
  protected String configurationAccountName;
}
