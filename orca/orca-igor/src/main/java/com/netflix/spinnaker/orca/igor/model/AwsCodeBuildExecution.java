/*
 * Copyright 2020 Amazon.com, Inc.
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
import com.netflix.spinnaker.orca.api.pipeline.models.ExecutionStatus;
import java.util.List;
import lombok.Data;
import lombok.Getter;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class AwsCodeBuildExecution {
  private final String arn;
  private final Status status;
  private final AwsCodeBuildLogs logs;
  private final String buildUrl;
  private final List<AwsCodeBuildEnvironmentVariable> exportedEnvironmentVariables;

  @JsonCreator
  public AwsCodeBuildExecution(
      @JsonProperty("arn") String arn,
      @JsonProperty("buildStatus") String buildStatus,
      @JsonProperty("logs") AwsCodeBuildLogs logs,
      @JsonProperty("exportedEnvironmentVariables")
          List<AwsCodeBuildEnvironmentVariable> exportedEnvironmentVariables) {
    this.arn = arn;
    this.status = Status.fromString(buildStatus);
    this.buildUrl = getBuildUrl(arn);
    this.logs = logs;
    this.exportedEnvironmentVariables = exportedEnvironmentVariables;
  }

  private String getBuildUrl(String arn) {
    final String[] arnSplit = arn.split("/");
    final String region = arnSplit[0].split(":")[3];
    final String buildId = arnSplit[1];
    final String project = buildId.split(":")[0];
    return String.format(
        "https://%s.console.aws.amazon.com/codesuite/codebuild/projects/%s/build/%s/log?region=%s",
        region, project, buildId, region);
  }

  @Data
  private static class AwsCodeBuildLogs {
    private String deepLink;
    private String s3DeepLink;
  }

  @Data
  private static class AwsCodeBuildEnvironmentVariable {
    private String name;
    private String value;
  }

  public enum Status {
    IN_PROGRESS(ExecutionStatus.RUNNING),
    SUCCEEDED(ExecutionStatus.SUCCEEDED),
    FAILED(ExecutionStatus.TERMINAL),
    FAULT(ExecutionStatus.TERMINAL),
    TIMED_OUT(ExecutionStatus.TERMINAL),
    STOPPED(ExecutionStatus.TERMINAL),
    UNKNOWN(ExecutionStatus.TERMINAL);

    @Getter private ExecutionStatus executionStatus;

    Status(ExecutionStatus executionStatus) {
      this.executionStatus = executionStatus;
    }

    public static Status fromString(String status) {
      try {
        return valueOf(status);
      } catch (NullPointerException | IllegalArgumentException e) {
        return UNKNOWN;
      }
    }
  }
}
