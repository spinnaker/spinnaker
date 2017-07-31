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

package com.netflix.spinnaker.orca.pipeline.util

import java.util.regex.Pattern
import com.fasterxml.jackson.databind.ObjectMapper
import com.google.common.annotations.VisibleForTesting
import com.netflix.spinnaker.orca.pipeline.model.Pipeline
import com.netflix.spinnaker.orca.pipeline.model.Stage
import groovy.transform.CompileDynamic
import groovy.util.logging.Slf4j

@Slf4j
class PackageInfo {
  ObjectMapper mapper
  Stage stage
  String versionDelimiter
  String packageType
  boolean extractBuildDetails
  boolean extractVersion
  BuildDetailExtractor buildDetailExtractor
  Pattern packageFilePattern

  PackageInfo(Stage stage, String packageType, String versionDelimiter, boolean extractBuildDetails, boolean extractVersion, ObjectMapper mapper) {
    this.stage = stage
    this.packageType = packageType
    this.versionDelimiter = versionDelimiter
    this.extractBuildDetails = extractBuildDetails
    this.extractVersion = extractVersion
    this.mapper = mapper
    this.buildDetailExtractor = new BuildDetailExtractor()

    packageFilePattern = Pattern.compile("${stage.context.package}.*\\.${packageType}")
  }

  @VisibleForTesting
  private boolean isUrl(String potentialUrl) {
    potentialUrl ==~ /\b(https?|ssh):\/\/.*/
  }

  public Map findTargetPackage(boolean allowMissingPackageInstallation) {
    Map requestMap = [:]
    // copy the context since we may modify it in createAugmentedRequest
    requestMap.putAll(stage.execution.context)
    requestMap.putAll(stage.context)

    if (stage.execution instanceof Pipeline) {
      Map trigger = ((Pipeline) stage.execution).trigger
      Map buildInfo = null
      if (requestMap.buildInfo) { // package was built as part of the pipeline
        buildInfo = mapper.convertValue(requestMap.buildInfo, HashMap)
      }

      if (!buildInfo?.artifacts) {
        buildInfo = findBuildInfoInUpstreamStage(stage, packageFilePattern) ?: buildInfo
      }

      return createAugmentedRequest(trigger, buildInfo, requestMap, allowMissingPackageInstallation)
    }
    return requestMap
  }

  /**
   * Try to find a package from the pipeline trigger and/or a step in the pipeline.
   * Optionally put the build details into the request object.  This does not alter the stage context,
   * so assign it back if that's the desired behavior.
   * @param trigger
   * @param buildInfo
   * @param request
   * @return
   */
  @CompileDynamic
  @VisibleForTesting
  private Map createAugmentedRequest(Map trigger, Map buildInfo, Map request, boolean allowMissingPackageInstallation) {
    Map artifactSourceBuildInfo = getArtifactSourceBuildInfo(trigger)
    List<Map> triggerArtifacts = artifactSourceBuildInfo?.artifacts
    List<Map> buildArtifacts = buildInfo?.artifacts

    if (isUrl(request.package) || request.package?.isEmpty() || !request.package) {
      return request
    }

    if (!buildInfo || (buildInfo && !buildArtifacts)) {
      if (!triggerArtifacts && (trigger.buildInfo != null || trigger.parentExecution?.trigger?.buildInfo != null)) {
        throw new IllegalStateException("Jenkins job detected but no artifacts found, please archive the packages in your job and try again.")
      }
    }

    if (!buildArtifacts && !triggerArtifacts) {
      return request
    }

    List missingPrefixes = []
    String fileExtension = ".${packageType}"

    // There might not be a request.package so we look for the package name from either the buildInfo or trigger
    //
    String reqPkg = request.package ?:
                    buildArtifacts?.first()?.fileName?.split(versionDelimiter)?.first() ?:
                      triggerArtifacts?.first()?.fileName?.split(versionDelimiter)?.first()

    List<String> requestPackages = reqPkg.split(" ")

    requestPackages.eachWithIndex { requestPackage, index ->

      String prefix = "${requestPackage}${versionDelimiter}"

      Map triggerArtifact = filterArtifacts(triggerArtifacts, prefix, fileExtension)
      Map buildArtifact = filterArtifacts(buildArtifacts, prefix, fileExtension)

      // only one unique package per pipeline is allowed
      if (triggerArtifact && buildArtifact && triggerArtifact.fileName != buildArtifact.fileName) {
        throw new IllegalStateException("Found build artifact in Jenkins stage and Pipeline Trigger")
      }

      String packageName
      String packageVersion

      if (triggerArtifact) {
        packageName = extractPackageName(triggerArtifact, fileExtension)
        if (extractVersion) {
          packageVersion = extractPackageVersion(triggerArtifact, prefix, fileExtension)
        }
      }

      if (buildArtifact) {
        packageName = extractPackageName(buildArtifact, fileExtension)
        if (extractVersion) {
          packageVersion = extractPackageVersion(buildArtifact, prefix, fileExtension)
        }
      }

      if (packageVersion) {
        request.put('packageVersion', packageVersion)
      }

      if (!triggerArtifact && !buildArtifact) {
        missingPrefixes.add(prefix)
      }

      // When a package match one of the packages coming from the trigger or from the previous stage its name
      // get replaced with the actual package name. Otherwise its just passed down to the bakery,
      // letting the bakery to resolve it.
      requestPackages[index] = packageName ?: requestPackage

      if (packageName) {

        if (extractBuildDetails) {
          def buildInfoForDetails = buildArtifact ? buildInfo : artifactSourceBuildInfo
          buildDetailExtractor.tryToExtractBuildDetails(buildInfoForDetails, request)
        }
      }
    }

    // If it hasn't been possible to match a package and allowMissingPackageInstallation is false raise an exception.
    if (missingPrefixes && !allowMissingPackageInstallation) {
      throw new IllegalStateException("Unable to find deployable artifact starting with ${missingPrefixes} and ending with ${fileExtension} in ${buildArtifacts} and ${triggerArtifacts}. Make sure your deb package file name complies with the naming convention: name_version-release_arch.")
    }

    request.put('package', requestPackages.join(" "))
    return request
  }

