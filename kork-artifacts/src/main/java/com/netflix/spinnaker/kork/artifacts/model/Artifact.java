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
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.HashMap;
import java.util.Map;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@JsonIgnoreProperties("kind")
public class Artifact {
  @JsonProperty("type")
  private String type;

  @JsonProperty("customKind")
  private boolean customKind;

  @JsonProperty("name")
  private String name;

  @JsonProperty("version")
  private String version;

  @JsonProperty("location")
  private String location;

  @JsonProperty("reference")
  private String reference;

  @JsonProperty("metadata")
  private Map<String, Object> metadata;

  @JsonProperty("artifactAccount")
  private String artifactAccount;

  @JsonProperty("provenance")
  private String provenance;

  @JsonProperty("uuid")
  private String uuid;

  // Deprecated as consumers should be using a builder; in the future this constructor will be
  // removed from the public API
  @Deprecated
  public Artifact(
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
    this.metadata = metadata;
    this.artifactAccount = artifactAccount;
    this.provenance = provenance;
    this.uuid = uuid;
  }

  // Deprecated as consumers should be using a builder; in the future this constructor will be
  // removed from the public API
  @Deprecated
  public Artifact() {}

  // Add extra, unknown data to the metadata map:
  @JsonAnySetter
  public void putMetadata(String key, Object value) {
    if (metadata == null) {
      metadata = new HashMap<>();
    }
    metadata.put(key, value);
  }
}
