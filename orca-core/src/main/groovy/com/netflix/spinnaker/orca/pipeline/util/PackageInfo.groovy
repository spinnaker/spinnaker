package com.netflix.spinnaker.orca.pipeline.util

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.orca.pipeline.model.Pipeline
import com.netflix.spinnaker.orca.pipeline.model.Stage
import groovy.transform.CompileDynamic
import org.springframework.beans.factory.annotation.Autowired

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

  public Map findTargetPackage() {
    Map requestMap = [:]
    // copy the context since we may modify it in createAugmentedRequest
    requestMap.putAll(stage.context)
    if (stage.execution instanceof Pipeline) {
      Map trigger = ((Pipeline) stage.execution).trigger
      Map buildInfo = [:]
      if (requestMap.buildInfo) { // package was built as part of the pipeline
        buildInfo = mapper.convertValue(requestMap.buildInfo, Map)
      }
      return createAugmentedRequest(trigger, buildInfo, requestMap)
    }
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
  private Map createAugmentedRequest(Map trigger, Map buildInfo, Map request) {
    List<Map> triggerArtifacts = trigger.buildInfo?.artifacts
    List<Map> buildArtifacts = buildInfo.artifacts
    if (!triggerArtifacts && !buildArtifacts) {
      return request
    }

    String prefix = "${request.package}${versionDelimiter}"
    String fileExtension = ".${packageType}"

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
      if(extractVersion) {
        packageVersion = extractPackageVersion(triggerArtifact, prefix, fileExtension)
      }
    }

    if (buildArtifact) {
      packageName = extractPackageName(buildArtifact, fileExtension)
      if(extractVersion) {
        packageVersion = extractPackageVersion(buildArtifact, prefix, fileExtension)
      }
    }

    if(packageVersion) {
      request.put('packageVersion', packageVersion)
    }

    if (packageName) {
      request.put('package', packageName)

      if (extractBuildDetails) {
        def buildInfoUrl = buildArtifact ? buildInfo?.url : trigger?.buildInfo?.url
        def buildInfoUrlParts = parseBuildInfoUrl(buildInfoUrl)

        if (buildInfoUrlParts?.size == 3) {
          request.put('buildHost', buildInfoUrlParts[0].toString())
          request.put('job', buildInfoUrlParts[1].toString())
          request.put('buildNumber', buildInfoUrlParts[2].toString())
        }
      }

      return request
    }

    throw new IllegalStateException("Unable to find deployable artifact starting with ${prefix} and ending with ${fileExtension} in ${buildArtifacts} and ${triggerArtifacts}")
  }

  @CompileDynamic
  private String extractPackageName(Map artifact, String fileExtension) {
    artifact.fileName.substring(0, artifact.fileName.lastIndexOf(fileExtension))
  }

  @CompileDynamic
  private String extractPackageVersion(Map artifact, String filePrefix, String fileExtension) {
    String version = artifact.fileName.substring(artifact.fileName.indexOf(filePrefix) + filePrefix.length(), artifact.fileName.lastIndexOf(fileExtension))
    if(version.contains(versionDelimiter)) { // further strip in case of _all is in the file name
      version = version.substring(0,version.indexOf(versionDelimiter))
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
  def parseBuildInfoUrl(String url) {
    List<String> urlParts = url?.tokenize("/")

    if (urlParts?.size == 5) {
      def buildNumber = urlParts.pop()
      def job = urlParts.pop()

      // Discard 'job' path segment.
      urlParts.pop()

      def buildHost = "${urlParts[0]}//${urlParts[1]}/"

      return [buildHost, job, buildNumber]
    }
  }
}
