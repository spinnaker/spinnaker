/*
 *
 * Copyright 2019 Netflix, Inc.
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
 *
 */
package com.netflix.spinnaker.igor.artifacts;

import com.netflix.spinnaker.igor.model.ArtifactServiceProvider;
import com.netflix.spinnaker.kork.artifacts.model.Artifact;
import com.netflix.spinnaker.kork.web.exceptions.NotFoundException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class TestArtifactService implements ArtifactService {
  @Override
  public ArtifactServiceProvider artifactServiceProvider() {
    return ArtifactServiceProvider.CUSTOM;
  }

  @Override
  public List<String> getArtifactVersions(String name) {
    if (!name.equals("test")) {
      return Collections.emptyList();
    }
    List<String> versions = new ArrayList<>();
    versions.add("v0.1.0");
    versions.add("v0.2.0");
    versions.add("v0.3.0");
    versions.add("v0.4.0");
    return versions;
  }

  @Override
  public Artifact getArtifact(String name, String version) {
    if (!name.equals("test") && !version.equals("v0.4.0")) {
      throw new NotFoundException("Artifact not found");
    }
    return new Artifact(
        "deb",
        false,
        "test",
        "v0.4.0",
        "artifactory//test/v0.4.0",
        "artifactory//test/v0.4.0",
        Collections.emptyMap(),
        "testAccount",
        "jenkins//test/v0.4.0",
        "1234");
  }
}
