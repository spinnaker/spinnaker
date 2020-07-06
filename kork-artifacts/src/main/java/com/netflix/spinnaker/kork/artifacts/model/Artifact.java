/*
 * Copyright 2017 Google, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
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

package com.netflix.spinnaker.kork.artifacts.model;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import com.netflix.spinnaker.kork.annotations.FieldsAreNullableByDefault;
import com.netflix.spinnaker.kork.annotations.MethodsReturnNonnullByDefault;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

@Getter
@ToString
@EqualsAndHashCode
@FieldsAreNullableByDefault
@JsonDeserialize(builder = Artifact.ArtifactBuilder.class)
// Use camelCase regardless of the ObjectMapper configuration. (Detailed comment in ArtifactTest.)
@JsonNaming
public final class Artifact {
  private final String type;
  private final boolean customKind;
  private final String name;
  private final String version;
  private final String location;
  private final String reference;
  @Nonnull private final Map<String, Object> metadata;
  private final String artifactAccount;
  private final String provenance;
  private final String uuid;

  @Builder(toBuilder = true)
  private Artifact(
      String type,
      boolean customKind,
      String name,
      String version,
      String location,
      String reference,
      Map<String, Object> metadata,
      String artifactAccount,
      String provenance,
      String uuid) {
    this.type = type;
    this.customKind = customKind;
    this.name = name;
    this.version = version;
    this.location = location;
    this.reference = reference;
    // Shallow copy the metadata map so changes to the input map after creating the artifact
    // don't affect its metadata.
    this.metadata = Optional.ofNullable(metadata).map(HashMap::new).orElseGet(HashMap::new);
    this.artifactAccount = artifactAccount;
    this.provenance = provenance;
    this.uuid = uuid;
  }

  @Nullable
  public Object getMetadata(String key) {
    return metadata.get(key);
  }

  @JsonIgnoreProperties("kind")
  @JsonPOJOBuilder(withPrefix = "")
  @MethodsReturnNonnullByDefault
  @JsonNaming
  public static class ArtifactBuilder {
    @Nonnull private Map<String, Object> metadata = new HashMap<>();

    public ArtifactBuilder metadata(@Nullable Map<String, Object> metadata) {
      this.metadata = Optional.ofNullable(metadata).orElseGet(HashMap::new);
      return this;
    }

    // Add extra, unknown data to the metadata map:
    @JsonAnySetter
    public ArtifactBuilder putMetadata(String key, Object value) {
      metadata.put(key, value);
      return this;
    }
  }
}
