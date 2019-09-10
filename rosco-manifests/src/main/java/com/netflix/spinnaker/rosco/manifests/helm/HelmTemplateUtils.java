package com.netflix.spinnaker.rosco.manifests.helm;

import com.netflix.spinnaker.kork.artifacts.model.Artifact;
import com.netflix.spinnaker.kork.web.exceptions.InvalidRequestException;
import com.netflix.spinnaker.rosco.jobs.BakeRecipe;
import com.netflix.spinnaker.rosco.manifests.BakeManifestEnvironment;
import com.netflix.spinnaker.rosco.manifests.TemplateUtils;
import com.netflix.spinnaker.rosco.services.ClouddriverService;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import javax.xml.bind.DatatypeConverter;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.util.ArrayUtils;

@Component
public class HelmTemplateUtils extends TemplateUtils {

  private static final String MANIFEST_SEPARATOR = "---\n";
  private static final String REGEX_TESTS_MANIFESTS = "# Source: .*/templates/tests/.*";

  public HelmTemplateUtils(ClouddriverService clouddriverService) {
    super(clouddriverService);
  }

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
      command.add("--set");
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

  public byte[] removeTestsDirectoryTemplates(byte[] input) {

    final String inputString = new String(input);
    final List<String> inputManifests =
        ArrayUtils.toUnmodifiableList(inputString.split(MANIFEST_SEPARATOR));

    final List<String> outputManifests =
        inputManifests.stream()
            .filter(
                manifest ->
                    !manifest.trim().isEmpty()
                        && !Pattern.compile(REGEX_TESTS_MANIFESTS).matcher(manifest).find())
            .collect(Collectors.toList());

    final String manifestBody =
        MANIFEST_SEPARATOR
            + outputManifests.stream().collect(Collectors.joining(MANIFEST_SEPARATOR));
    return manifestBody.getBytes();
  }

  private String nameFromReference(String reference) {
    try {
      MessageDigest md = MessageDigest.getInstance("MD5");
      return DatatypeConverter.printHexBinary(md.digest(reference.getBytes()));
    } catch (NoSuchAlgorithmException e) {
      throw new RuntimeException("Failed to save bake manifest: " + e.getMessage(), e);
    }
  }

  protected Path downloadArtifactToTmpFile(BakeManifestEnvironment env, Artifact artifact)
      throws IOException {
    if (artifact.getReference() == null) {
      throw new InvalidRequestException("Input artifact has an empty 'reference' field.");
    }
    File targetFile =
        env.getStagingPath().resolve(nameFromReference(artifact.getReference())).toFile();
    downloadArtifact(artifact, targetFile);
    return targetFile.toPath();
  }
}
