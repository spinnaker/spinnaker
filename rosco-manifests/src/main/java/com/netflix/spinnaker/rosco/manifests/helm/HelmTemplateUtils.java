package com.netflix.spinnaker.rosco.manifests.helm;

import com.netflix.spinnaker.rosco.jobs.BakeRecipe;
import com.netflix.spinnaker.rosco.manifests.BakeManifestRequest;
import com.netflix.spinnaker.rosco.manifests.TemplateUtils;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

@Component
public class HelmTemplateUtils extends TemplateUtils {
  @Override
  public BakeRecipe buildBakeRecipe(BakeManifestRequest request) {
    BakeRecipe result = super.buildBakeRecipe(request);
    Path templatePath;
    try {
      templatePath = downloadArtifactToTmpFile(request.getInputArtifact());
    } catch (IOException e) {
      throw new IllegalStateException("Failed to fetch helm template: " + e.getMessage(), e);
    }

    List<String> command = new ArrayList<>();
    command.add("helm");
    command.add("template");
    command.add(templatePath.toString());
    command.add("--name");
    command.add(request.getOutputName());

    result.setCommand(command);

    return result;
  }
}
