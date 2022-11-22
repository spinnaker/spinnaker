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
import com.netflix.spinnaker.orca.api.pipeline.models.ExecutionStatus
import com.netflix.spinnaker.orca.api.pipeline.RetryableTask
import com.netflix.spinnaker.orca.api.pipeline.models.StageExecution
import com.netflix.spinnaker.orca.api.pipeline.TaskResult
import com.netflix.spinnaker.orca.bakery.BakerySelector
import com.netflix.spinnaker.orca.bakery.api.BakeRequest
import com.netflix.spinnaker.orca.bakery.api.BakeStatus
import com.netflix.spinnaker.orca.bakery.api.BakeryService
import com.netflix.spinnaker.orca.front50.Front50Service
import com.netflix.spinnaker.orca.front50.model.Application
import com.netflix.spinnaker.orca.pipeline.util.ArtifactUtils
import com.netflix.spinnaker.orca.pipeline.util.OperatingSystem
import com.netflix.spinnaker.orca.pipeline.util.PackageInfo
import com.netflix.spinnaker.orca.pipeline.util.PackageType
import groovy.transform.CompileDynamic
import groovy.transform.CompileStatic
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import retrofit.RetrofitError

import static com.netflix.spinnaker.kork.web.selector.v2.SelectableService.*

@Component
@CompileStatic
class CreateBakeTask implements RetryableTask {

  long backoffPeriod = 30000
  long timeout = 300000

  @Autowired
  ArtifactUtils artifactUtils

  @Autowired(required = false)
  BakerySelector bakerySelector

  @Autowired ObjectMapper mapper

  @Autowired(required = false)
  Front50Service front50Service

  RetrySupport retrySupport = new RetrySupport()

  private final Logger log = LoggerFactory.getLogger(getClass())

  @Override
  TaskResult execute(StageExecution stage) {
    if (!bakerySelector) {
      throw new UnsupportedOperationException("You have not enabled baking for this orca instance. Set bakery.enabled: true")
    }

    def bakery = bakerySelector.select(stage)
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
      BakeRequest bake = bakeFromContext(stage, bakery)
      String rebake = shouldRebake(stage) ? "1" : null

      if (stage.context.amiSuffix != null && bake.amiSuffix != null && bake.amiSuffix != stage.context.amiSuffix) {
        log.warn("Bake request amiSuffix ${bake.amiSuffix} differs from stage context amiSuffix ${stage.context.amiSuffix}, resolving...")
        bake.amiSuffix = stage.context.amiSuffix
      }

      def bakeStatus = bakery.service.createBake(stage.context.region as String, bake, rebake)

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

      TaskResult.builder(ExecutionStatus.SUCCEEDED).context(stageOutputs).build()
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

        return TaskResult.ofStatus(ExecutionStatus.RUNNING)
      }
      throw e
    }
  }

  private static boolean shouldRebake(StageExecution stage) {
    if (stage.context.rebake == true) {
      return true
    }
    return stage.execution.trigger?.rebake
  }

  private BakeRequest bakeFromContext(StageExecution stage, SelectedService<BakeryService> bakery) {
    PackageType packageType
    packageType = stage.context.baseOs != null ? getBaseOsPackageType(bakery, stage) : getCustomPackageType(stage)
    stage.context.packageType = packageType
    List<Artifact> artifacts = artifactUtils.getAllArtifacts(stage.getExecution())

    PackageInfo packageInfo = new PackageInfo(stage,
      artifacts,
      packageType.packageType,
      packageType.versionDelimiter,
      bakery.config.extractBuildDetails as Boolean,
      false /* extractVersion */,
      mapper)

    Map requestMap = packageInfo.findTargetPackage(bakery.config.allowMissingPackageInstallation as Boolean)

    if (stage.context.account) {
      requestMap.accountName = stage.context.account as String
    }

    // if the field "packageArtifactIds" is present in the context, because it was set in the UI,
    // this will resolve those ids into real artifacts and then put them in List<Artifact> packageArtifacts
    requestMap.packageArtifacts = stage.context.packageArtifactIds.collect { String artifactId ->
      artifactUtils.getBoundArtifactForId(stage, artifactId)
    }

    // Workaround for deck/titusBakeStage.js historically injecting baseOs=trusty into stage definitions;
    // baseOs is unnecessary for docker bakes
    if ("docker" == requestMap.storeType) {
      requestMap.remove("baseOs")
    }

    if (requestMap.baseName == "") {
      requestMap.remove("baseName")
    }

    if (stage.context.baseOs == null) {
      requestMap.custom_managed_image_name = stage.context.managedImage as String
      if (stage.context.managedImage == null) {
        requestMap.sku = stage.context.sku as String
        requestMap.offer = stage.context.offer as String
        requestMap.publisher = stage.context.publisher as String
      }
    }

    def request = mapper.convertValue(requestMap, BakeRequest)
    if (!bakery.config.roscoApisEnabled) {
      request.other().clear()
    }

    return request
  }

  private static PackageType getCustomPackageType(StageExecution stage) {
    if(stage.context.osType != "linux") {
      return PackageType.NUPKG
    }

    PackageType type = stage.context.packageType as PackageType
    if (type == null) {
      type = new OperatingSystem(stage.context.managedImage as String).getPackageType()
    }

    type
  }

  private static PackageType getBaseOsPackageType(SelectedService<BakeryService> bakery, StageExecution stage) {
    bakery.config.roscoApisEnabled
        ? bakery.service.getBaseImage(stage.context.cloudProviderType as String, stage.context.baseOs as String).packageType
        : new OperatingSystem(stage.context.baseOs as String).getPackageType()
  }
}
