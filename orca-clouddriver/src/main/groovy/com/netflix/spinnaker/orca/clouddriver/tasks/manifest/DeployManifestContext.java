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

package com.netflix.spinnaker.orca.clouddriver.tasks.manifest;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.netflix.spinnaker.kork.artifacts.model.Artifact;
import lombok.Getter;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;

@Getter
public class DeployManifestContext extends HashMap<String, Object> {
  private final String source;
  private final String manifestArtifactId;
  private final Artifact manifestArtifact;
  private final String manifestArtifactAccount;
  private final Boolean skipExpressionEvaluation;
  private final TrafficManagement trafficManagement;
  private final List<String> requiredArtifactIds;
  private final List<BindArtifact> requiredArtifacts;

  // There does not seem to be a way to auto-generate a constructor using our current version of Lombok (1.16.20) that
  // Jackson can use to deserialize.
  public DeployManifestContext(
    @JsonProperty("source") String source,
    @JsonProperty("manifestArtifactId") String manifestArtifactId,
    @JsonProperty("manifestArtifact") Artifact manifestArtifact,
    @JsonProperty("manifestArtifactAccount") String manifestArtifactAccount,
    @JsonProperty("skipExpressionEvaluation") Boolean skipExpressionEvaluation,
    @JsonProperty("trafficManagement") TrafficManagement trafficManagement,
    @JsonProperty("requiredArtifactIds") List<String> requiredArtifactIds,
    @JsonProperty("requiredArtifacts") List<BindArtifact> requiredArtifacts
  ){
    this.source = source;
    this.manifestArtifactId = manifestArtifactId;
    this.manifestArtifact = manifestArtifact;
    this.manifestArtifactAccount = manifestArtifactAccount;
    this.skipExpressionEvaluation = skipExpressionEvaluation;
    this.trafficManagement = Optional.ofNullable(trafficManagement).orElse(new TrafficManagement(false, null));
    this.requiredArtifactIds = requiredArtifactIds;
    this.requiredArtifacts = requiredArtifacts;
  }

  @Getter
  public static class BindArtifact {
    @Nullable
    private final String expectedArtifactId;

    @Nullable
    private final Artifact artifact;


    public BindArtifact(@JsonProperty("expectedArtifactId") @Nullable String expectedArtifactId,
                        @JsonProperty("artifact") @Nullable Artifact artifact) {
      this.expectedArtifactId = expectedArtifactId;
      this.artifact = artifact;
    }
  }

  @Getter
  public static class TrafficManagement {
    private final boolean enabled;
    private final Options options;

    public TrafficManagement (
      @JsonProperty("enabled") Boolean enabled,
      @JsonProperty("options") Options options
    ) {
      this.enabled = Optional.ofNullable(enabled).orElse(false);
      this.options = Optional.ofNullable(options).orElse(new Options(false, Collections.emptyList(), null));
    }

    @Getter
    public static class Options {
      private final boolean enableTraffic;
      private final List<String> services;
      private final ManifestStrategyType strategy;

      public Options(
        @JsonProperty("enableTraffic") Boolean enableTraffic,
        @JsonProperty("services") List<String> services,
        @JsonProperty("strategy") String strategy
      ) {
        this.enableTraffic = Optional.ofNullable(enableTraffic).orElse(false);
        this.services = Optional.ofNullable(services).orElse(Collections.emptyList());
        this.strategy = ManifestStrategyType.fromKey(strategy);
      }
    }
  }
}
