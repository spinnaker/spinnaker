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

import com.fasterxml.jackson.annotation.JsonProperty;
import com.netflix.spinnaker.kork.artifacts.model.Artifact;
import java.util.*;
import javax.annotation.Nullable;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
public class ArtifactoryItem {
  private String repo;
  private String path;
  private List<ArtifactoryArtifact> artifacts;

  @Nullable
  public Artifact toMatchableArtifact(ArtifactoryRepositoryType repoType) {
    switch (repoType) {
      case Maven:
        String[] pathParts = path.split("/");
        String version = pathParts[pathParts.length - 1];
        String artifactId = pathParts[pathParts.length - 2];

        String[] groupParts = Arrays.copyOfRange(pathParts, 0, pathParts.length - 2);
        String group = String.join(".", groupParts);

        final Artifact.ArtifactBuilder artifactBuilder =
            Artifact.builder()
                .type("maven/file")
                .reference(group + ":" + artifactId + ":" + version)
                .name(group + ":" + artifactId)
                .version(version)
                .provenance(repo);

        if (artifacts != null && !artifacts.isEmpty()) {
          final ArtifactoryArtifact artifact = artifacts.get(0);
          if (artifact.modules != null && !artifact.modules.isEmpty()) {
            final ArtifactoryModule module = artifact.modules.get(0);
            if (module.builds != null && !module.builds.isEmpty()) {
              module.builds.sort((o1, o2) -> o2.created.compareTo(o1.created));
              final ArtifactoryBuild build = module.builds.get(0);
              final Map<String, Object> metadata = new HashMap<>();
              metadata.put("build", build);
              artifactBuilder.metadata(metadata);
            }
          }
        }

        return artifactBuilder.build();
    }
    return null;
  }

  @Data
  @NoArgsConstructor
  @AllArgsConstructor
  public static class ArtifactoryArtifact {
    private List<ArtifactoryModule> modules;
  }

  @Data
  @NoArgsConstructor
  @AllArgsConstructor
  public static class ArtifactoryModule {
    private List<ArtifactoryBuild> builds;
  }

  @Data
  @NoArgsConstructor
  @AllArgsConstructor
  public static class ArtifactoryBuild {
    @JsonProperty("build.created")
    private String created;

    @JsonProperty("build.name")
    private String name;

    @JsonProperty("build.number")
    private String number;

    @JsonProperty("build.url")
    private String url;
  }
}
