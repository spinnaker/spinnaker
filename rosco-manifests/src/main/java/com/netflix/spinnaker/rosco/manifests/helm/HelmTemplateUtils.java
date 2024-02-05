package com.netflix.spinnaker.rosco.manifests.helm;

import com.netflix.spinnaker.kork.artifacts.artifactstore.ArtifactStore;
import com.netflix.spinnaker.kork.artifacts.artifactstore.ArtifactStoreConfigurationProperties;
import com.netflix.spinnaker.kork.artifacts.model.Artifact;
import com.netflix.spinnaker.rosco.jobs.BakeRecipe;
import com.netflix.spinnaker.rosco.manifests.ArtifactDownloader;
import com.netflix.spinnaker.rosco.manifests.BakeManifestEnvironment;
import com.netflix.spinnaker.rosco.manifests.BakeManifestRequest;
import com.netflix.spinnaker.rosco.manifests.HelmBakeTemplateUtils;
import com.netflix.spinnaker.rosco.manifests.config.RoscoHelmConfigurationProperties;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
@Slf4j
@ConfigurationPropertiesScan("com.netflix.spinnaker.kork.artifacts.artifactstore")
public class HelmTemplateUtils extends HelmBakeTemplateUtils<HelmBakeManifestRequest> {
  private final RoscoHelmConfigurationProperties helmConfigurationProperties;

  public HelmTemplateUtils(
      ArtifactDownloader artifactDownloader,
      Optional<ArtifactStore> artifactStore,
      ArtifactStoreConfigurationProperties artifactStoreProperties,
      RoscoHelmConfigurationProperties helmConfigurationProperties) {
    super(artifactDownloader, artifactStore, artifactStoreProperties.getHelm());
    this.helmConfigurationProperties = helmConfigurationProperties;
  }

  public BakeRecipe buildBakeRecipe(BakeManifestEnvironment env, HelmBakeManifestRequest request)
      throws IOException {
    Path templatePath;

    List<Artifact> inputArtifacts = request.getInputArtifacts();
    if (inputArtifacts == null || inputArtifacts.isEmpty()) {
      throw new IllegalArgumentException("At least one input artifact must be provided to bake");
    }

    templatePath = getHelmTypePathFromArtifact(env, inputArtifacts, request.getHelmChartFilePath());

    log.info("path to Chart.yaml: {}", templatePath);
    return buildCommand(request, getValuePaths(inputArtifacts, env), templatePath);
  }

  public String fetchFailureMessage(String description, Exception e) {
    return "Failed to fetch helm " + description + ": " + e.getMessage();
  }

  public String getHelmExecutableForRequest(HelmBakeManifestRequest request) {
    if (BakeManifestRequest.TemplateRenderer.HELM2.equals(request.getTemplateRenderer())) {
      return helmConfigurationProperties.getV2ExecutablePath();
    }
    return helmConfigurationProperties.getV3ExecutablePath();
  }

  public BakeRecipe buildCommand(
      HelmBakeManifestRequest request, List<Path> valuePaths, Path templatePath) {
    BakeRecipe result = new BakeRecipe();
    result.setName(request.getOutputName());

    List<String> command = new ArrayList<>();
    String executable = getHelmExecutableForRequest(request);

    // Helm `template` subcommands are slightly different
    // helm 2: helm template <chart> --name <release name>
    // helm 3: helm template <release name> <chart>
    // Other parameters such as --namespace, --set, and --values are the same
    command.add(executable);
    command.add("template");
    if (HelmBakeManifestRequest.TemplateRenderer.HELM2.equals(request.getTemplateRenderer())) {
      command.add(templatePath.toString());
      command.add("--name");
      command.add(request.getOutputName());
    } else {
      command.add(request.getOutputName());
      command.add(templatePath.toString());
    }

    String namespace = request.getNamespace();
    if (namespace != null && !namespace.isEmpty()) {
      command.add("--namespace");
      command.add(namespace);
    }

    if (request.isIncludeCRDs()
        && request.getTemplateRenderer() == BakeManifestRequest.TemplateRenderer.HELM3) {
      command.add("--include-crds");
    }

    String apiVersions = request.getApiVersions();
    if (StringUtils.hasText(apiVersions)) {
      command.add("--api-versions");
      command.add(apiVersions);
    }

    String kubeVersion = request.getKubeVersion();
    if (StringUtils.hasText(kubeVersion)) {
      command.add("--kube-version");
      command.add(kubeVersion);
    }

    Map<String, Object> overrides = request.getOverrides();
    if (overrides != null && !overrides.isEmpty()) {
      List<String> overrideList = buildOverrideList(overrides);
      String overrideOption = request.isRawOverrides() ? "--set" : "--set-string";
      command.add(overrideOption);
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
