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
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@Data
@Builder
@ToString
@AllArgsConstructor
@NoArgsConstructor
@ApiModel(description="Defines how to find the metrics for the experiment and control in the metrics source")
public class CanaryAnalysisExecutionRequestScope {

  @Builder.Default
  @ApiModelProperty(
      value = "TODO: someone who uses this, should update this",
      example = "default")
  String scopeName = "default";

  @ApiModelProperty(
      value = "Value used in the metric source metrics service to fetch the data for the control. " +
          "The ASG name is the default value supplied via the Spinnaker UX when using AWS EC2.",
      example = "examplemicroservice--control-v001")
  String controlScope;

  @ApiModelProperty(
      value = "The location of the control, used by some metrics sources to further differentiate metrics",
      example = "us-west-2")
  String controlLocation;

  @ApiModelProperty(
      value = "Value used in the metric source metrics service to fetch the data for the experiment. " +
          "The ASG name is the default value supplied via the Spinnaker UX when using AWS EC2.",
      example = "examplemicroservice--experiment-v001")
  String experimentScope;

  @ApiModelProperty(
      value = "The location of the experiment, used by some metrics sources to further differentiate metrics.",
      example = "us-west-2")
  String experimentLocation;

  @ApiModelProperty(
      value = "If supplied this value will be used instead of the current time at execution for looking up metrics.",
      example = "2018-12-17T20:56:39.689Z"
  )
  String startTimeIso;

  @ApiModelProperty(
      value = "Either this value or lifetime from the parent object must be supplied." +
          "If supplied then the value of this field will be used to calculate the lifetime of the analysis execution.\n" +
          "This field takes precedent over lifetime",
      example = "2018-12-17T21:56:39.689Z"
  )
  String endTimeIso;

  @ApiModelProperty(
      value = "The frequency of data points that will be requested from the metrics sources when querying for metrics.\n" +
          "Defaults to 60, meaning a data point ever 60 seconds is the resolution of data that will be requested if applicable from the datasource.",
      example = "60"
  )
  @Builder.Default
  Long step = 60L;

  @ApiModelProperty(
      value = "Additional scope defining key values, some metric sources have required scope params such as New Relic " +
          "and SignalFx which require _scope_key to be supplied."
  )
  @Builder.Default
  Map<String, String> extendedScopeParams = new HashMap<>();

  @JsonIgnore
  public Instant getStartTimeAsInstant() {
    if (startTimeIso != null) {
      return Instant.parse(startTimeIso);
    }
    return null;
  }

  @JsonIgnore
  public Instant getEndTimeAsInstant() {
    if (endTimeIso != null) {
      return Instant.parse(endTimeIso);
    }
    return null;
  }
}
