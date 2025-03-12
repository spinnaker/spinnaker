/*
 * Copyright 2020 Armory, Inc.
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

package com.netflix.spinnaker.orca.bakery.tasks.manifests.cf;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.netflix.spinnaker.kork.artifacts.model.ExpectedArtifact;
import java.util.List;
import lombok.Getter;

@Getter
public class BakeCloudFoundryManifestContext {

  private final List<BakeCloudFoundryManifestTask.InputArtifact> inputArtifacts;
  private final List<ExpectedArtifact> expectedArtifacts;
  private final String outputName;
  private final String templateRenderer = "CF";

  public BakeCloudFoundryManifestContext(
      @JsonProperty("inputArtifacts")
          List<BakeCloudFoundryManifestTask.InputArtifact> inputArtifacts,
      @JsonProperty("expectedArtifacts") List<ExpectedArtifact> expectedArtifact,
      @JsonProperty("outputName") String outputName) {
    this.inputArtifacts = inputArtifacts;
    this.expectedArtifacts = expectedArtifact;
    this.outputName = outputName;
  }
}
