/*
 * Copyright 2019 Pivotal, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
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
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
import lombok.Data;

public interface ManifestContext {
  Source getSource();

  String getManifestArtifactId();

  Artifact getManifestArtifact();

  String getManifestArtifactAccount();

  List<String> getRequiredArtifactIds();

  List<BindArtifact> getRequiredArtifacts();

  boolean isSkipExpressionEvaluation();

  /**
   * @return A manifest provided as direct text input in the stage definition. Deploy and Patch
   *     Manifest stages have differently named model elements describing this one concept, so for
   *     backwards compatibility we must map their individual model elements to use them generally.
   */
  @Nullable
  List<Map<Object, Object>> getManifests();

  enum Source {
    @JsonProperty("text")
    Text,

    @JsonProperty("artifact")
    Artifact
  }

  @Data
  class BindArtifact {
    @Nullable private String expectedArtifactId;

    @Nullable private Artifact artifact;
  }
}
