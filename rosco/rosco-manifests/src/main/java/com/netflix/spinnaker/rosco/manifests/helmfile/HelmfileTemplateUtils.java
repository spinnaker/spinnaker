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
import com.netflix.spinnaker.kork.exceptions.SpinnakerException;
import com.netflix.spinnaker.kork.retrofit.exceptions.SpinnakerHttpException;
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
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class HelmfileTemplateUtils extends HelmBakeTemplateUtils<HelmfileBakeManifestRequest> {

  // Environment and namespace are passed directly as arguments to the helmfile executable (see
  // buildCommand below), so they're restricted to characters that are valid in a Kubernetes
  // namespace / helmfile environment name.
  //
  // Note this is NOT a shell-injection fix: BakeRecipe.command is executed by JobExecutorLocal as
  // an argv array (commons-exec CommandLine with quote-parsing disabled, ultimately
  // ProcessBuilder-style execve), never via "sh -c" or any other shell, so metacharacters such as
  // ";", "|", or "$()" in these values are inert - they can't break out to run another command.
  // This pattern instead guards against argument injection (e.g. a value beginning with "-" being
  // misread by helmfile's flag parser as a new flag rather than the value of --environment /
  // --namespace) and acts as defense-in-depth in case this code path is ever refactored to invoke
  // a shell.
  private static final Pattern SAFE_ARGUMENT_PATTERN =
      Pattern.compile("^[a-zA-Z0-9]([a-zA-Z0-9._-]*[a-zA-Z0-9])?$");

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
    return buildCommand(
        request,
        getValuePaths(inputArtifacts, env),
        getStateValuePaths(request, env),
        helmfileFilePath);
  }

  private List<Path> getStateValuePaths(
      HelmfileBakeManifestRequest request, BakeManifestEnvironment env) {
    List<Artifact> stateValuesArtifacts = request.getStateValuesArtifacts();
    if (stateValuesArtifacts == null || stateValuesArtifacts.isEmpty()) {
      return new ArrayList<>();
    }

    List<Path> stateValuePaths = new ArrayList<>();
    try {
      for (Artifact stateValuesArtifact : stateValuesArtifacts) {
        stateValuePaths.add(downloadArtifactToTmpFile(env, stateValuesArtifact));
      }
    } catch (SpinnakerHttpException e) {
      throw new SpinnakerHttpException(fetchFailureMessage("state values file", e), e);
    } catch (IOException | SpinnakerException e) {
      throw new IllegalStateException(fetchFailureMessage("state values file", e), e);
    }

    return stateValuePaths;
  }

  public String fetchFailureMessage(String description, Exception e) {
    return "Failed to fetch helmfile " + description + ": " + e.getMessage();
  }

  public String getHelmExecutableForRequest(HelmfileBakeManifestRequest request) {
    return helmConfigurationProperties.getV3ExecutablePath();
  }

  public BakeRecipe buildCommand(
      HelmfileBakeManifestRequest request,
      List<Path> valuePaths,
      List<Path> stateValuePaths,
      Path helmfileFilePath) {
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

    // --environment is only added when a value is actually supplied (null/empty checks above). If
    // omitted, helmfile applies its own built-in default environment named "default" - Spinnaker
    // never passes that value explicitly.
    String environment = request.getEnvironment();
    if (environment != null && !environment.isEmpty()) {
      command.add("--environment");
      command.add(validateArgument("environment", environment));
    }

    // --namespace is likewise only added when a value is actually supplied; if omitted, no
    // --namespace argument is passed to helmfile at all.
    String namespace = request.getNamespace();
    if (namespace != null && !namespace.isEmpty()) {
      command.add("--namespace");
      command.add(validateArgument("namespace", namespace));
    }

    if (request.isIncludeCRDs()) {
      command.add("--include-crds");
    }

    if (stateValuePaths != null && !stateValuePaths.isEmpty()) {
      stateValuePaths.forEach(
          path -> {
            command.add("--state-values-file");
            command.add(path.toString());
          });
    }

    Map<String, Object> overrides = request.getOverrides();
    if (overrides != null && !overrides.isEmpty()) {
      List<String> overrideList = buildOverrideList(overrides);
      command.add("--set");
      command.add(String.join(",", overrideList));
    }

    if (!valuePaths.isEmpty()) {
      valuePaths.forEach(
          path -> {
            command.add("--values");
            command.add(path.toString());
          });
    }

    result.setCommand(command);

    return result;
  }

  private static String validateArgument(String fieldName, String value) {
    if (!SAFE_ARGUMENT_PATTERN.matcher(value).matches()) {
      throw new IllegalArgumentException(
          "The bake request "
              + fieldName
              + " field contains invalid characters. Only letters, numbers, '.', '_' and '-' are allowed.");
    }
    return value;
  }
}
