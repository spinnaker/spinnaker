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

import static org.assertj.core.api.Assertions.assertThat;

import com.netflix.spinnaker.igor.artifactory.model.ArtifactoryItem.*;
import com.netflix.spinnaker.kork.artifacts.model.Artifact;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class ArtifactoryItemTest {
  @Test
  void toMatchableArtifact() {
    ArtifactoryItem artifact = new ArtifactoryItem();
    artifact.setPath("io/pivotal/spinnaker/demo/0.1.0-dev.20+d9a14fb");
    artifact.setRepo("libs-demo-local");

    Artifact matchableArtifact = artifact.toMatchableArtifact(ArtifactoryRepositoryType.Maven);
    assertThat(matchableArtifact).isNotNull();
    assertThat(matchableArtifact.getType()).isEqualTo("maven/file");
    assertThat(matchableArtifact.getReference())
        .isEqualTo("io.pivotal.spinnaker:demo:0.1.0-dev.20+d9a14fb");
    assertThat(matchableArtifact.getVersion()).isEqualTo("0.1.0-dev.20+d9a14fb");
    assertThat(matchableArtifact.getName()).isEqualTo("io.pivotal.spinnaker:demo");
  }

  @Test
  void toMatchableArtifactWithBuild() {
    ArtifactoryItem artifact = new ArtifactoryItem();
    artifact.setPath("io/pivotal/spinnaker/demo/0.1.0-dev.20+d9a14fb");
    artifact.setRepo("libs-demo-local");

    final ArtifactoryBuild expectedBuild =
        new ArtifactoryBuild(
            "2019-04-25T01:04:15.980Z",
            "artifactory_build_info_maven",
            "3",
            "http://localhost:7080/job/artifactory_build_info_maven/3/");

    final List<ArtifactoryBuild> builds = new ArrayList<>();
    builds.add(
        new ArtifactoryBuild(
            "2019-04-24T19:36:35.486Z",
            "artifactory_build_info_maven",
            "1",
            "http://localhost:7080/job/artifactory_build_info_maven/1/"));
    builds.add(expectedBuild);
    builds.add(
        new ArtifactoryBuild(
            "2019-04-25T00:56:26.723Z",
            "artifactory_build_info_maven",
            "2",
            "http://localhost:7080/job/artifactory_build_info_maven/2/"));
    final List<ArtifactoryModule> modules = new ArrayList<>();
    modules.add(new ArtifactoryModule(builds));
    final List<ArtifactoryArtifact> artifacts = new ArrayList<>();
    artifacts.add(new ArtifactoryArtifact(modules));
    artifact.setArtifacts(artifacts);

    Artifact matchableArtifact = artifact.toMatchableArtifact(ArtifactoryRepositoryType.Maven);
    assertThat(matchableArtifact.getMetadata().get("build")).isEqualTo(expectedBuild);
  }
}
