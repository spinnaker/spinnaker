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

import com.netflix.spinnaker.orca.RetryableTask
import com.netflix.spinnaker.orca.pipeline.util.OperatingSystem
import com.netflix.spinnaker.orca.pipeline.util.PackageInfo
import groovy.transform.CompileDynamic
import groovy.transform.CompileStatic
import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.orca.DefaultTaskResult
import com.netflix.spinnaker.orca.ExecutionStatus
import com.netflix.spinnaker.orca.TaskResult
import com.netflix.spinnaker.orca.bakery.api.BakeRequest
import com.netflix.spinnaker.orca.bakery.api.BakeryService
import com.netflix.spinnaker.orca.pipeline.model.Stage
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import retrofit.RetrofitError

@Component
@CompileStatic
class CreateBakeTask implements RetryableTask {

  long backoffPeriod = 30000
  long timeout = 300000

  @Autowired BakeryService bakery
  @Autowired ObjectMapper mapper

  @Value('${bakery.extractBuildDetails:false}')
  boolean extractBuildDetails


  @Value('${bakery.propagateCloudProviderType:false}')
  boolean propagateCloudProviderType

  @Override
  TaskResult execute(Stage stage) {
    String region = stage.context.region
    def bake = bakeFromContext(stage)

    try {
      def bakeStatus = bakery.createBake(region, bake).toBlocking().single()

      def stageOutputs = [
        status: bakeStatus,
        bakePackageName: bake.packageName
      ] as Map<String, ? extends Object>

      if (bake.buildHost) {
        stageOutputs << [
          buildHost: bake.buildHost,
          job      : bake.job,
          buildNumber: bake.buildNumber
        ]

        if (bake.commitHash) {
          stageOutputs.commitHash = bake.commitHash
        }
      }

      new DefaultTaskResult(ExecutionStatus.SUCCEEDED, stageOutputs)
    } catch (RetrofitError e) {
      if (e.response?.status == 404) {
        return new DefaultTaskResult(ExecutionStatus.RUNNING)
      }
      throw e
    }
  }

  @CompileDynamic
  private BakeRequest bakeFromContext(Stage stage) {
    OperatingSystem operatingSystem = OperatingSystem.valueOf(stage.context.baseOs as String)

    PackageInfo packageInfo = new PackageInfo(stage, operatingSystem.packageType.packageType,
      operatingSystem.packageType.versionDelimiter, extractBuildDetails, false, mapper)
    Map requestMap = packageInfo.findTargetPackage()
    BakeRequest bakeRequest = mapper.convertValue(requestMap, BakeRequest)

    if (!propagateCloudProviderType) {
      bakeRequest = bakeRequest.copyWith(cloudProviderType: null)
    }

    return bakeRequest
  }
}
