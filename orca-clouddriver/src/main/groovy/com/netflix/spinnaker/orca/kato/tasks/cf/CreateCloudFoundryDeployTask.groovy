package com.netflix.spinnaker.orca.kato.tasks.cf

import com.fasterxml.jackson.databind.ObjectMapper
import com.google.common.annotations.VisibleForTesting
import com.netflix.frigga.NameBuilder
import com.netflix.spinnaker.orca.DefaultTaskResult
import com.netflix.spinnaker.orca.ExecutionStatus
import com.netflix.spinnaker.orca.Task
import com.netflix.spinnaker.orca.TaskResult
import com.netflix.spinnaker.orca.clouddriver.KatoService
import com.netflix.spinnaker.orca.clouddriver.model.TaskId
import com.netflix.spinnaker.orca.clouddriver.utils.HealthHelper
import com.netflix.spinnaker.orca.pipeline.model.Pipeline
import com.netflix.spinnaker.orca.pipeline.model.Stage
import groovy.transform.CompileDynamic
import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

/**
 * Craft a deployment task for Cloud Foundry.
 */
@Slf4j
@Component
class CreateCloudFoundryDeployTask implements Task {

  @Autowired
  KatoService kato

  @Autowired
  ObjectMapper mapper

  @Override
  TaskResult execute(Stage stage) {
    def operation = convert(stage)
    def taskId = deploy(operation)

    def regionGroups = [:].withDefault { [] }
    operation.availabilityZones.each { key, value ->
      regionGroups[key] << operation.serverGroupName
    }

    new DefaultTaskResult(ExecutionStatus.SUCCEEDED,
        [
            "cloudProvider"       : "cf",
            "notification.type"   : "createdeploy",
            "kato.last.task.id"   : taskId,
            "account.name"        : operation.credentials,
            "deploy.account.name" : operation.credentials,
            "deploy.server.groups": regionGroups,
            interestingHealthProviderNames: HealthHelper.getInterestingHealthProviderNames(stage, ["serverGroup"])

        ]
    )
  }

  Map convert(Stage stage) {
    def operation = [:]

    if (stage.context.containsKey("clusters")) {
      stage.context.clusters.each { operation.putAll(it as Map) }
    } else {
      operation.putAll(stage.context)
    }

    if (operation.account && !operation.credentials) {
      operation.credentials = operation.account
    }

    CloudFoundryPackageInfo info = new CloudFoundryPackageInfo(stage, 'jar', '-', true, true, mapper)
    operation.targetPackage = info.findTargetPackage()

    def nameBuilder = new NameBuilder() {
      @Override
      public String combineAppStackDetail(String appName, String stack, String detail) {
        return super.combineAppStackDetail(appName, stack, detail)
      }
    }

    def clusterName = nameBuilder.combineAppStackDetail(operation.application, operation.stack, operation.freeFormDetails)
    def nextSequence = operation.targetPackage.buildNumber.toInteger() % 1000
    operation.serverGroupName = "${clusterName}-v".toString() + String.format("%03d", nextSequence)

    operation
  }

  private TaskId deploy(Map deployOperation) {
    log.info("Deploying $deployOperation")

    kato.requestOperations('cf', [[cloudFoundryDeployDescription: deployOperation]]).toBlocking().first()
  }

  static class CloudFoundryPackageInfo {
    ObjectMapper mapper
    Stage stage
    String versionDelimiter
    String packageType
    boolean extractBuildDetails
    boolean extractVersion

    CloudFoundryPackageInfo(Stage stage, String packageType, String versionDelimiter, boolean extractBuildDetails, boolean extractVersion, ObjectMapper mapper) {
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
      requestMap.putAll(stage.context)
      if (stage.execution instanceof Pipeline) {
        Map trigger = ((Pipeline) stage.execution).trigger
        Map buildInfo = [:]
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
      List<Map> triggerArtifacts = trigger.buildInfo?.artifacts
      List<Map> buildArtifacts = buildInfo.artifacts
      if ((!triggerArtifacts && !buildArtifacts) || isUrl(request.package)) {
        return request
      }

      String fileExtension = ".${packageType}"

      Map triggerArtifact = filterArtifacts(triggerArtifacts, fileExtension)
      Map buildArtifact = filterArtifacts(buildArtifacts, fileExtension)

      // only one unique package per pipeline is allowed
      if (triggerArtifact && buildArtifact && triggerArtifact.fileName != buildArtifact.fileName) {
        throw new IllegalStateException("Found build artifact in Jenkins stage and Pipeline Trigger")
      }

      String packageName
      String packageVersion

      if (triggerArtifact) {
        packageName = extractPackageName(triggerArtifact, fileExtension)
        if(extractVersion) {
          packageVersion = extractPackageVersion(triggerArtifact, fileExtension)
        }
        request.put('relativePath', triggerArtifact.relativePath)
      }

      if (buildArtifact) {
        packageName = extractPackageName(buildArtifact, fileExtension)
        if (extractVersion) {
          packageVersion = extractPackageVersion(buildArtifact, fileExtension)
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

          if (buildInfoUrlParts?.size >= 2) {
            request.put('job', buildInfoUrlParts[0].toString())
            request.put('buildNumber', buildInfoUrlParts[1].toString())
            request.put('buildUrl', buildInfoUrl)
          }

          def commitHash = buildArtifact ? extractCommitHash(buildInfo) : extractCommitHash(trigger?.buildInfo)

          if (commitHash) {
            request.put('commitHash', commitHash)
          }
        }

        return request
      }

      throw new IllegalStateException("Unable to find deployable artifact ending with ${fileExtension} in ${buildArtifacts} and ${triggerArtifacts}")
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
    private String extractPackageVersion(Map artifact, String fileExtension) {
      String version = artifact.fileName.substring(0, artifact.fileName.lastIndexOf(fileExtension))
      if(version.contains(versionDelimiter)) { // further strip in case of _all is in the file name
        version = version.substring(0,version.indexOf(versionDelimiter))
      }
      return version
    }

    @CompileDynamic
    private Map filterArtifacts(List<Map> artifacts, String fileExtension) {
      artifacts.find {
        it.fileName?.endsWith(fileExtension)
      }
    }

    @CompileDynamic
    // Naming-convention for buildInfo.url is $protocol://$buildHost/job/$job/$buildNumber/.
    // For example: http://spinnaker.builds.test.netflix.net/job/SPINNAKER-package-echo/69/
    def parseBuildInfoUrl(String url) {
      List<String> urlParts = url?.tokenize("/")

      def buildNumber = urlParts.pop()
      def job = urlParts.pop()

      [job, buildNumber]
    }
  }


}
