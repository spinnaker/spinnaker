/*
 * Copyright 2018 Netflix, Inc.
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

package com.netflix.spinnaker.orca.kayenta.tasks

import com.netflix.spinnaker.kork.exceptions.UserException
import com.netflix.spinnaker.kork.retrofit.Retrofit2SyncCall
import com.netflix.spinnaker.orca.api.pipeline.Task
import com.netflix.spinnaker.orca.api.pipeline.TaskResult
import com.netflix.spinnaker.orca.api.pipeline.models.ExecutionStatus.SUCCEEDED
import com.netflix.spinnaker.orca.api.pipeline.models.StageExecution
import com.netflix.spinnaker.orca.ext.mapTo
import com.netflix.spinnaker.orca.kayenta.CanaryExecutionRequest
import com.netflix.spinnaker.orca.kayenta.KayentaService
import com.netflix.spinnaker.orca.kayenta.RunCanaryContext
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@Component
class RunKayentaCanaryTask(
  private val kayentaService: KayentaService
) : Task {

  private val log = LoggerFactory.getLogger(javaClass)

  override fun execute(stage: StageExecution): TaskResult {
    val context = stage.mapTo<RunCanaryContext>()

    if (context.canaryConfigId.isNullOrEmpty()) {
      throw UserException("Invalid canaryConfigId. Was the correct configName or configId used?")
    }

    val canaryPipelineExecutionId = Retrofit2SyncCall.execute(kayentaService.create(
      context.canaryConfigId,
      stage.execution.application,
      stage.execution.id,
      context.metricsAccountName,
      context.configurationAccountName,
      context.storageAccountName,
      CanaryExecutionRequest(context.scopes, context.scoreThresholds, context.siteLocal)
    ))["canaryExecutionId"] as String

    val outputs = mapOf(
      "canaryPipelineExecutionId" to canaryPipelineExecutionId,
      "canaryConfigId" to context.canaryConfigId
    )

    return TaskResult.builder(SUCCEEDED).context("canaryPipelineExecutionId", canaryPipelineExecutionId).outputs(outputs).build()
  }
}
