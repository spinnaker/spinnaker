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

import com.fasterxml.jackson.databind.ObjectMapper
import com.google.common.annotations.VisibleForTesting
import com.netflix.spinnaker.orca.pipeline.model.Pipeline
import com.netflix.spinnaker.orca.pipeline.model.Stage
import groovy.transform.CompileDynamic

class PackageInfo {
  ObjectMapper mapper
  Stage stage
  String versionDelimiter
  String packageType
  boolean extractBuildDetails
  boolean extractVersion

  PackageInfo(Stage stage, String packageType, String versionDelimiter, boolean extractBuildDetails, boolean extractVersion, ObjectMapper mapper) {
    this.stage = stage
    this.packageType = packageType
    this.versionDelimiter = versionDelimiter
    this.extractBuildDetails = extractBuildDetails
    this.extractVersion = extractVersion
    this.mapper = mapper
  }

  @VisibleForTesting
  private boolean isUrl(String potentialUrl) {
    potentialUrl ==~ /\b(https?|ssh):\/\/.*/
  }

  public Map findTargetPackage() {
    Map requestMap = [:]
    // copy the context since we may modify it in createAugmentedRequest
    requestMap.putAll(stage.execution.context)
    requestMap.putAll(stage.context)

    if (stage.execution instanceof Pipeline) {
      Map trigger = ((Pipeline) stage.execution).trigger
      Map buildInfo = null
      if (requestMap.buildInfo) { // package was built as part of the pipeline
        buildInfo = mapper.convertValue(requestMap.buildInfo, Map)
      }
      return createAugmentedRequest(trigger, buildInfo, requestMap)
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
  private Map createAugmentedRequest(Map trigger, Map buildInfo, Map request) {

    List<Map> triggerArtifacts = trigger?.buildInfo?.artifacts ?: trigger?.parentExecution?.trigger?.buildInfo?.artifacts
    List<Map> buildArtifacts = buildInfo?.artifacts

    if (isUrl(request.package)) {
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
          def buildInfoUrl = buildArtifact ? buildInfo?.url : trigger?.buildInfo?.url
          def buildInfoUrlParts = parseBuildInfoUrl(buildInfoUrl)

          if (buildInfoUrlParts?.size == 3) {
            request.put('buildHost', buildInfoUrlParts[0].toString())
            request.put('job', buildInfoUrlParts[1].toString())
            request.put('buildNumber', buildInfoUrlParts[2].toString())
          }

          def commitHash = buildArtifact ? extractCommitHash(buildInfo) : extractCommitHash(trigger?.buildInfo)

          if (commitHash) {
            request.put('commitHash', commitHash)
          }
        }
      }
    }

    // If it hasn't been possible to match a package and allowMissingPackageInstallation is false raise an exception.
    if (missingPrefixes && !request.allowMissingPackageInstallation) {
      throw new IllegalStateException("Unable to find deployable artifact starting with ${missingPrefixes} and ending with ${fileExtension} in ${buildArtifacts} and ${triggerArtifacts}. Make sure your deb package file name complies with the naming convention: name_version-release_arch.")
    }

    request.put('package', requestPackages.join(" "))
    return request
  }

  @CompileDynamic
  private String extractCommitHash(Map buildInfo) {
    // buildInfo.scm contains a list of maps. Each map contains these keys: name, sha1, branch.
    // If the list contains more than one entry, prefer the first one that is not master and is not develop.
    def commitHash

    if (buildInfo?.scm?.size() >= 2) {
      commitHash = buildInfo.scm.find {
        it.branch != "master" && it.branch != "develop"
      }?.sha1
    }

    if (!commitHash) {
      commitHash = buildInfo?.scm?.first()?.sha1
    }

    return commitHash
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
    artifacts.find {
      it.fileName?.startsWith(prefix) && it.fileName?.endsWith(fileExtension)
    }
  }

  @CompileDynamic
  // Naming-convention for buildInfo.url is $protocol://$buildHost/job/$job/$buildNumber/.
  // For example: http://spinnaker.builds.test.netflix.net/job/SPINNAKER-package-echo/69/
  // Note that job names can contain slashes if using the Folders plugin.
  // For example: http://spinnaker.builds.test.netflix.net/job/folder1/job/job1/69/
  def parseBuildInfoUrl(String url) {
    List<String> urlParts = url?.tokenize("/")

    if (urlParts?.size >= 5) {
      def buildNumber = urlParts.pop()
      def job = urlParts[3..-1].join('/')

      def buildHost = "${urlParts[0]}//${urlParts[1]}/"

      return [buildHost, job, buildNumber]
    }
  }
}
