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
import com.netflix.spinnaker.orca.pipeline.model.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import static com.netflix.spinnaker.orca.pipeline.model.Execution.ExecutionType.PIPELINE;
import static java.lang.String.format;
import static java.util.Collections.emptyList;
import static java.util.Collections.emptyMap;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toList;

public class PackageInfo {

  private final Logger log = LoggerFactory.getLogger(getClass());

  private final ObjectMapper mapper;
  private final Stage stage;
  private final String versionDelimiter;
  private final String packageType;
  private final boolean extractBuildDetails;
  private final boolean extractVersion;
  private final BuildDetailExtractor buildDetailExtractor;
  private final Pattern packageFilePattern;

  public PackageInfo(Stage stage, String packageType, String versionDelimiter, boolean extractBuildDetails, boolean extractVersion, ObjectMapper mapper) {
    this.stage = stage;
    this.packageType = packageType;
    this.versionDelimiter = versionDelimiter;
    this.extractBuildDetails = extractBuildDetails;
    this.extractVersion = extractVersion;
    this.mapper = mapper;
    this.buildDetailExtractor = new BuildDetailExtractor();

    packageFilePattern = Pattern.compile(format("%s.*\\.%s", stage.getContext().get("package"), packageType));
  }

  @VisibleForTesting
  private boolean isUrl(String potentialUrl) {
    return potentialUrl.matches("\\b(https?|ssh):\\/\\/.*");
  }

  public Map<String, Object> findTargetPackage(boolean allowMissingPackageInstallation) {
    Map<String, Object> requestMap = new HashMap<>();
    // copy the context since we may modify it in createAugmentedRequest
    requestMap.putAll(stage.getContext());

    if (stage.getExecution().getType() == PIPELINE) {
      Map trigger = mapper.convertValue(stage.getExecution().getTrigger(), Map.class);
      Map buildInfo = null;
      if (requestMap.get("buildInfo") != null) { // package was built as part of the pipeline
        buildInfo = mapper.convertValue(requestMap.get("buildInfo"), Map.class);
      }

      if (buildInfo == null || (buildInfo.get("artifacts") != null && !((Collection) buildInfo.get("artifacts")).isEmpty())) {
        Map<String, Object> upstreamBuildInfo = findBuildInfoInUpstreamStage(stage, packageFilePattern);
        if (!upstreamBuildInfo.isEmpty()) {
          buildInfo = upstreamBuildInfo;
        }
      }

      if (buildInfo == null) {
        buildInfo = emptyMap();
      }

      return createAugmentedRequest(trigger, buildInfo, requestMap, allowMissingPackageInstallation);
    }
    return requestMap;
  }

