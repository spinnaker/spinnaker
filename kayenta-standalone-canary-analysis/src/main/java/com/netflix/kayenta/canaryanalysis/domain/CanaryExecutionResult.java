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
@ApiModel(description="Summary Result of the Judgement executions. See GET /canary/${this.executionId} for full result")
public class CanaryExecutionResult {

  @NotNull
  @ApiModelProperty(value = "The execution id of the canary judgement, can be used in the canary controller apis to fetch the full result")
  protected String executionId;

  @NotNull
  @ApiModelProperty(value = "The Orca Execution Status for the Canary Judgement Execution")
  protected ExecutionStatus executionStatus;

  @ApiModelProperty(value = "The first exception that occurred in the Judgement Execution will be propagated here if it was present")
  protected Object exception;

  @ApiModelProperty(value = "The result of the canary judgement execution")
  protected CanaryResult result;

  @ApiModelProperty(value = "ISO 8061 String - The start time that was used to query the metric source for this judgement")
  protected String judgementStartTimeIso;

  @ApiModelProperty(value = "Epoch Millis - The start time that was used to query the metric source for this judgement")
  protected Long judgementStartTimeMillis;

  @ApiModelProperty(value = "ISO 8061 String - The end time that was used to query the metric source for this judgement")
  protected String judgementEndTimeIso;

  @ApiModelProperty(value = "Epoch Millis - The end time that was used to query the metric source for this judgement")
  protected Long judgementEndTimeMillis;

  @NonNull
  @Builder.Default
  @ApiModelProperty(value = "Any warnings that occurred during the canary judgement will be present here")
  List<String> warnings = new LinkedList<>();

  @ApiModelProperty(value = "The metric set pair list id for this canary judgement execution, can be used for getting the raw metrics via the API")
  protected String metricSetPairListId;

  // (startTime - buildTime) should indicate the time it was in the queue before starting.
  // (endTime - buildTime) should indicate the total time it took from request to result.
  // (endTime - startTime) should be the amount of time the canary was actually running.
  @ApiModelProperty(value = "epoch millis - buildTime is when the pipeline was first created.")
  protected Long buildTimeMillis;

  @ApiModelProperty(value = "ISO 8061 string - buildTime is when the pipeline was first created.")
  protected String buildTimeIso;

  @ApiModelProperty(value = "epoch millis - startTime refers to the time the pipeline started running.")
  protected Long startTimeMillis;

  @ApiModelProperty(value = "ISO 8061 string - startTime refers to the time the pipeline started running.")
  protected String startTimeIso;

  @ApiModelProperty(value = "epoch millis - endTime refers to the time the pipeline ended, either successfully or unsuccessfully.")
  protected Long endTimeMillis;

  @ApiModelProperty(value = "ISO 8061 string - endTime refers to the time the pipeline ended, either successfully or unsuccessfully.")
  protected String endTimeIso;

  @ApiModelProperty(value = "If set, these are the account names used for this run.")
  protected String storageAccountName;

  @ApiModelProperty(value = "If set, these are the account names used for this run.")
  protected String configurationAccountName;
}
