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
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import com.netflix.spinnaker.kork.artifacts.model.Artifact;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import lombok.Builder;
import lombok.Value;

@Builder(builderClassName = "DeployManifestContextBuilder", toBuilder = true)
@JsonDeserialize(builder = DeployManifestContext.DeployManifestContextBuilder.class)
@Value
public class DeployManifestContext implements ManifestContext {
  @Nullable private List<Map<Object, Object>> manifests;

  @Builder.Default @Nonnull
  private TrafficManagement trafficManagement = TrafficManagement.builder().build();

  private Source source;

  private String manifestArtifactId;
  private Artifact manifestArtifact;
  private String manifestArtifactAccount;

  private List<String> requiredArtifactIds;
  private List<BindArtifact> requiredArtifacts;

  @Builder.Default private boolean skipExpressionEvaluation = false;

  @Builder(builderClassName = "TrafficManagementBuilder", toBuilder = true)
  @JsonDeserialize(builder = DeployManifestContext.TrafficManagement.TrafficManagementBuilder.class)
  @Value
  public static class TrafficManagement {
    @Builder.Default private boolean enabled = false;
    @Nonnull @Builder.Default private Options options = Options.builder().build();

    @Builder(builderClassName = "OptionsBuilder", toBuilder = true)
    @JsonDeserialize(builder = DeployManifestContext.TrafficManagement.Options.OptionsBuilder.class)
    @Value
    public static class Options {
      @Builder.Default private boolean enableTraffic = false;
      @Builder.Default private List<String> services = Collections.emptyList();
      @Builder.Default private ManifestStrategyType strategy = ManifestStrategyType.NONE;

      @JsonPOJOBuilder(withPrefix = "")
      public static class OptionsBuilder {}
    }

    public enum ManifestStrategyType {
      @JsonProperty("redblack")
      RED_BLACK,

      @JsonProperty("highlander")
      HIGHLANDER,

      @JsonProperty("none")
      NONE
    }

    @JsonPOJOBuilder(withPrefix = "")
    public static class TrafficManagementBuilder {}
  }

  @Override
  public List<Map<Object, Object>> getManifests() {
    return manifests;
  }

  @JsonPOJOBuilder(withPrefix = "")
  public static class DeployManifestContextBuilder {}
}
