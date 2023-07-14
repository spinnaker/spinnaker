/*
 * Copyright 2023 Grab Holdings, Inc.
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

package com.netflix.spinnaker.rosco.manifests;

import com.netflix.spinnaker.kork.artifacts.model.Artifact;
import com.netflix.spinnaker.kork.exceptions.SpinnakerException;
import com.netflix.spinnaker.kork.retrofit.exceptions.SpinnakerHttpException;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public abstract class HelmBakeTemplateUtils<T extends BakeManifestRequest> {
  private static final String MANIFEST_SEPARATOR = "---\n";
  private static final Pattern REGEX_TESTS_MANIFESTS =
      Pattern.compile("# Source: .*/templates/tests/.*");

  private final ArtifactDownloader artifactDownloader;

  protected HelmBakeTemplateUtils(ArtifactDownloader artifactDownloader) {
    this.artifactDownloader = artifactDownloader;
  }

  public ArtifactDownloader getArtifactDownloader() {
    return artifactDownloader;
  }

  public abstract String fetchFailureMessage(String description, Exception e);

  public String removeTestsDirectoryTemplates(String inputString) {
    return Arrays.stream(inputString.split(MANIFEST_SEPARATOR))
        .filter(manifest -> !REGEX_TESTS_MANIFESTS.matcher(manifest).find())
        .collect(Collectors.joining(MANIFEST_SEPARATOR));
  }

  protected Path downloadArtifactToTmpFile(BakeManifestEnvironment env, Artifact artifact)
      throws IOException {
    String fileName = UUID.randomUUID().toString();
    Path targetPath = env.resolvePath(fileName);
    artifactDownloader.downloadArtifactToFile(artifact, targetPath);
    return targetPath;
  }

  public abstract String getHelmExecutableForRequest(T request);

  protected List<Path> getValuePaths(List<Artifact> artifacts, BakeManifestEnvironment env) {
    List<Path> valuePaths = new ArrayList<>();

    try {
      // not a stream to keep exception handling cleaner
      for (Artifact valueArtifact : artifacts.subList(1, artifacts.size())) {
        valuePaths.add(downloadArtifactToTmpFile(env, valueArtifact));
      }
    } catch (SpinnakerHttpException e) {
      throw new SpinnakerHttpException(fetchFailureMessage("values file", e), e);
    } catch (IOException | SpinnakerException e) {
      throw new IllegalStateException(fetchFailureMessage("values file", e), e);
    }

    return valuePaths;
  }

  protected Path getHelmTypePathFromArtifact(
      BakeManifestEnvironment env, List<Artifact> inputArtifacts, String filePath)
      throws IOException {
    Path helmTypeFilePath;

    Artifact helmTypeTemplateArtifact = inputArtifacts.get(0);
    String artifactType = Optional.ofNullable(helmTypeTemplateArtifact.getType()).orElse("");

    if ("git/repo".equals(artifactType)) {
      env.downloadArtifactTarballAndExtract(getArtifactDownloader(), helmTypeTemplateArtifact);

      helmTypeFilePath = env.resolvePath(Optional.ofNullable(filePath).orElse(""));
    } else {
      try {
        helmTypeFilePath = downloadArtifactToTmpFile(env, helmTypeTemplateArtifact);
      } catch (SpinnakerHttpException e) {
        throw new SpinnakerHttpException(fetchFailureMessage("template", e), e);
      } catch (IOException | SpinnakerException e) {
        throw new IllegalStateException(fetchFailureMessage("template", e), e);
      }
    }

    return helmTypeFilePath;
  }
}
