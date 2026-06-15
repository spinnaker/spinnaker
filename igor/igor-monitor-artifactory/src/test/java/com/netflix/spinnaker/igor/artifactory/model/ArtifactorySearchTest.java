/*
 * Copyright 2026 Wise, PLC.
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

package com.netflix.spinnaker.igor.artifactory.model;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ArtifactorySearchTest {

  @Test
  void getArtifactExtensionReturnsDefaultForRepoType() {
    ArtifactorySearch search = new ArtifactorySearch();
    search.setRepoType(ArtifactoryRepositoryType.FILE);

    assertThat(search.getArtifactExtension()).isEqualTo(".tgz");
  }

  @Test
  void getArtifactExtensionReturnsOverrideWhenSet() {
    ArtifactorySearch search = new ArtifactorySearch();
    search.setRepoType(ArtifactoryRepositoryType.FILE);
    search.setArtifactExtension(".tar.gz");

    assertThat(search.getArtifactExtension()).isEqualTo(".tar.gz");
  }

  @Test
  void getArtifactExtensionReturnsDefaultForMaven() {
    ArtifactorySearch search = new ArtifactorySearch();
    search.setRepoType(ArtifactoryRepositoryType.MAVEN);

    assertThat(search.getArtifactExtension()).isEqualTo(".pom");
  }

  @Test
  void getArtifactExtensionOverrideTakesPrecedenceOverMavenDefault() {
    ArtifactorySearch search = new ArtifactorySearch();
    search.setRepoType(ArtifactoryRepositoryType.MAVEN);
    search.setArtifactExtension(".jar");

    assertThat(search.getArtifactExtension()).isEqualTo(".jar");
  }
}
