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

import java.util.concurrent.TimeUnit
import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.orca.ExecutionStatus
import com.netflix.spinnaker.orca.RetryableTask
import com.netflix.spinnaker.orca.TaskResult
import com.netflix.spinnaker.orca.bakery.api.BakeryService
import com.netflix.spinnaker.orca.pipeline.model.Stage
import com.netflix.spinnaker.orca.pipeline.util.OperatingSystem
import com.netflix.spinnaker.orca.pipeline.util.PackageInfo
import com.netflix.spinnaker.orca.pipeline.util.PackageType
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

@Component
class ResolveQuipVersionTask implements RetryableTask {

  final long backoffPeriod = TimeUnit.SECONDS.toMillis(5)

  final long timeout = TimeUnit.SECONDS.toMillis(30)

  @Autowired(required = false)
  BakeryService bakeryService

  @Value('${bakery.roscoApisEnabled:false}')
  boolean roscoApisEnabled

  @Value('${bakery.allowMissingPackageInstallation:false}')
  boolean allowMissingPackageInstallation

  @Autowired
  ObjectMapper objectMapper

  @Override
  TaskResult execute(Stage stage) {
    PackageType packageType
    if (!bakeryService) {
      throw new UnsupportedOperationException("You have not enabled baking for this orca instance. Set bakery.enabled: true")
    }
    if (roscoApisEnabled) {
      def baseImage = bakeryService.getBaseImage(stage.context.cloudProviderType as String,
        stage.context.baseOs as String).toBlocking().single()
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

    return new TaskResult(ExecutionStatus.SUCCEEDED, [version: version], [version:version])
  }
}
