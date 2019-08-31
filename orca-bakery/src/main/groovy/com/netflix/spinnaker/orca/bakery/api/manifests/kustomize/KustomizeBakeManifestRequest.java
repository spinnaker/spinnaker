/*
 * Copyright 2019 Armory
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

package com.netflix.spinnaker.orca.bakery.api.manifests.kustomize;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.netflix.spinnaker.kork.artifacts.model.Artifact;
import com.netflix.spinnaker.orca.bakery.api.manifests.BakeManifestRequest;
import com.netflix.spinnaker.orca.bakery.tasks.manifests.BakeManifestContext;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class KustomizeBakeManifestRequest extends BakeManifestRequest {
  @JsonProperty("inputArtifact")
  private Artifact inputArtifact;

  public KustomizeBakeManifestRequest(
      BakeManifestContext bakeManifestContext, Artifact inputArtifact, String outputArtifactName) {
    super(
        bakeManifestContext.getTemplateRenderer(),
        outputArtifactName,
        bakeManifestContext.getOutputName());
    this.inputArtifact = inputArtifact;
  }
}