  /**
   * Try to find a package from the pipeline trigger and/or a step in the pipeline.
   * Optionally put the build details into the request object.  This does not alter the stage context,
   * so assign it back if that's the desired behavior.
   *
   * @param trigger
   * @param buildInfo
   * @param request
   * @return
   */
  @VisibleForTesting
  private Map<String, Object> createAugmentedRequest(Map<String, Object> trigger, Map<String, Object> buildInfo, Map<String, Object> request, boolean allowMissingPackageInstallation) {
    Map<String, Object> artifactSourceBuildInfo = getArtifactSourceBuildInfo(trigger);
    List<Map<String, Object>> triggerArtifacts = Optional.ofNullable((List<Map<String, Object>>) artifactSourceBuildInfo.get("artifacts")).orElse(emptyList());
    List<Map<String, Object>> buildArtifacts = Optional.ofNullable((List<Map<String, Object>>) buildInfo.get("artifacts")).orElse(emptyList());

    if (request.get("package") == null || request.get("package").equals("") || isUrl(request.get("package").toString())) {
      return request;
    }

    if (buildInfo.isEmpty() || buildArtifacts.isEmpty()) {
      Optional<Map<String, Object>> parentBuildInfo = Optional
        .ofNullable((Map) trigger.get("parentExecution"))
        .map(it -> (Map) it.get("trigger"))
        .map(it -> (Map<String, Object>) it.get("buildInfo"));
      if (triggerArtifacts.isEmpty() && (trigger.get("buildInfo") != null || parentBuildInfo.isPresent())) {
        throw new IllegalStateException("Jenkins job detected but no artifacts found, please archive the packages in your job and try again.");
      }
    }

    if (buildArtifacts.isEmpty() && triggerArtifacts.isEmpty()) {
      return request;
    }

    List<String> missingPrefixes = new ArrayList<>();
    String fileExtension = format(".%s", packageType);

    // There might not be a request.package so we look for the package name from either the buildInfo or trigger
    //
    String reqPkg = Optional
      .ofNullable(request.get("package").toString())
      .orElseGet(() ->
        buildArtifacts
          .stream()
          .findFirst()
          .map(it -> it.get("fileName").toString().split(versionDelimiter)[0])
          .orElseGet(() -> triggerArtifacts.stream().findFirst().map(it -> it.get("fileName").toString().split(versionDelimiter)[0]).orElse(null))
      );

    List<String> requestPackages = Arrays.asList(reqPkg.split(" "));

    for (int index = 0; index < requestPackages.size(); index++) {
      String requestPackage = requestPackages.get(index);

      String prefix = requestPackage + versionDelimiter;

      Map<String, Object> triggerArtifact = filterArtifacts(triggerArtifacts, prefix, fileExtension);
      Map<String, Object> buildArtifact = filterArtifacts(buildArtifacts, prefix, fileExtension);

      // only one unique package per pipeline is allowed
      if (!triggerArtifact.isEmpty() && !buildArtifact.isEmpty() && !triggerArtifact.get("fileName").equals(buildArtifact.get("fileName"))) {
        throw new IllegalStateException("Found build artifact in Jenkins stage and Pipeline Trigger");
      }

      String packageName = null;
      String packageVersion = null;

      if (!triggerArtifact.isEmpty()) {
        packageName = extractPackageName(triggerArtifact, fileExtension);
        if (extractVersion) {
          packageVersion = extractPackageVersion(triggerArtifact, prefix, fileExtension);
        }
      }

      if (!buildArtifact.isEmpty()) {
        packageName = extractPackageName(buildArtifact, fileExtension);
        if (extractVersion) {
          packageVersion = extractPackageVersion(buildArtifact, prefix, fileExtension);
        }
      }

      if (packageVersion != null) {
        request.put("packageVersion", packageVersion);
      }

      if (triggerArtifact.isEmpty() && buildArtifact.isEmpty()) {
        missingPrefixes.add(prefix);
      }

      // When a package match one of the packages coming from the trigger or from the previous stage its name
      // get replaced with the actual package name. Otherwise its just passed down to the bakery,
      // letting the bakery to resolve it.
      requestPackages.set(index, packageName != null ? packageName : requestPackage);

      if (packageName != null) {

        if (extractBuildDetails) {
          Map<String, Object> buildInfoForDetails = !buildArtifact.isEmpty() ? buildInfo : artifactSourceBuildInfo;
          buildDetailExtractor.tryToExtractBuildDetails(buildInfoForDetails, request);
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

    request.put("package", requestPackages.stream().collect(joining(" ")));
    return request;
  }

  Map<String, Object> getArtifactSourceBuildInfo(Map<String, Object> trigger) {
    Map<String, Object> buildInfo = Optional.ofNullable((Map<String, Object>) trigger.get("buildInfo")).orElse(emptyMap());
    Map<String, Object> parentExecution = Optional.ofNullable((Map<String, Object>) trigger.get("parentExecution")).orElse(emptyMap());
    if (buildInfo.get("artifacts") != null) {
      return buildInfo;
    }
    if (parentExecution.get("trigger") != null) {
      return getArtifactSourceBuildInfo((Map<String, Object>) parentExecution.get("trigger"));
    }
    return emptyMap();
  }

  private String extractPackageName(Map artifact, String fileExtension) {
    String fileName = artifact.get("fileName").toString();
    return fileName.substring(0, fileName.lastIndexOf(fileExtension));
  }

  private String extractPackageVersion(Map<String, Object> artifact, String filePrefix, String fileExtension) {
    String fileName = artifact.get("fileName").toString();
    String version = fileName.substring(fileName.indexOf(filePrefix) + filePrefix.length(), fileName.lastIndexOf(fileExtension));
    if (version.contains(versionDelimiter)) {
      // further strip in case of _all is in the file name
      version = version.substring(0, version.indexOf(versionDelimiter));
    }
    return version;
  }

  private Map<String, Object> filterArtifacts(List<Map<String, Object>> artifacts, String prefix, String fileExtension) {
    if (packageType.equals("rpm")) {
      return filterRPMArtifacts(artifacts, prefix);
    } else {
      return artifacts.
        stream()
        .filter(it ->
          it.get("fileName") != null && it.get("fileName").toString().startsWith(prefix) && it.get("fileName").toString().endsWith(fileExtension)
        )
        .findFirst()
        .orElse(emptyMap());
    }
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

  private static Map<String, Object> findBuildInfoInUpstreamStage(Stage currentStage, Pattern packageFilePattern) {
    Stage upstreamStage = currentStage
      .ancestors()
      .stream()
      .filter(it -> {
        Map<String, Object> buildInfo = (Map<String, Object>) it.getOutputs().get("buildInfo");
        return buildInfo != null &&
          artifactMatch((List<Map<String, String>>) buildInfo.get("artifacts"), packageFilePattern);
      })
      .findFirst()
      .orElse(null);
    return upstreamStage != null ? (Map<String, Object>) upstreamStage.getOutputs().get("buildInfo") : emptyMap();
  }

  private static boolean artifactMatch(List<Map<String, String>> artifacts, Pattern pattern) {
    return artifacts != null &&
      artifacts.stream()
        .anyMatch((Map artifact) -> pattern.matcher(String.valueOf(artifact.get("fileName"))).matches());
  }
}