  @CompileDynamic
  Map getArtifactSourceBuildInfo(Map trigger){
    if (trigger?.buildInfo?.artifacts) {
      return trigger.buildInfo
    }
    if (trigger?.parentExecution?.trigger) {
      return getArtifactSourceBuildInfo(trigger.parentExecution.trigger)
    }
    return null
  }

  @CompileDynamic
  private String extractPackageName(Map artifact, String fileExtension) {
    artifact.fileName.substring(0, artifact.fileName.lastIndexOf(fileExtension))
  }

  @CompileDynamic
  private String extractPackageVersion(Map artifact, String filePrefix, String fileExtension) {
    String version = artifact.fileName.substring(artifact.fileName.indexOf(filePrefix) + filePrefix.length(), artifact.fileName.lastIndexOf(fileExtension))
    if (version.contains(versionDelimiter)) { // further strip in case of _all is in the file name
      version = version.substring(0, version.indexOf(versionDelimiter))
    }
    return version
  }

  @CompileDynamic
  private Map filterArtifacts(List<Map> artifacts, String prefix, String fileExtension) {
    if (packageType == 'rpm') {
      filterRPMArtifacts(artifacts, prefix)
    } else {
      artifacts.find {
        it.fileName?.startsWith(prefix) && it.fileName?.endsWith(fileExtension)
      }
    }
  }

  @CompileDynamic
  private Map filterRPMArtifacts(List<Map> artifacts, String prefix) {
    artifacts.find { artifact ->
      List<String> parts = artifact.fileName?.tokenize(versionDelimiter)
      if (parts.size >= 3) {
        parts.pop()
        parts.pop()
        String appName = parts.join(versionDelimiter)
        return "${appName}${versionDelimiter}" == prefix
      }
    }
  }

  private static Map findBuildInfoInUpstreamStage(Stage currentStage, Pattern packageFilePattern) {
    def upstreamStage = currentStage.ancestors().find {
      artifactMatch(it.context.buildInfo?.artifacts as List<Map<String, String>>, packageFilePattern)
    }
    return upstreamStage ? upstreamStage.context.buildInfo as Map : null
  }

  private static boolean artifactMatch(List<Map<String, String>> artifacts, Pattern pattern) {
    artifacts?.find {
      Map artifact -> pattern.matcher(artifact.get('fileName') as String ?: "").matches()
    }
  }
}
