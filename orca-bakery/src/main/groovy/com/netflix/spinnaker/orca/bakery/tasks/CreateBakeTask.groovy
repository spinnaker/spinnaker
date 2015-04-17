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
import com.netflix.spinnaker.orca.pipeline.util.OperatingSystem
import com.netflix.spinnaker.orca.pipeline.util.PackageInfo
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
    OperatingSystem operatingSystem = OperatingSystem.valueOf(stage.context.baseOs)

    PackageInfo packageInfo = new PackageInfo(stage, operatingSystem.packageType.packageType,
      operatingSystem.packageType.versionDelimiter, extractBuildDetails, false, mapper)
    Map requestMap = packageInfo.findTargetPackage()
    return mapper.convertValue(requestMap, BakeRequest)
  }
}
