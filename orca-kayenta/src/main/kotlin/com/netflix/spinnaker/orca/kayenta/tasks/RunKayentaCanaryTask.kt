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

import com.fasterxml.jackson.module.kotlin.convertValue
import com.netflix.spinnaker.orca.ExecutionStatus.SUCCEEDED
import com.netflix.spinnaker.orca.Task
import com.netflix.spinnaker.orca.TaskResult
import com.netflix.spinnaker.orca.ext.mapTo
import com.netflix.spinnaker.orca.jackson.OrcaObjectMapper
import com.netflix.spinnaker.orca.kayenta.CanaryExecutionRequest
import com.netflix.spinnaker.orca.kayenta.CanaryScopes
import com.netflix.spinnaker.orca.kayenta.KayentaService
import com.netflix.spinnaker.orca.kayenta.model.RunCanaryContext
import com.netflix.spinnaker.orca.pipeline.model.Stage
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.util.Collections.singletonMap

@Component
class RunKayentaCanaryTask(
  private val kayentaService: KayentaService
) : Task {

  private val log = LoggerFactory.getLogger(javaClass)

  override fun execute(stage: Stage): TaskResult {
    val context = stage.mapTo<RunCanaryContext>()
    // The `DeployCanaryServerGroups` stage will deploy a list of experiment/control
    // pairs, but we will only canary the first pair in `deployedServerGroups`.
    val scopes = stage.context["deployedServerGroups"]?.let {
      val pairs = OrcaObjectMapper.newInstance().convertValue<List<DeployedServerGroupPair>>(it)
      context.scopes.from(pairs.first())
    } ?: context.scopes

    val canaryPipelineExecutionId = kayentaService.create(
      context.canaryConfigId,
      stage.execution.application,
      stage.execution.id,
      context.metricsAccountName,
      context.configurationAccountName,
      context.storageAccountName,
      CanaryExecutionRequest(scopes, context.scoreThresholds)
    )["canaryExecutionId"] as String

    return TaskResult(SUCCEEDED, singletonMap("canaryPipelineExecutionId", canaryPipelineExecutionId))
  }
}

private fun Map<String, CanaryScopes>.from(pair: DeployedServerGroupPair): Map<String, CanaryScopes> {
  return entries.associate { (key, scope) ->
    key to scope.copy(
      controlScope = scope.controlScope.copy(
        scope = pair.controlScope,
        location = pair.controlLocation
      ),
      experimentScope = scope.experimentScope.copy(
        scope = pair.experimentScope,
        location = pair.experimentLocation
      )
    )
  }
}

internal data class DeployedServerGroupPair(
  val controlLocation: String,
  val controlScope: String,
  val experimentLocation: String,
  val experimentScope: String
)
