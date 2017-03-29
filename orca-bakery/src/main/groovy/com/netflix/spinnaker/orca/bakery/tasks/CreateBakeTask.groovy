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
import com.netflix.spinnaker.orca.ExecutionStatus
import com.netflix.spinnaker.orca.RetryableTask
import com.netflix.spinnaker.orca.TaskResult
import com.netflix.spinnaker.orca.bakery.api.BakeRequest
import com.netflix.spinnaker.orca.bakery.api.BakeStatus
import com.netflix.spinnaker.orca.bakery.api.BakeryService
import com.netflix.spinnaker.orca.pipeline.model.Pipeline
import com.netflix.spinnaker.orca.pipeline.model.Stage
import com.netflix.spinnaker.orca.pipeline.util.OperatingSystem
import com.netflix.spinnaker.orca.pipeline.util.PackageInfo
import com.netflix.spinnaker.orca.pipeline.util.PackageType
import groovy.transform.CompileDynamic
import groovy.transform.CompileStatic
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import retrofit.RetrofitError

@Component
@CompileStatic
class CreateBakeTask implements RetryableTask {

  long backoffPeriod = 30000
  long timeout = 300000

  @Autowired(required = false)
  BakeryService bakery
  @Autowired ObjectMapper mapper

  @Value('${bakery.extractBuildDetails:false}')
  boolean extractBuildDetails

  @Value('${bakery.roscoApisEnabled:false}')
  boolean roscoApisEnabled

  @Value('${bakery.allowMissingPackageInstallation:false}')
  boolean allowMissingPackageInstallation

  @Override
  TaskResult execute(Stage stage) {
    String region = stage.context.region
    if (!bakery) {
      throw new UnsupportedOperationException("You have not enabled baking for this orca instance. Set bakery.enabled: true")
    }

    try {
      // If the user has specified a base OS that is unrecognized by Rosco, this method will
      // throw a Retrofit exception (HTTP 404 Not Found)
      def bake = bakeFromContext(stage)
      String rebake = shouldRebake(stage) ? "1" : null
      def bakeStatus = bakery.createBake(region, bake, rebake).toBlocking().single()

      def stageOutputs = [
        status          : bakeStatus,
        bakePackageName : bake.packageName ?: "",
        previouslyBaked : bakeStatus.state == BakeStatus.State.COMPLETED
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
    if (stage.execution instanceof Pipeline) {
      Map trigger = ((Pipeline) stage.execution).trigger
      return trigger?.rebake == true
    }
    return false
  }

  @CompileDynamic
  private BakeRequest bakeFromContext(Stage stage) {
    PackageType packageType
    if (roscoApisEnabled) {
      def baseImage = bakery.getBaseImage(stage.context.cloudProviderType as String,
                                          stage.context.baseOs as String).toBlocking().single()
      packageType = baseImage.packageType as PackageType
    } else {
      OperatingSystem operatingSystem = OperatingSystem.valueOf(stage.context.baseOs as String)
      packageType = operatingSystem.packageType
    }

    PackageInfo packageInfo = new PackageInfo(stage,
                                              packageType.packageType,
                                              packageType.versionDelimiter,
                                              extractBuildDetails,
                                              false /* extractVersion */,
                                              mapper)

    Map requestMap = packageInfo.findTargetPackage(allowMissingPackageInstallation)

    def request = mapper.convertValue(requestMap, BakeRequest)
    if (!roscoApisEnabled) {
      request.other.clear()
    }
    return request
  }
}
