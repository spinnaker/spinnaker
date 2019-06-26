package com.netflix.spinnaker.rosco.manifests.helm;

import com.netflix.spinnaker.kork.artifacts.model.Artifact;
import com.netflix.spinnaker.rosco.jobs.BakeRecipe;
import com.netflix.spinnaker.rosco.manifests.TemplateUtils;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@Component
public class HelmTemplateUtils extends TemplateUtils {
  public BakeRecipe buildBakeRecipe(BakeManifestEnvironment env, HelmBakeManifestRequest request) {
    BakeRecipe result = new BakeRecipe();
    result.setName(request.getOutputName());

    Path templatePath;
    List<Path> valuePaths = new ArrayList<>();
    List<Artifact> inputArtifacts = request.getInputArtifacts();
    if (inputArtifacts == null || inputArtifacts.isEmpty()) {
      throw new IllegalArgumentException("At least one input artifact must be provided to bake");
    }

    try {
      templatePath = downloadArtifactToTmpFile(env, inputArtifacts.get(0));

      // not a stream to keep exception handling cleaner
      for (Artifact valueArtifact : inputArtifacts.subList(1, inputArtifacts.size())) {
        valuePaths.add(downloadArtifactToTmpFile(env, valueArtifact));
      }
    } catch (IOException e) {
      throw new IllegalStateException("Failed to fetch helm template: " + e.getMessage(), e);
    }

    List<String> command = new ArrayList<>();
    command.add("helm");
    command.add("template");
    command.add(templatePath.toString());
    command.add("--name");
    command.add(request.getOutputName());

    String namespace = request.getNamespace();
    if (namespace != null && !namespace.isEmpty()) {
      command.add("--namespace");
      command.add(namespace);
    }

    Map<String, Object> overrides = request.getOverrides();
    if (!overrides.isEmpty()) {
      List<String> overrideList = new ArrayList<>();
      for (Map.Entry<String, Object> entry : overrides.entrySet()) {
        overrideList.add(entry.getKey() + "=" + entry.getValue().toString());
      }
      command.add("--set-string");
      command.add(overrideList.stream().collect(Collectors.joining(",")));
    }

    if (!valuePaths.isEmpty()) {
      command.add("--values");
      command.add(
          String.join(",", valuePaths.stream().map(Path::toString).collect(Collectors.toList())));
    }

    result.setCommand(command);

    return result;
  }
}
