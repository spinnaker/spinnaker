/*
 * Copyright 2019 Pivotal, Inc.
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

package com.netflix.spinnaker.igor.artifactory.model;

import com.netflix.spinnaker.kork.artifacts.model.Artifact;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ArtifactoryArtifactTest {
  @Test
  void toMatchableArtifact() {
    ArtifactoryArtifact artifact = new ArtifactoryArtifact();
    artifact.setPath("io/pivotal/spinnaker/demo/0.1.0-dev.20+d9a14fb");
    artifact.setRepo("libs-demo-local");

    Artifact matchableArtifact = artifact.toMatchableArtifact(ArtifactoryRepositoryType.Maven);
    assertThat(matchableArtifact).isNotNull();
    assertThat(matchableArtifact.getType()).isEqualTo("maven/file");
    assertThat(matchableArtifact.getReference()).isEqualTo("io.pivotal.spinnaker:demo:0.1.0-dev.20+d9a14fb");
    assertThat(matchableArtifact.getVersion()).isEqualTo("0.1.0-dev.20+d9a14fb");
    assertThat(matchableArtifact.getName()).isEqualTo("io.pivotal.spinnaker:demo");
  }
}
