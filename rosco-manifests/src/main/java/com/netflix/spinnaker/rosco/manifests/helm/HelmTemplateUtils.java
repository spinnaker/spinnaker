package com.netflix.spinnaker.rosco.manifests.helm;

import com.netflix.spinnaker.rosco.jobs.BakeRecipe;
import com.netflix.spinnaker.rosco.manifests.BakeManifestRequest;
import com.netflix.spinnaker.rosco.manifests.TemplateUtils;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
public class HelmTemplateUtils extends TemplateUtils {
  @Override
  public BakeRecipe buildBakeRecipe(BakeManifestEnvironment env, BakeManifestRequest request) {
    BakeRecipe result = super.buildBakeRecipe(env, request);
    Path templatePath;
    try {
      templatePath = downloadArtifactToTmpFile(env, request.getInputArtifact());
    } catch (IOException e) {
      throw new IllegalStateException("Failed to fetch helm template: " + e.getMessage(), e);
    }

    List<String> command = new ArrayList<>();
    command.add("helm");
    command.add("template");
    command.add(templatePath.toString());
    command.add("--name");
    command.add(request.getOutputName());

    Map<String, Object> overrides = request.getOverrides();
    if (overrides != null) {
      for (Map.Entry<String, Object> entry : overrides.entrySet()) {
        command.add("--set");
        command.add("'" + entry.getKey() + "=" + entry.getValue().toString() + "'");
      }
    }

    result.setCommand(command);

    return result;
  }
}
