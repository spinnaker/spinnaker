/*
 * Copyright 2016 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.orca.kato.tasks.quip

import com.netflix.spinnaker.kork.retrofit.Retrofit2SyncCall
import com.netflix.spinnaker.orca.api.pipeline.models.StageExecution

import java.util.concurrent.TimeUnit
import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.orca.api.pipeline.models.ExecutionStatus
import com.netflix.spinnaker.orca.api.pipeline.RetryableTask
import com.netflix.spinnaker.orca.api.pipeline.TaskResult
import com.netflix.spinnaker.orca.bakery.api.BakeryService
import com.netflix.spinnaker.orca.pipeline.util.OperatingSystem
import com.netflix.spinnaker.orca.pipeline.util.PackageInfo
import com.netflix.spinnaker.orca.pipeline.util.PackageType
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

@Deprecated
@Component
class ResolveQuipVersionTask implements RetryableTask {

  final long backoffPeriod = TimeUnit.SECONDS.toMillis(5)

  final long timeout = TimeUnit.SECONDS.toMillis(30)

  @Autowired(required = false)
  BakeryService bakeryService

  @Value('${bakery.rosco-apis-enabled:false}')
  boolean roscoApisEnabled

  @Value('${bakery.allow-missing-package-installation:false}')
  boolean allowMissingPackageInstallation

  @Autowired
  ObjectMapper objectMapper

  @Override
  TaskResult execute(StageExecution stage) {
    PackageType packageType
    if (!bakeryService) {
      throw new UnsupportedOperationException("You have not enabled baking for this orca instance. Set bakery.enabled: true")
    }
    if (roscoApisEnabled) {
      def baseImage = Retrofit2SyncCall.execute(bakeryService.getBaseImage(stage.context.cloudProviderType as String,
        stage.context.baseOs as String)).toBlocking().single()
      packageType = baseImage.packageType
    } else {
      packageType = new OperatingSystem(stage.context.baseOs as String).getPackageType()
    }
    PackageInfo packageInfo = new PackageInfo(stage,
      [],
      packageType.packageType,
      packageType.versionDelimiter,
      true, // extractBuildDetails
      true, // extractVersion
      objectMapper)
    String version = stage.context?.patchVersion ?:  packageInfo.findTargetPackage(allowMissingPackageInstallation)?.packageVersion

    return TaskResult.builder(ExecutionStatus.SUCCEEDED).context([version: version]).outputs([version:version]).build()
  }
}
