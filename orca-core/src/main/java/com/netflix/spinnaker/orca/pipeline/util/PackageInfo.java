/*
 * Copyright 2015 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.orca.pipeline.util;

import java.util.*;
import java.util.regex.Pattern;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.annotations.VisibleForTesting;
import com.netflix.spinnaker.kork.artifacts.model.Artifact;
import com.netflix.spinnaker.orca.pipeline.model.Stage;
import static com.netflix.spinnaker.orca.pipeline.model.Execution.ExecutionType.PIPELINE;
import static java.lang.String.format;
import static java.util.Collections.emptyList;
import static java.util.Collections.emptyMap;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toList;

/**
 * This class inspects the context of a stage, preceding stages, the trigger, and possibly the parent pipeline
 * in order to see if an artifact matching the name(s) specified in the bake stage was produced.
 * If so, that version will be used in the bake request.
 * If nothing is found after all this searching it is up to the bakery to pull the latest package version.
 *
 * Artifact information comes from Jenkins on the pipeline trigger in the field `buildInfo.artifacts`.
 * If your trigger contains the Artifacts field, this class will also look for version information in there.
 */
public class PackageInfo {

  private final ObjectMapper mapper;
  private final Stage stage;
  private final List<Artifact> artifacts;
  private final String versionDelimiter;
  private final String packageType;
  private final boolean extractBuildDetails;
  private final boolean extractVersion;
  private final BuildDetailExtractor buildDetailExtractor;
  private final List<Pattern> packageFilePatterns = new ArrayList<>();

  public PackageInfo(Stage stage,
                     List<Artifact> artifacts,
                     String packageType,
                     String versionDelimiter,
                     boolean extractBuildDetails,
                     boolean extractVersion,
                     ObjectMapper mapper) {
    this.stage = stage;
    this.artifacts = artifacts;
    this.packageType = packageType;
    this.versionDelimiter = versionDelimiter;
    this.extractBuildDetails = extractBuildDetails;
    this.extractVersion = extractVersion;
    this.mapper = mapper;
    this.buildDetailExtractor = new BuildDetailExtractor();

    // can be a space separated set of packages
    if (stage.getContext().containsKey("package")) {
      String packages = stage.getContext().get("package").toString();
      for (String p : packages.split(" ")) {
        packageFilePatterns.add(
          Pattern.compile(format("%s.*\\.%s", p, packageType))
        );
      }
    }
  }

  @VisibleForTesting
  private boolean isUrl(String potentialUrl) {
    return potentialUrl.matches("\\b(https?|ssh):\\/\\/.*");
  }

  public Map<String, Object> findTargetPackage(boolean allowMissingPackageInstallation) {
    Map<String, Object> stageContext = new HashMap<>();
    // copy the context since we may modify it in createAugmentedRequest
    stageContext.putAll(stage.getContext());

    if (stage.getExecution().getType() == PIPELINE) {
      Map trigger = mapper.convertValue(stage.getExecution().getTrigger(), Map.class);
      Map buildInfoCurrentExecution = null;
      if (stageContext.get("buildInfo") != null) { // package was built as part of the pipeline
        buildInfoCurrentExecution = mapper.convertValue(stageContext.get("buildInfo"), Map.class);
      }

      if (buildInfoCurrentExecution == null || (buildInfoCurrentExecution.get("artifacts") != null && !((Collection) buildInfoCurrentExecution.get("artifacts")).isEmpty())) {
        Map<String, Object> upstreamBuildInfo = findBuildInfoInUpstreamStage(stage, packageFilePatterns);
        if (!upstreamBuildInfo.isEmpty()) {
          buildInfoCurrentExecution = upstreamBuildInfo;
        }
      }

      if (buildInfoCurrentExecution == null) {
        buildInfoCurrentExecution = emptyMap();
      }

      return createAugmentedRequest(trigger, buildInfoCurrentExecution, stageContext, allowMissingPackageInstallation);
    }

    // A package could only have been produced as part of a pipeline,
    // so if this is not a pipeline return the unchanged context.
    return stageContext;
  }

