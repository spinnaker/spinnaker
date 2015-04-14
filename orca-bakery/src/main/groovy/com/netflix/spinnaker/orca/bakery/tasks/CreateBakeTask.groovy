/*
 * Copyright 2014 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
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

package com.netflix.spinnaker.orca.bakery.tasks

import com.netflix.spinnaker.orca.pipeline.model.Pipeline
import groovy.transform.CompileDynamic
import groovy.transform.CompileStatic
import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.orca.DefaultTaskResult
import com.netflix.spinnaker.orca.ExecutionStatus
import com.netflix.spinnaker.orca.Task
import com.netflix.spinnaker.orca.TaskResult
import com.netflix.spinnaker.orca.bakery.api.BakeRequest
import com.netflix.spinnaker.orca.bakery.api.BakeryService
import com.netflix.spinnaker.orca.pipeline.model.Stage
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

@Component
@CompileStatic
class CreateBakeTask implements Task {

  @Autowired BakeryService bakery
  @Autowired ObjectMapper mapper

  @Value('${bakery.extractBuildDetails:false}')
  boolean extractBuildDetails

  @Override
  TaskResult execute(Stage stage) {
    String region = stage.context.region
    def bake = bakeFromContext(stage)

    def bakeStatus = bakery.createBake(region, bake).toBlocking().single()

    if (bake.buildHost) {
      new DefaultTaskResult(ExecutionStatus.SUCCEEDED, [
        status: bakeStatus,
        bakePackageName: bake.packageName,
        buildHost: bake.buildHost,
        job: bake.job,
        buildNumber: bake.buildNumber
      ])
    } else {
      new DefaultTaskResult(ExecutionStatus.SUCCEEDED, [
        status: bakeStatus,
        bakePackageName: bake.packageName
      ])
    }
  }

  @CompileDynamic
  private BakeRequest bakeFromContext(Stage stage) {

    BakeRequest request = mapper.convertValue(stage.context, BakeRequest)
    if (stage.execution instanceof Pipeline) {
      Map trigger = ((Pipeline) stage.execution).trigger
      Map buildInfo = [:]
      if (stage.context.buildInfo) {
        buildInfo = mapper.convertValue(stage.context.buildInfo, Map)
      }

      return createAugmentedRequest(trigger, buildInfo, request)
    }
    return request
  }

  @CompileDynamic
  private BakeRequest createAugmentedRequest(Map trigger, Map buildInfo, BakeRequest request) {
    List<Map> triggerArtifacts = trigger.buildInfo?.artifacts
    List<Map> buildArtifacts = buildInfo.artifacts
    if (!triggerArtifacts && !buildArtifacts) {
      return request
    }

    String prefix = "${request.packageName}${request.baseOs.packageType.versionDelimiter}"
    String fileExtension = ".${request.baseOs.packageType.packageType}"

    Map triggerArtifact = filterArtifacts(triggerArtifacts, prefix, fileExtension)
    Map buildArtifact = filterArtifacts(buildArtifacts, prefix, fileExtension)

    if (triggerArtifact && buildArtifact && triggerArtifact.fileName != buildArtifact.fileName) {
      throw new IllegalStateException("Found build artifact in Jenkins stage and Pipeline Trigger")
    }

    String packageName

    if (triggerArtifact) {
      packageName = extractPackageName(triggerArtifact, fileExtension)
    }

    if (buildArtifact) {
      packageName = extractPackageName(buildArtifact, fileExtension)
    }

    if (packageName) {
      def augmentedRequest = request.copyWith(packageName: packageName)

      if (extractBuildDetails) {
        if (trigger?.buildInfo?.url && buildInfo?.url && trigger?.buildInfo?.url != buildInfo?.url) {
          throw new IllegalStateException("Found mismatched build urls in Jenkins stage and Pipeline Trigger.")
        }

        def buildInfoUrlParts

        if (trigger?.buildInfo?.url) {
          buildInfoUrlParts = parseBuildInfoUrl(trigger.buildInfo.url)
        }

        if (buildInfo?.url) {
          buildInfoUrlParts = parseBuildInfoUrl(buildInfo.url)
        }

        if (buildInfoUrlParts?.size == 3) {
          augmentedRequest = augmentedRequest.copyWith(buildHost: buildInfoUrlParts[0],
                                                       job: buildInfoUrlParts[1],
                                                       buildNumber: buildInfoUrlParts[2])
        }
      }

      return augmentedRequest
    }

    throw new IllegalStateException("Unable to find deployable artifact starting with ${prefix} and ending with ${fileExtension} in ${buildArtifacts} and ${triggerArtifacts}")
  }

  @CompileDynamic
  private String extractPackageName(Map artifact, String fileExtension) {
    artifact.fileName.substring(0, artifact.fileName.lastIndexOf(fileExtension))
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

    if (urlParts.size == 5) {
      def buildNumber = urlParts.pop()
      def job = urlParts.pop()

      // Discard 'job' path segment.
      urlParts.pop()

      def buildHost = "${urlParts[0]}//${urlParts[1]}/"

      return [buildHost, job, buildNumber]
    }
  }
}
