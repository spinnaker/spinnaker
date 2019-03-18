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

package com.netflix.spinnaker.orca.bakery.tasks.manifests;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.netflix.spinnaker.kork.artifacts.model.ExpectedArtifact;
import lombok.Getter;

import java.util.List;
import java.util.Map;

@Getter
public class BakeManifestContext {
  private final List<CreateBakeManifestTask.InputArtifactPair> inputArtifacts;
  private final List<ExpectedArtifact> expectedArtifacts;
  private final Map<String, Object> overrides;
  private final Boolean evaluateOverrideExpressions;
  private final String templateRenderer;
  private final String outputName;
  private final String namespace;

  // There does not seem to be a way to auto-generate a constructor using our current version of Lombok (1.16.20) that
  // Jackson can use to deserialize.
  public BakeManifestContext(
    @JsonProperty("inputArtifacts") List<CreateBakeManifestTask.InputArtifactPair> inputArtifacts,
    @JsonProperty("expectedArtifacts") List<ExpectedArtifact> expectedArtifacts,
    @JsonProperty("overrides") Map<String, Object> overrides,
    @JsonProperty("evaluateOverrideExpressions") Boolean evaluateOverrideExpressions,
    @JsonProperty("templateRenderer") String templateRenderer,
    @JsonProperty("outputName") String outputName,
    @JsonProperty("namespace") String namespace
  ) {
    this.inputArtifacts = inputArtifacts;
    this.expectedArtifacts = expectedArtifacts;
    this.overrides = overrides;
    this.evaluateOverrideExpressions = evaluateOverrideExpressions;
    this.templateRenderer = templateRenderer;
    this.outputName = outputName;
    this.namespace = namespace;
  }
}
