/*
 * Copyright 2018 Google, Inc.
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

package com.netflix.spinnaker.rosco.providers.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.kork.artifacts.model.Artifact;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;

public class PackerArtifactService {

  private Path tempDir = Paths.get(System.getProperty("java.io.tmpdir"));
  private ObjectMapper objectMapper = new ObjectMapper();

  public PackerArtifactService() throws IOException {
    if (!Files.isDirectory(tempDir)) {
      Files.createDirectories(tempDir);
    }
  }

  public Path writeArtifactsToFile(String bakeId, List<Artifact> artifacts) {
    Path artifactFile = getArtifactFilePath(bakeId);

    // If we were not passed any artifacts at all, write an empty array to the file rather
    // than null
    if (artifacts == null) {
      artifacts = new ArrayList<>();
    }

    try (BufferedOutputStream artifactStream =
        new BufferedOutputStream(
            Files.newOutputStream(
                artifactFile, StandardOpenOption.CREATE, StandardOpenOption.WRITE))) {
      objectMapper.writeValue(artifactStream, artifacts);
    } catch (IOException e) {
      throw new IllegalStateException("Could not write artifacts to file: " + e.getMessage());
    }

    return artifactFile;
  }

  public void deleteArtifactFile(String bakeId) {
    Path artifactFile = getArtifactFilePath(bakeId);

    try {
      Files.deleteIfExists(artifactFile);
    } catch (IOException e) {
      throw new IllegalStateException("Could not delete artifact file: " + e.getMessage());
    }
  }

  private Path getArtifactFilePath(String bakeId) {
    return tempDir.resolve(bakeId + "-artifacts.json");
  }
}
