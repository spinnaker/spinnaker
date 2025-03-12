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

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

@Data
@Builder
@ToString
@AllArgsConstructor
@NoArgsConstructor
@Schema(
    description =
        "Defines where to find the metrics for the experiment and control in the metrics source.")
public class CanaryAnalysisExecutionRequestScope {

  @Builder.Default
  @Schema(description = "Specifies unique name of the metrics scope.", example = "default")
  String scopeName = "default";

  @Schema(
      description =
          "This value is used to fetch the data for the control from the metrics service. "
              + "It is often a server group name (e.g. ASG on EC2 or MIG on GCE).",
      example = "examplemicroservice--control-v001")
  String controlScope;

  @Schema(
      description =
          "This is the location of the control which is used by some metrics sources to further differentiate metrics."
              + "Examples include a region or zone.",
      example = "us-west-2")
  String controlLocation;

  @Schema(
      description =
          "The time offset in minutes to be subtracted from the control start and end time. If none is provided, the control is "
              + "evaluated at the same time as the experiment.",
      example = "10")
  @Builder.Default
  Long controlOffsetInMinutes = 0L;

  @Schema(
      description =
          "This value is used to fetch the data for the experiment from the metrics service. "
              + "It is often a server group name (e.g. ASG on EC2 or MIG on GCE).",
      example = "examplemicroservice--experiment-v001")
  String experimentScope;

  @Schema(
      description =
          "This is the location of the experiment which is used by some metrics sources to further differentiate metrics."
              + "Examples include a region or zone.",
      example = "us-west-2")
  String experimentLocation;

  @Schema(
      description =
          "This optional value indicates the start time for looking up metrics. "
              + "If this value is omitted, the current time at execution will be used instead.",
      example = "2018-12-17T20:56:39.689Z")
  String startTimeIso;

  @Schema(
      description =
          "This value will be used to calculate the length of time of the analysis execution.\n"
              + "Either this value or lifetime (in the parent object) must be supplied. "
              + "This field takes precedence over lifetime.",
      example = "2018-12-17T21:56:39.689Z")
  String endTimeIso;

  @Schema(
      description =
          "This indicates the period in seconds for how often data points will be requested from the metrics sources when querying for metrics.\n"
              + "The value defaults to 60 which means a data point will be requested every 60 seconds from the data source.\n"
              + "The resulting resolution (data points per the calculated interval) needs to be at least 50 "
              + "in order to produce accurate results.",
      example = "60")
  @Builder.Default
  Long step = 60L;

  @Schema(
      description =
          "This is an additional scope to define key values as some metric sources require additional scope params. "
              + "For example New Relic and SignalFx require _scope_key to be supplied.")
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