  /**
   * Try to find a package from the artifacts.
   * If not present, fall back to the pipeline trigger and/or a step in the pipeline.
   * Optionally put the build details into the request object.  This does not alter the stage context,
   * so assign it back if that's the desired behavior.
   *
   * @param trigger the trigger of the pipeline
   * @param buildInfoCurrentExecution the buildInfo block that comes from either the current execution, not the trigger
   * @param stageContext the stage context
   * @return
   */
  @VisibleForTesting
  private Map<String, Object> createAugmentedRequest(Map<String, Object> trigger,
                                                     Map<String, Object> buildInfoCurrentExecution,
                                                     Map<String, Object> stageContext,
                                                     boolean allowMissingPackageInstallation) {
    Map<String, Object> triggerBuildInfo = getBuildInfoFromTriggerOrParentTrigger(trigger);
    List<Map<String, Object>> triggerArtifacts = Optional.ofNullable((List<Map<String, Object>>) triggerBuildInfo.get("artifacts")).orElse(emptyList());
    List<Map<String, Object>> buildArtifacts = Optional.ofNullable((List<Map<String, Object>>) buildInfoCurrentExecution.get("artifacts")).orElse(emptyList());

    if (stageContext.get("package") == null || stageContext.get("package").equals("") || isUrl(stageContext.get("package").toString())) {
      return stageContext;
    }

    if (buildInfoCurrentExecution.isEmpty() || buildArtifacts.isEmpty()) {
      Optional<Map<String, Object>> parentBuildInfo = Optional
        .ofNullable((Map) trigger.get("parentExecution"))
        .map(it -> (Map) it.get("trigger"))
        .map(it -> (Map<String, Object>) it.get("buildInfo"));
      if (triggerArtifacts.isEmpty() && (trigger.get("buildInfo") != null || parentBuildInfo.isPresent()) && artifacts.isEmpty()) {
        throw new IllegalStateException("Jenkins job detected but no artifacts found, please archive the packages in your job and try again.");
      }
    }

    if (buildArtifacts.isEmpty() && triggerArtifacts.isEmpty() && artifacts.isEmpty()) {
      return stageContext;
    }

    List<String> missingPrefixes = new ArrayList<>();
    String fileExtension = format(".%s", packageType);

    String reqPkg = stageContext.get("package").toString();
    List<String> requestPackages = Arrays.asList(reqPkg.split(" "));

    for (int index = 0; index < requestPackages.size(); index++) {
      String requestPackage = requestPackages.get(index);

      String prefix = requestPackage + versionDelimiter;

      Artifact matchedArtifact = filterKorkArtifacts(artifacts, requestPackage, packageType);
      Map<String, Object> triggerArtifact = filterArtifacts(triggerArtifacts, prefix, fileExtension);
      Map<String, Object> buildArtifact = filterArtifacts(buildArtifacts, prefix, fileExtension);

      // only one unique package per pipeline is allowed
      if (!triggerArtifact.isEmpty() && !buildArtifact.isEmpty() && !triggerArtifact.get("fileName").equals(buildArtifact.get("fileName"))) {
        throw new IllegalStateException("Found build artifact in both Jenkins stage ("
          + buildArtifact.get("fileName")
          + ") and Pipeline Trigger ("
          + triggerArtifact.get("filename")
          + ")");
      }

      if (!triggerArtifact.isEmpty() && matchedArtifact != null && !extractPackageVersion(triggerArtifact, prefix, fileExtension).equals(matchedArtifact.getVersion())) {
        throw new IllegalStateException("Found build artifact in both Pipeline Trigger ("
          + triggerArtifact.get("filename")
          + ") and produced artifacts ("
          + matchedArtifact.getVersion() + versionDelimiter + matchedArtifact.getVersion()
          + ")");
      }

      if (!buildArtifact.isEmpty() && matchedArtifact != null && !extractPackageVersion(buildArtifact, prefix, fileExtension).equals(matchedArtifact.getVersion())) {
        throw new IllegalStateException("Found build artifact in both Jenkins stage ("
          + matchedArtifact.getVersion() + versionDelimiter + matchedArtifact.getVersion()
          + ") and produced artifacts ("
          + buildArtifact.get("fileName")
          + ")");
      }

      String packageIdentifier = null; //package-name + delimiter + version, like "test-package_1.0.0"
      String packageVersion = null;

      if (matchedArtifact != null) {
        packageIdentifier = matchedArtifact.getName() + versionDelimiter + matchedArtifact.getVersion();
        if (extractVersion) {
          packageVersion = matchedArtifact.getVersion();
        }
      } else if (!buildArtifact.isEmpty()) {
        packageIdentifier = extractPackageIdentifier(buildArtifact, fileExtension);
        if (extractVersion) {
          packageVersion = extractPackageVersion(buildArtifact, prefix, fileExtension);
        }
      } else if (!triggerArtifact.isEmpty()) {
        packageIdentifier = extractPackageIdentifier(triggerArtifact, fileExtension);
        if (extractVersion) {
          packageVersion = extractPackageVersion(triggerArtifact, prefix, fileExtension);
        }
      }

      if (packageVersion != null) {
        stageContext.put("packageVersion", packageVersion);
      }

      if (triggerArtifact.isEmpty() && buildArtifact.isEmpty() && matchedArtifact == null) {
        missingPrefixes.add(prefix);
      }

      // When a package matches one of the packages coming from the trigger or from the previous stage its name
      // get replaced with the actual package name. Otherwise its just passed down to the bakery,
      // letting the bakery to resolve it.
      requestPackages.set(index, packageIdentifier != null ? packageIdentifier : requestPackage);

      if (packageIdentifier != null) {
        if (extractBuildDetails) {
          Map<String, Object> buildInfoForDetails = !buildArtifact.isEmpty() ? buildInfoCurrentExecution : triggerBuildInfo;
          buildDetailExtractor.tryToExtractJenkinsBuildDetails(buildInfoForDetails, stageContext);
        }
      }
    }

    // If it hasn't been possible to match a package and allowMissingPackageInstallation is false raise an exception.
    if (!missingPrefixes.isEmpty() && !allowMissingPackageInstallation) {
      throw new IllegalStateException(format(
        "Unable to find deployable artifact starting with %s and ending with %s in %s and %s. Make sure your deb package file name complies with the naming convention: name_version-release_arch.",
        missingPrefixes,
        fileExtension,
        buildArtifacts,
        triggerArtifacts.stream().map(it -> it.get("fileName")).collect(toList())
      ));
    }

    stageContext.put("package", requestPackages.stream().collect(joining(" ")));
    return stageContext;
  }

