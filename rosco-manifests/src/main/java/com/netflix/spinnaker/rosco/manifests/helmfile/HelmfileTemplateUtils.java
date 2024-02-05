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

package com.netflix.spinnaker.rosco.manifests.helmfile;

import com.netflix.spinnaker.kork.artifacts.artifactstore.ArtifactStore;
import com.netflix.spinnaker.kork.artifacts.artifactstore.ArtifactStoreConfigurationProperties;
import com.netflix.spinnaker.kork.artifacts.model.Artifact;
import com.netflix.spinnaker.rosco.jobs.BakeRecipe;
import com.netflix.spinnaker.rosco.manifests.ArtifactDownloader;
import com.netflix.spinnaker.rosco.manifests.BakeManifestEnvironment;
import com.netflix.spinnaker.rosco.manifests.HelmBakeTemplateUtils;
import com.netflix.spinnaker.rosco.manifests.config.RoscoHelmConfigurationProperties;
import com.netflix.spinnaker.rosco.manifests.config.RoscoHelmfileConfigurationProperties;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class HelmfileTemplateUtils extends HelmBakeTemplateUtils<HelmfileBakeManifestRequest> {
  private final RoscoHelmfileConfigurationProperties helmfileConfigurationProperties;
  private final RoscoHelmConfigurationProperties helmConfigurationProperties =
      new RoscoHelmConfigurationProperties();

  public HelmfileTemplateUtils(
      ArtifactDownloader artifactDownloader,
      Optional<ArtifactStore> artifactStore,
      ArtifactStoreConfigurationProperties artifactStoreConfig,
      RoscoHelmfileConfigurationProperties helmfileConfigurationProperties) {
    super(artifactDownloader, artifactStore, artifactStoreConfig.getHelm());
    this.helmfileConfigurationProperties = helmfileConfigurationProperties;
  }

  public BakeRecipe buildBakeRecipe(
      BakeManifestEnvironment env, HelmfileBakeManifestRequest request) throws IOException {
    Path helmfileFilePath;

    List<Artifact> inputArtifacts = request.getInputArtifacts();
    if (inputArtifacts == null || inputArtifacts.isEmpty()) {
      throw new IllegalArgumentException("At least one input artifact must be provided to bake");
    }

    log.info("helmfileFilePath: '{}'", request.getHelmfileFilePath());
    helmfileFilePath =
        getHelmTypePathFromArtifact(env, inputArtifacts, request.getHelmfileFilePath());

    log.info("path to helmfile: {}", helmfileFilePath);
    return buildCommand(request, getValuePaths(inputArtifacts, env), helmfileFilePath);
  }

  public String fetchFailureMessage(String description, Exception e) {
    return "Failed to fetch helmfile " + description + ": " + e.getMessage();
  }

  public String getHelmExecutableForRequest(HelmfileBakeManifestRequest request) {
    return helmConfigurationProperties.getV3ExecutablePath();
  }

  public BakeRecipe buildCommand(
      HelmfileBakeManifestRequest request, List<Path> valuePaths, Path helmfileFilePath) {
    BakeRecipe result = new BakeRecipe();
    result.setName(request.getOutputName());

    List<String> command = new ArrayList<>();
    String executable = helmfileConfigurationProperties.getExecutablePath();

    command.add(executable);
    command.add("template");
    command.add("--file");
    command.add(helmfileFilePath.toString());

    command.add("--helm-binary");
    command.add(getHelmExecutableForRequest(null));

    String environment = request.getEnvironment();
    if (environment != null && !environment.isEmpty()) {
      command.add("--environment");
      command.add(environment);
    }

    String namespace = request.getNamespace();
    if (namespace != null && !namespace.isEmpty()) {
      command.add("--namespace");
      command.add(namespace);
    }

    if (request.isIncludeCRDs()) {
      command.add("--include-crds");
    }

    Map<String, Object> overrides = request.getOverrides();
    if (!overrides.isEmpty()) {
      List<String> overrideList = buildOverrideList(overrides);
      command.add("--set");
      command.add(String.join(",", overrideList));
    }

    if (!valuePaths.isEmpty()) {
      command.add("--values");
      command.add(valuePaths.stream().map(Path::toString).collect(Collectors.joining(",")));
    }

    result.setCommand(command);

    return result;
  }
}
