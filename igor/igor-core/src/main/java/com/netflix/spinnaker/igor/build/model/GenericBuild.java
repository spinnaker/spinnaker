/*
 * Copyright 2019 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 *
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.igor.build.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Wither;

@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@Wither
public class GenericBuild {
  private boolean building;
  private String fullDisplayName;
  private String name;
  private long number;
  private Integer duration;
  /** String representation of time in nanoseconds since Unix epoch */
  private String timestamp;

  private Result result;
  private List<GenericArtifact> artifacts;
  private List<TestResults> testResults;
  private String url;
  private String id;

  @JsonProperty("scm")
  private List<GenericGitRevision> genericGitRevisions;

  private Map<String, ?> properties;

  @Data
  @AllArgsConstructor
  @NoArgsConstructor
  public static class TestResults {
    private int failCount;
    private int skipCount;
    private int totalCount;
    private String urlName;
  }
}
