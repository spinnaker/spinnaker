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

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.collect.ImmutableList;
import com.netflix.spinnaker.kork.artifacts.model.Artifact;
import com.netflix.spinnaker.kork.artifacts.model.ExpectedArtifact;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import lombok.Getter;

@Getter
public class CIStageDefinition implements RetryableStageDefinition {
  private final String master;
  private final String job;
  private final String propertyFile;
  private final Integer buildNumber;
  private final BuildInfo buildInfo;
  private final boolean waitForCompletion;
  private final List<ExpectedArtifact> expectedArtifacts;
  private final int consecutiveErrors;

  // There does not seem to be a way to auto-generate a constructor using our current version of
  // Lombok (1.16.20) that
  // Jackson can use to deserialize.
  public CIStageDefinition(
      @JsonProperty("master") String master,
      @JsonProperty("job") String job,
      @JsonProperty("property") String propertyFile,
      @JsonProperty("buildNumber") Integer buildNumber,
      @JsonProperty("buildInfo") BuildInfo buildInfo,
      @JsonProperty("waitForCompletion") Boolean waitForCompletion,
      @JsonProperty("expectedArtifacts") List<ExpectedArtifact> expectedArtifacts,
      @JsonProperty("consecutiveErrors") Integer consecutiveErrors) {
    this.master = master;
    this.job = job;
    this.propertyFile = propertyFile;
    this.buildNumber = buildNumber;
    this.buildInfo = Optional.ofNullable(buildInfo).orElse(new BuildInfo(null));
    this.waitForCompletion = Optional.ofNullable(waitForCompletion).orElse(true);
    this.expectedArtifacts =
        Collections.unmodifiableList(
            Optional.ofNullable(expectedArtifacts).orElse(Collections.emptyList()));
    this.consecutiveErrors = Optional.ofNullable(consecutiveErrors).orElse(0);
  }

  @Getter
  public static class BuildInfo {
    private final ImmutableList<Artifact> artifacts;

    public BuildInfo(@JsonProperty("artifacts") List<Artifact> artifacts) {
      this.artifacts =
          Optional.ofNullable(artifacts).map(ImmutableList::copyOf).orElse(ImmutableList.of());
    }
  }
}
