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

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.netflix.kayenta.canary.results.CanaryResult;
import com.netflix.spinnaker.orca.ExecutionStatus;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.NonNull;

import javax.validation.constraints.NotNull;
import java.util.LinkedList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
@ApiModel(description="Summary Result of the Judgement Executions. See GET /canary/${this.executionId} for full result.")
public class CanaryExecutionResult {

  @NotNull
  @ApiModelProperty(value = "This is the execution id of the canary judgement which can be used in the canary controller APIs to fetch the full result.")
  protected String executionId;

  @NotNull
  @ApiModelProperty(value = "This is the Orca Execution Status for the Canary Judgement Execution.")
  protected ExecutionStatus executionStatus;

  @ApiModelProperty(value = "This shows the first exception in the Judgement Execution if any occurred.")
  protected Object exception;

  @ApiModelProperty(value = "The result of the canary judgement execution.")
  protected CanaryResult result;

  @ApiModelProperty(value = "judgementStartTimeIso is an ISO 8061 string and is the start time used to query the metric source for this judgement.")
  protected String judgementStartTimeIso;

  @ApiModelProperty(value = "judgementStartTimeMillis is in epoch millis time and is the start time used to query the metric source for this judgement.")
  protected Long judgementStartTimeMillis;

  @ApiModelProperty(value = "judgementEndTimeIso is an ISO 8061 string and is the end time used to query the metric source for this judgement.")
  protected String judgementEndTimeIso;

  @ApiModelProperty(value = "judgementEndTimeMillis is in epoch millis time and is the end time used to query the metric source for this judgement.")
  protected Long judgementEndTimeMillis;

  @NonNull
  @Builder.Default
  @ApiModelProperty(value = "This shows any warnings that occurred during the canary judgement.")
  List<String> warnings = new LinkedList<>();

  @ApiModelProperty(value = "This is the metric set pair list id for this canary judgement execution which can be used for obtaining the raw metrics via the API.")
  protected String metricSetPairListId;

  // (startTime - buildTime) should indicate the time it was in the queue before starting.
  // (endTime - buildTime)   should indicate the total time it took from request to result.
  // (endTime - startTime)   should be the amount of time the canary was actually running.
  @ApiModelProperty(value = "buildTimeMillis is in epoch millis time and refers to the time the pipeline was first created.")
  protected Long buildTimeMillis;

  @ApiModelProperty(value = "buildTimeIso is an ISO 8061 string and refers to the time the pipeline was first created.")
  protected String buildTimeIso;

  @ApiModelProperty(value = "startTimeMillis is in epoch millis time and refers to the time the pipeline started running.")
  protected Long startTimeMillis;

  @ApiModelProperty(value = "startTimeIso is an ISO 8061 string and refers to the time the pipeline started running.")
  protected String startTimeIso;

  @ApiModelProperty(value = "endTimeMillis is in epoch millis time and refers to the time the pipeline ended, either successfully or unsuccessfully.")
  protected Long endTimeMillis;

  @ApiModelProperty(value = "endTimeIso is an ISO 8061 string and refers to the time the pipeline ended, either successfully or unsuccessfully.")
  protected String endTimeIso;

  @ApiModelProperty(value = "If set, these are the account names used for this run.")
  protected String storageAccountName;

  @ApiModelProperty(value = "If set, these are the account names used for this run.")
  protected String configurationAccountName;
}
