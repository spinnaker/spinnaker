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

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.netflix.kayenta.canary.CanaryClassifierThresholdsConfig;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

import javax.validation.constraints.NotNull;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

@Data
@Builder
@ToString
@AllArgsConstructor
@NoArgsConstructor
@ApiModel(description="The canary analysis configuration request object for initiating a canary analysis execution.")
public class CanaryAnalysisExecutionRequest {

  @NotNull
  @ApiModelProperty(value =
      "This is a list of Canary Analysis Execution Request scopes. " +
      "This directs the Canary Analysis Execution where to find the experiment and control metrics in the metrics source. " +
      "The list must have at least one value.")
  private List<CanaryAnalysisExecutionRequestScope> scopes;

  @NotNull
  @ApiModelProperty(value =
      "The thresholds that will be used for the canary judgements. " +
      "When multiple judgements are occurring during the lifetime of this execution, the last judgement must have a score " +
      "that meets or exceeds the pass threshold, all previous judgement scores must meet or exceed the marginal score.")
  private CanaryClassifierThresholdsConfig thresholds;

  @ApiModelProperty(value =
      "This is the amount of time in minutes the analysis phase of the canary analysis execution will last. " +
      "Either this value or endTimeIso (in scopes) must be set. ")
  private Long lifetimeDurationMins;

  @NotNull
  @Builder.Default
  @ApiModelProperty(value =
      "This is how long the canary analysis execution will wait before beginning the analysis phase. " +
      "This can be useful in a continuous integration situation where the canary analysis execution is triggered asynchronously " +
      "and metrics are ready for consumption after a time period.")
  private Long beginAfterMins = 0L;

  @NotNull
  @Builder.Default
  @ApiModelProperty(value =
      "If this optional value is supplied, then the canary analysis execution will perform judgements on a sliding time window. " +
      "The judgements will be from endTime - lookbackMins to startTime + (judgementNumber * interval). " +
      "If lookbackMins is not exactly equal to interval, then the metrics analyzed will be overlapping or discontiguous.\n" +
      "If this field is omitted, the judgements will be performed on a growing time window, " +
      "from startTime + (judgementNumber - 1 * interval) to startTime + (judgementNumber * interval).\n")
  private Long lookbackMins = 0L;

  @ApiModelProperty(value =
      "The value of analysisIntervalMins is used to calculate how many judgements will occur over the lifetime of the canary analysis execution.\n" +
      "If this field is omitted then it will default to lifetime.\n" +
      "If this field is set to a value greater than lifetime, it will be reset to lifetime.")
  private Long analysisIntervalMins;

  @ApiModelProperty(value =
      "A map of customizable data that among other things can be used in org-specific external modules such as event " +
      "listeners to handle notifications such as Slack, email, async http callbacks, etc.\n" +
      "The contents of this field don't have an effect on the actual canary analysis execution.")
  protected Map<String, Object> siteLocal;

  @JsonIgnore
  public Duration getBeginCanaryAnalysisAfterAsDuration() {
    return Duration.ofMinutes(beginAfterMins);
  }

  @JsonIgnore
  public Duration getLifetimeDuration() {
    if (lifetimeDurationMins != null) {
      return Duration.ofMinutes(lifetimeDurationMins);
    }
    return null;
  }

  @JsonIgnore
  public Instant getBeginCanaryAnalysisAfterAsInstant() {
    return Instant.ofEpochMilli(getBeginCanaryAnalysisAfterAsDuration().toMillis());
  }

  @JsonIgnore
  public Instant getStartTime() {
    return scopes.get(0).getStartTimeAsInstant();
  }

  @JsonIgnore
  public Instant getEndTime() {
    return scopes.get(0).getEndTimeAsInstant();
  }

  @JsonIgnore
  public Duration getStep() {
    return Duration.ofSeconds(scopes.get(0).getStep());
  }

  @JsonIgnore
  public Duration getLookBackAsDuration() {
    return Duration.ofMinutes(lookbackMins);
  }

  @JsonIgnore
  public Instant getLookBackAsInstant() {
    return Instant.ofEpochMilli(getLookBackAsDuration().toMillis());
  }
}