  /**
   * @param trigger
   * @return the buildInfo block from the pipeline trigger if it exists,
   *         or the buildInfo block from the parent pipeline trigger if it exists,
   *         or an empty map.
   */
  Map<String, Object> getBuildInfoFromTriggerOrParentTrigger(Map<String, Object> trigger) {
    Map<String, Object> triggerBuildInfo = Optional.ofNullable((Map<String, Object>) trigger.get("buildInfo")).orElse(emptyMap());
    Map<String, Object> parentExecution = Optional.ofNullable((Map<String, Object>) trigger.get("parentExecution")).orElse(emptyMap());
    if (triggerBuildInfo.get("artifacts") != null) {
      return triggerBuildInfo;
    }
    if (parentExecution.get("trigger") != null) {
      return getBuildInfoFromTriggerOrParentTrigger((Map<String, Object>) parentExecution.get("trigger"));
    }
    return emptyMap();
  }

  /**
   * packageIdentifier is the package name plus version information.
   *  When filename = orca_1.1767.0-h1997.29115f6_all.deb
   *  then packageIdentifier = orca_1.1767.0-h1997.29115f6_all
   * @return the name of the package plus all version information, including architecture.
   */
  private String extractPackageIdentifier(Map artifact, String fileExtension) {
    String fileName = artifact.get("fileName").toString();
    return fileName.substring(0, fileName.lastIndexOf(fileExtension));
  }

  private String extractPackageVersion(Map<String, Object> artifact, String filePrefix, String fileExtension) {
    String fileName = artifact.get("fileName").toString();
    if (packageType.equals("rpm")) {
      return extractRpmVersion(fileName);
    }
    String version = fileName.substring(fileName.indexOf(filePrefix) + filePrefix.length(), fileName.lastIndexOf(fileExtension));
    if (version.contains(versionDelimiter)) {
      // further strip in case of _all is in the file name
      version = version.substring(0, version.indexOf(versionDelimiter));
    }
    return version;
  }

  private String extractRpmVersion(String fileName) {
    String[] parts = fileName.split(versionDelimiter);
    String suffix = parts[parts.length - 1].replaceAll(".rpm", "");
    return parts[parts.length - 2] + versionDelimiter + suffix;
  }

  private Map<String, Object> filterArtifacts(List<Map<String, Object>> artifacts, String prefix, String fileExtension) {
    if (packageType.equals("rpm")) {
      return filterRPMArtifacts(artifacts, prefix);
    } else {
      return artifacts
        .stream()
        .filter(it ->
          it.get("fileName") != null && it.get("fileName").toString().startsWith(prefix) && it.get("fileName").toString().endsWith(fileExtension)
        )
        .findFirst()
        .orElse(emptyMap());
    }
  }

  private Artifact filterKorkArtifacts(List<Artifact> artifacts, String requestPackage, String packageType) {
    return artifacts
      .stream()
      .filter( it ->
        it.getName() != null && it.getName().equals(requestPackage) && it.getType().equalsIgnoreCase(packageType)
      ).findFirst()
      .orElse(null);
  }

  private Map<String, Object> filterRPMArtifacts(List<Map<String, Object>> artifacts, String prefix) {
    return artifacts
      .stream()
      .filter(artifact -> {
        String[] parts = artifact.get("fileName").toString().split(versionDelimiter);
        if (parts.length >= 3) {
          parts = Arrays.copyOfRange(parts, 0, parts.length - 2);
          String appName = Arrays.stream(parts).collect(joining(versionDelimiter));
          return format("%s%s", appName, versionDelimiter).equals(prefix);
        }
        return false;
      })
      .findFirst()
      .orElse(emptyMap());
  }

  private static Map<String, Object> findBuildInfoInUpstreamStage(Stage currentStage,
                                                                  List<Pattern> packageFilePatterns) {
    Stage upstreamStage = currentStage
      .ancestors()
      .stream()
      .filter(it -> {
        Map<String, Object> buildInfo = (Map<String, Object>) it.getOutputs().get("buildInfo");
        return buildInfo != null &&
          artifactMatch((List<Map<String, String>>) buildInfo.get("artifacts"), packageFilePatterns);
      })
      .findFirst()
      .orElse(null);
    return upstreamStage != null ? (Map<String, Object>) upstreamStage.getOutputs().get("buildInfo") : emptyMap();
  }

  private static boolean artifactMatch(List<Map<String, String>> artifacts, List<Pattern> patterns) {
    return artifacts != null &&
      artifacts
        .stream()
        .anyMatch((Map artifact) -> patterns
          .stream()
          .anyMatch(p -> p.matcher(String.valueOf(artifact.get("fileName"))).matches())
        );
  }
}
