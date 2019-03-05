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
import lombok.Data;

import javax.annotation.Nullable;
import java.util.Arrays;

@Data
public class ArtifactoryArtifact {
  private String repo;
  private String path;

  @Nullable
  public Artifact toMatchableArtifact(ArtifactoryRepositoryType repoType) {
    switch(repoType) {
      case Maven:
        String[] pathParts = path.split("/");
        String version = pathParts[pathParts.length - 1];
        String artifactId = pathParts[pathParts.length - 2];

        String[] groupParts = Arrays.copyOfRange(pathParts, 0, pathParts.length - 2);
        String group = String.join(".", groupParts);

        return Artifact.builder().type("maven/file")
          .reference(group + ":" + artifactId + ":" + version)
          .name(group + ":" + artifactId)
          .version(version)
          .provenance(repo)
          .build();
    }
    return null;
  }
}
