/*
 * Copyright 2017 Netflix, Inc.
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
package com.netflix.kayenta.canary;

import com.netflix.kayenta.canary.results.CanaryResult;
import lombok.Builder;
import lombok.Data;

import javax.validation.constraints.NotNull;
import java.util.Map;

@Data
@Builder
public class CanaryExecutionStatusResponse {

  @NotNull
  protected Map<String, String> stageStatus;

  @NotNull
  protected Boolean complete;

  @NotNull
  protected String status;

  protected CanaryResult result;

  protected String metricSetPairListId;

  //
  // buildTime is when the pipeline was first created.
  // startTime refers to the time the pipeline started running.
  // endTime refers to the time the pipeline ended, either successfully or unsuccessfully.
  //
  // (startTime - buildTime) should indicate the time it was in the queue before starting.
  // (endTime - buildTime) should indicate the total time it took from request to result.
  // (endTime - startTime) should be the amount of time the canary was actually running.
  //
  protected Long buildTimeMillis;
  protected String buildTimeIso;
  protected Long startTimeMillis;
  protected String startTimeIso;
  protected Long endTimeMillis;
  protected String endTimeIso;
}
