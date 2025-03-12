package com.netflix.spinnaker.rosco.manifests.helm;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
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
import java.io.UncheckedIOException;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
@Slf4j
@ConfigurationPropertiesScan("com.netflix.spinnaker.kork.artifacts.artifactstore")
public class HelmTemplateUtils extends HelmBakeTemplateUtils<HelmBakeManifestRequest> {
  // Threshold for Helm 3, where absolute values >= 1000000 cause scientific notation in overrides.
  private static final long HELM3_MAX_ABSOLUTE_NUMERIC_VALUE_THRESHOLD = 1_000_000L;
  private static final String OVERRIDES_FILE_PREFIX = "overrides_";
  private static final String YML_FILE_EXTENSION = ".yml";
  private final RoscoHelmConfigurationProperties helmConfigurationProperties;
  /**
   * Dedicated ObjectMapper for YAML processing. This custom ObjectMapper ensures specialized
   * handling of YAML format, allowing distinct settings from the default JSON ObjectMapper.
   */
  private final ObjectMapper yamlObjectMapper = new ObjectMapper(new YAMLFactory());

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
    return buildCommand(request, getValuePaths(inputArtifacts, env), templatePath, env);
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
      HelmBakeManifestRequest request,
      List<Path> valuePaths,
      Path templatePath,
      BakeManifestEnvironment env) {
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
    Path overridesFile = null;
    if (overrides != null && !overrides.isEmpty()) {
      String overridesString = getOverridesAsString(overrides);
      if (overridesString.length() < helmConfigurationProperties.getOverridesFileThreshold()
          || helmConfigurationProperties.getOverridesFileThreshold() == 0) {

        String overrideOption = request.isRawOverrides() ? "--set" : "--set-string";
        command.add(overrideOption);
        command.add(overridesString);
      } else if (request.isRawOverrides()) {

        // Helm3 treats large numbers as `int64` when passed via `--set` and as `float64` when
        // passed via `--values`, causing different template outputs.
        // Specifically, numbers >= 1,000,000 or <= -1,000,000 appear in non-scientific
        // notation with `--set` and in scientific notation with `--values`.
        // To ensure consistent Helm3 template behavior, the overrides YAML file
        // feature should be skipped for values >= or <= this threshold.
        // In contrast, Helm2 renders large numbers in scientific notation regardless of whether
        // they are passed via `--set` or `--values`.
        Map<String, Object> overridesForYaml = new HashMap<>(request.getOverrides());
        Map<String, Object> largeOverrides =
            getLargeAbsoluteNumericValuedEntries(
                request.getOverrides(), HELM3_MAX_ABSOLUTE_NUMERIC_VALUE_THRESHOLD);
        overridesForYaml.keySet().removeAll(largeOverrides.keySet());
        // process overrides with larger numeric values as commandline argument
        if (!largeOverrides.isEmpty()) {
          command.add("--set");
          command.add(getOverridesAsString(largeOverrides));
        }
        // covert the remaining overrides to yaml
        if (!overridesForYaml.isEmpty()) {
          overridesFile =
              writeOverridesToFile(
                  env, getOverridesAsString(overridesForYaml), request.isRawOverrides());
        }
      } else {
        overridesFile = writeOverridesToFile(env, overridesString, request.isRawOverrides());
      }
    }

    if (!valuePaths.isEmpty()) {
      command.add("--values");
      command.add(valuePaths.stream().map(Path::toString).collect(Collectors.joining(",")));
    }
    // For shorter overrides, --set/--set-string are used.
    // Since --set/--set-string have higher precedence over --values in Helm versions 2.16.1 and
    // 3.4.1, and later --values arguments override earlier ones,
    // it's important to add override values at the end of the command. This ensures their
    // precedence, especially when multiple --values are used.
    if (overridesFile != null) {
      command.add("--values");
      command.add(overridesFile.toString());
    }

    result.setCommand(command);

    return result;
  }

  /**
   * Filters and returns entries from the given map where numeric absolute values meet or exceed the
   * specified threshold.
   *
   * @param overrides : A map containing key-value pairs of Helm chart overrides.
   * @param numericAbsoluteValueThreshold : the minimum absolute value to consider as large.
   * @return Map<String, Object>: having entries with large absolute numeric values.
   */
  public static Map<String, Object> getLargeAbsoluteNumericValuedEntries(
      Map<String, Object> overrides, long numericAbsoluteValueThreshold) {
    Map<String, Object> largeMap = new HashMap<>();

    for (Map.Entry<String, Object> entry : overrides.entrySet()) {
      Object value = entry.getValue();
      long numericValue = 0;

      if (value instanceof Number) {
        numericValue = ((Number) value).longValue();
      } else if (value instanceof String) {
        try {
          numericValue = Long.parseLong((String) value);
        } catch (NumberFormatException e) {
          // Value is not a number, so we skip this entry
          continue;
        }
      }

      if (Math.abs(numericValue) >= numericAbsoluteValueThreshold) {
        largeMap.put(entry.getKey(), value);
      }
    }

    return largeMap;
  }
  /**
   * Constructs a comma-separated string representation of the given overrides map in the format
   * "key1=value1,key2=value2,...".
   *
   * @param overrides A map containing key-value pairs of Helm chart overrides.
   * @return A string representation of the overrides suitable for Helm command usage.
   */
  private String getOverridesAsString(Map<String, Object> overrides) {
    List<String> overrideList = buildOverrideList(overrides);
    return String.join(",", overrideList);
  }
  /**
   * Writes Helm chart overrides to a YAML file. This method takes a map of Helm chart overrides,
   * creates a YAML file with a unique filename (generated using a prefix, a random UUID, and a file
   * extension), and resolves the file path within the specified environment.
   *
   * @param env The environment providing context for file resolution.
   * @param overridesString helm --set argument string
   * @param rawOverrides If true, preserves the original data types in the overrides map. If false,
   *     converts all values to strings before writing to the YAML file.
   * @return The path to the created YAML file containing the Helm chart overrides.
   * @throws IllegalStateException If an error occurs during the file writing process.
   */
  private Path writeOverridesToFile(
      BakeManifestEnvironment env, String overridesString, boolean rawOverrides) {
    String fileName = OVERRIDES_FILE_PREFIX + UUID.randomUUID().toString() + YML_FILE_EXTENSION;
    Path filePath = env.resolvePath(fileName);
    Map<String, Object> helmParsedMap = null;
    HelmSetArgumentParser helmSetArgumentParser =
        new HelmSetArgumentParser(overridesString, !rawOverrides);
    try {
      helmParsedMap = helmSetArgumentParser.parse();
    } catch (IOException e) {
      throw new UncheckedIOException("error while parsing overrides string to yaml", e);
    }
    try (Writer writer = Files.newBufferedWriter(filePath)) {
      yamlObjectMapper.writeValue(writer, helmParsedMap);
      if (log.isDebugEnabled())
        log.debug(
            "Created overrides file at {} with the following contents:{}{}",
            filePath.toString(),
            System.lineSeparator(),
            new String(Files.readAllBytes(filePath)));
    } catch (IOException ioException) {
      throw new IllegalStateException(
          String.format("failed to write override yaml file %s.", filePath.toString())
              + ioException.getMessage(),
          ioException);
    }
    return filePath;
  }
}
