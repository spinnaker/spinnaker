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
import com.netflix.spinnaker.kork.artifacts.model.Artifact;
import lombok.Getter;

import java.util.Map;
import java.util.Optional;

@Getter
public class GoogleCloudBuildStageDefinition implements RetryableStageDefinition {
  private final String account;
  private final GoogleCloudBuild buildInfo;
  private final Map<String, Object> buildDefinition;
  private final String buildDefinitionSource;
  private final GoogleCloudBuildDefinitionArtifact buildDefinitionArtifact;
  private final int consecutiveErrors;

  // There does not seem to be a way to auto-generate a constructor using our current version of Lombok (1.16.20) that
  // Jackson can use to deserialize.
  public GoogleCloudBuildStageDefinition(
    @JsonProperty("account") String account,
    @JsonProperty("buildInfo") GoogleCloudBuild build,
    @JsonProperty("buildDefinition") Map<String, Object> buildDefinition,
    @JsonProperty("buildDefinitionSource") String buildDefinitionSource,
    @JsonProperty("buildDefinitionArtifact") GoogleCloudBuildDefinitionArtifact buildDefinitionArtifact,
    @JsonProperty("consecutiveErrors") Integer consecutiveErrors
  ) {
    this.account = account;
    this.buildInfo = build;
    this.buildDefinition = buildDefinition;
    this.buildDefinitionSource = buildDefinitionSource;
    this.buildDefinitionArtifact = Optional.ofNullable(buildDefinitionArtifact)
      .orElse(new GoogleCloudBuildDefinitionArtifact(null, null, null));
    this.consecutiveErrors = Optional.ofNullable(consecutiveErrors).orElse(0);
  }

  @Getter
  public static class GoogleCloudBuildDefinitionArtifact {
    private final Artifact artifact;
    private final String artifactAccount;
    private final String artifactId;

    public GoogleCloudBuildDefinitionArtifact(
      @JsonProperty("artifact") Artifact artifact,
      @JsonProperty("artifactAccount") String artifactAccount,
      @JsonProperty("artifactId") String artifactId
    ) {
      this.artifact = artifact;
      this.artifactAccount = artifactAccount;
      this.artifactId = artifactId;
    }
  }
}
