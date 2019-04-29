/*
 * Copyright 2019 Google, Inc.
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

package com.netflix.spinnaker.orca.igor.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.netflix.spinnaker.orca.ExecutionStatus;
import lombok.Builder;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Builder
@Getter
@RequiredArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class GoogleCloudBuild {
  private final String id;
  private final Status status;
  private final String logUrl;

  @JsonCreator
  public GoogleCloudBuild(
    @JsonProperty("id") String id,
    @JsonProperty("status") String status,
    @JsonProperty("logUrl") String logUrl
  ) {
    this.id = id;
    this.status = Status.fromString(status);
    this.logUrl = logUrl;
  }

  public enum Status {
    STATUS_UNKNOWN(ExecutionStatus.RUNNING),
    QUEUED(ExecutionStatus.RUNNING),
    WORKING(ExecutionStatus.RUNNING),
    SUCCESS(ExecutionStatus.SUCCEEDED),
    FAILURE(ExecutionStatus.TERMINAL),
    INTERNAL_ERROR(ExecutionStatus.TERMINAL),
    TIMEOUT(ExecutionStatus.TERMINAL),
    CANCELLED(ExecutionStatus.TERMINAL);

    @Getter
    private ExecutionStatus executionStatus;

    Status(ExecutionStatus executionStatus) {
      this.executionStatus = executionStatus;
    }

    public static Status fromString(String status) {
      try {
        return valueOf(status);
      } catch (NullPointerException | IllegalArgumentException e) {
        return STATUS_UNKNOWN;
      }
    }
  }
}
