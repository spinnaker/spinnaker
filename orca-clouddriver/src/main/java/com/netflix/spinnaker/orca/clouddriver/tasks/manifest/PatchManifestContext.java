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
import com.google.common.collect.ImmutableList;
import com.netflix.spinnaker.kork.artifacts.model.Artifact;
import java.util.List;
import java.util.Map;
import javax.annotation.Nonnull;
import lombok.Builder;
import lombok.Value;

@Value
@JsonDeserialize(builder = PatchManifestContext.PatchManifestContextBuilder.class)
@Builder(builderClassName = "PatchManifestContextBuilder", toBuilder = true)
public class PatchManifestContext implements ManifestContext {
  private Object patchBody;
  private Source source;

  private String manifestArtifactId;
  private Artifact manifestArtifact;
  private String manifestArtifactAccount;
  private String manifestName;

  private List<String> requiredArtifactIds;
  private List<BindArtifact> requiredArtifacts;

  @Builder.Default private boolean skipExpressionEvaluation = false;

  @Builder.Default
  private PatchManifestContext.Options options = PatchManifestContext.Options.builder().build();

  @Builder(builderClassName = "OptionsBuilder", toBuilder = true)
  @JsonDeserialize(builder = PatchManifestContext.Options.OptionsBuilder.class)
  @Value
  static class Options {
    @Builder.Default private MergeStrategy mergeStrategy = MergeStrategy.STRATEGIC;
    @Builder.Default private boolean record = true;

    @JsonPOJOBuilder(withPrefix = "")
    static class OptionsBuilder {}
  }

  public enum MergeStrategy {
    @JsonProperty("strategic")
    STRATEGIC,

    @JsonProperty("json")
    JSON,

    @JsonProperty("merge")
    MERGE
  }

  @Nonnull
  @Override
  public List<Map<Object, Object>> getManifests() {
    /*
     * Clouddriver expects patchBody to be a List when the mergeStrategy is json, and a Map otherwise.
     * Prior to Spinnaker 1.15, Deck sent either a List or Map depending on merge strategy, and Orca
     * deserialized it as an Object. In Spinnaker 1.15 and 1.16, a bug was introduced where patchBody
     * was deserialized as a Map regardless of merge strategy. Starting in 1.17, Deck will send a List
     * regardless of merge strategy, but we should handle receiving a Map from pipelines configured
     * in 1.15 and 1.16.
     */
    if (patchBody == null) {
      return ImmutableList.of();
    }
    if (patchBody instanceof List) {
      return ImmutableList.copyOf((List) patchBody);
    }
    return ImmutableList.of((Map<Object, Object>) patchBody);
  }

  @JsonPOJOBuilder(withPrefix = "")
  public static class PatchManifestContextBuilder {}
}
