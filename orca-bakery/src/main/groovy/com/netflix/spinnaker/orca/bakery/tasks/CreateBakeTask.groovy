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

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.kork.artifacts.model.Artifact
import com.netflix.spinnaker.kork.core.RetrySupport
import com.netflix.spinnaker.orca.ExecutionStatus
import com.netflix.spinnaker.orca.RetryableTask
import com.netflix.spinnaker.orca.TaskResult
import com.netflix.spinnaker.orca.bakery.api.BakeRequest
import com.netflix.spinnaker.orca.bakery.api.BakeStatus
import com.netflix.spinnaker.orca.bakery.api.BakeryService
import com.netflix.spinnaker.orca.front50.Front50Service
import com.netflix.spinnaker.orca.front50.model.Application
import com.netflix.spinnaker.orca.pipeline.model.Stage
import com.netflix.spinnaker.orca.pipeline.util.ArtifactResolver
import com.netflix.spinnaker.orca.pipeline.util.OperatingSystem
import com.netflix.spinnaker.orca.pipeline.util.PackageInfo
import com.netflix.spinnaker.orca.pipeline.util.PackageType
import groovy.transform.CompileDynamic
import groovy.transform.CompileStatic
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import retrofit.RetrofitError

@Component
@CompileStatic
class CreateBakeTask implements RetryableTask {

  long backoffPeriod = 30000
  long timeout = 300000

  @Autowired
  ArtifactResolver artifactResolver

  @Autowired(required = false)
  BakeryService bakery

  @Autowired ObjectMapper mapper

  @Autowired(required = false)
  Front50Service front50Service

  @Value('${bakery.extractBuildDetails:false}')
  boolean extractBuildDetails

  @Value('${bakery.roscoApisEnabled:false}')
  boolean roscoApisEnabled

  @Value('${bakery.allowMissingPackageInstallation:false}')
  boolean allowMissingPackageInstallation

  RetrySupport retrySupport = new RetrySupport()

  private final Logger log = LoggerFactory.getLogger(getClass())

  @Override
  TaskResult execute(Stage stage) {
    String region = stage.context.region
    if (!bakery) {
      throw new UnsupportedOperationException("You have not enabled baking for this orca instance. Set bakery.enabled: true")
    }

    // If application exists, we should pass the owner of the application as the user to the bakery
    try {
      if (front50Service != null) {
        String appName = stage.execution.application
        Application application = retrySupport.retry({return front50Service.get(appName)}, 5, 2000, false)
        String user = application.email
        if (user != null && user != "") {
          stage.context.user = user
        }
      }
    } catch (RetrofitError e) {
      // ignore exception, we will just use the owner passed to us
      if (!e.message.contains("404")) {
        log.warn("Error retrieving application {} from front50, ignoring.", stage.execution.application, e)
      }
    }

    try {
      // If the user has specified a base OS that is unrecognized by Rosco, this method will
      // throw a Retrofit exception (HTTP 404 Not Found)
      def bake = bakeFromContext(stage)
      String rebake = shouldRebake(stage) ? "1" : null
      def bakeStatus = bakery.createBake(region, bake, rebake).toBlocking().single()

      def stageOutputs = [
        status         : bakeStatus,
        bakePackageName: bake.packageName ?: "",
        previouslyBaked: bakeStatus.state == BakeStatus.State.COMPLETED
      ] as Map<String, ? extends Object>

      if (bake.buildInfoUrl) {
        stageOutputs.buildInfoUrl = bake.buildInfoUrl
      }

      if (bake.buildHost) {
        stageOutputs << [
          buildHost  : bake.buildHost,
          job        : bake.job,
          buildNumber: bake.buildNumber
        ]

        if (bake.commitHash) {
          stageOutputs.commitHash = bake.commitHash
        }
      }

      new TaskResult(ExecutionStatus.SUCCEEDED, stageOutputs)
    } catch (RetrofitError e) {
      if (e.response?.status && e.response.status == 404) {
        try {
          def exceptionResult = mapper.readValue(e.response.body.in().text, Map)
          def exceptionMessages = (exceptionResult.messages ?: []) as List<String>
          if (exceptionMessages) {
            throw new IllegalStateException(exceptionMessages[0])
          }
        } catch (IOException ignored) {
          // do nothing
        }

        return new TaskResult(ExecutionStatus.RUNNING)
      }
      throw e
    }
  }

  private static boolean shouldRebake(Stage stage) {
    if (stage.context.rebake == true) {
      return true
    }
    return stage.execution.trigger?.rebake
  }

  @CompileDynamic
  private BakeRequest bakeFromContext(Stage stage) {
    PackageType packageType
    if (roscoApisEnabled) {
      def baseImage = bakery.getBaseImage(stage.context.cloudProviderType as String,
        stage.context.baseOs as String).toBlocking().single()
      packageType = baseImage.packageType as PackageType
    } else {
      packageType = new OperatingSystem(stage.context.baseOs as String).getPackageType()
    }

    List<Artifact> artifacts = artifactResolver.getAllArtifacts(stage.getExecution())

    PackageInfo packageInfo = new PackageInfo(stage,
      artifacts,
      packageType.packageType,
      packageType.versionDelimiter,
      extractBuildDetails,
      false /* extractVersion */,
      mapper)

    Map requestMap = packageInfo.findTargetPackage(allowMissingPackageInstallation)

    // if the field "packageArtifactIds" is present in the context, because it was set in the UI,
    // this will resolve those ids into real artifacts and then put them in List<Artifact> packageArtifacts
    requestMap.packageArtifacts = stage.context.packageArtifactIds.collect { String artifactId ->
      artifactResolver.getBoundArtifactForId(stage, artifactId)
    }

    // Workaround for deck/titusBakeStage.js historically injecting baseOs=trusty into stage definitions;
    // baseOs is unnecessary for docker bakes
    if ("docker" == requestMap.storeType) {
      requestMap.remove("baseOs")
    }

    def request = mapper.convertValue(requestMap, BakeRequest)
    if (!roscoApisEnabled) {
      request.other.clear()
    }
    return request
  }
}
