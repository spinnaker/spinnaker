/*
 * Copyright 2020 Google, LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
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
 *
 */

package com.netflix.spinnaker.echo.telemetry

import com.netflix.spinnaker.echo.api.events.Event as EchoEvent
import com.netflix.spinnaker.echo.jackson.EchoObjectMapper
import com.netflix.spinnaker.kork.proto.stats.CloudProvider
import com.netflix.spinnaker.kork.proto.stats.Event as StatsEvent
import com.netflix.spinnaker.kork.proto.stats.Execution
import com.netflix.spinnaker.kork.proto.stats.Stage
import com.netflix.spinnaker.kork.proto.stats.Status
import java.util.ArrayList
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component

@Component
@ConditionalOnProperty(value = ["stats.enabled"], matchIfMissing = true)
class ExecutionDataProvider : TelemetryEventDataProvider {

  private val objectMapper = EchoObjectMapper.getInstance()

  override fun populateData(echoEvent: EchoEvent, statsEvent: StatsEvent): StatsEvent {
    val content = objectMapper.convertValue(echoEvent.getContent(), EventContent::class.java)
    val execution = content.execution

    var executionType = getExecutionType(execution.type)

    if (execution.source.type.equals("templatedPipeline", ignoreCase = true)) {
      if (execution.source.version.equals("v1", ignoreCase = true)) {
        executionType = Execution.Type.MANAGED_PIPELINE_TEMPLATE_V1
      } else if (execution.source.version.equals("v2", ignoreCase = true)) {
        executionType = Execution.Type.MANAGED_PIPELINE_TEMPLATE_V2
      }
    }

    val executionStatus = getStatus(execution.status)
    val triggerType = getTriggerType(execution.trigger.type)

    val protoStages = execution.stages.flatMap { toStages(it) }

    val executionBuilder = Execution.newBuilder()
      .setType(executionType)
      .setStatus(executionStatus)
      .setTrigger(Execution.Trigger.newBuilder().setType(triggerType))
      .addAllStages(protoStages)
    val executionId: String = execution.id
    if (executionId.isNotEmpty()) {
      executionBuilder.id = hash(executionId)
    }
    val executionProto = executionBuilder.build()

    return statsEvent.toBuilder()
      .setExecution(statsEvent.execution.toBuilder().mergeFrom(executionProto))
      .build()
  }

  private fun getExecutionType(type: String): Execution.Type =
    Execution.Type.valueOf(Execution.Type.getDescriptor().findMatchingValue(type))

  private fun getStatus(status: String): Status =
    Status.valueOf(Status.getDescriptor().findMatchingValue(status))

  private fun getTriggerType(type: String): Execution.Trigger.Type =
    Execution.Trigger.Type.valueOf(Execution.Trigger.Type.getDescriptor().findMatchingValue(type))

  private fun toStages(stage: EventContent.Stage): List<Stage> {
    // Only interested in user-configured stages.
    if (stage.isSyntheticStage()) {
      return listOf()
    }

    val stageStatus = getStatus(stage.status)
    val stageBuilder = Stage.newBuilder().setType(stage.type).setStatus(stageStatus)
    val returnList: MutableList<Stage> = ArrayList()
    val cloudProvider = stage.context.cloudProvider
    if (!cloudProvider.isNullOrEmpty()) {
      stageBuilder.cloudProvider = getCloudProvider(cloudProvider)
      returnList.add(stageBuilder.build())
    } else if (!stage.context.newState.cloudProviders.isNullOrEmpty()) {
      // Create and Update Application operations can specify multiple cloud providers in 1
      // operation.
      val cloudProviders = stage.context.newState.cloudProviders.split(",")
      for (cp in cloudProviders) {
        returnList.add(stageBuilder.clone().setCloudProvider(getCloudProvider(cp)).build())
      }
    } else {
      returnList.add(stageBuilder.build())
    }
    return returnList
  }

  private fun getCloudProvider(cloudProvider: String): CloudProvider {
    val cloudProviderId =
      CloudProvider.ID.valueOf(CloudProvider.ID.getDescriptor().findMatchingValue(cloudProvider))
    return CloudProvider.newBuilder().setId(cloudProviderId).build()
  }

  data class EventContent(val execution: Execution = Execution()) {

    data class Execution(
      val id: String = "",
      val type: String = "UNKNOWN",
      val status: String = "UNKNOWN",
      val trigger: Trigger = Trigger(),
      val source: Source = Source(),
      val stages: List<Stage> = listOf()
    )

    data class Trigger(val type: String = "UNKNOWN")

    data class Source(val type: String? = null, val version: String? = null)

    data class Stage(
      val status: String = "UNKNOWN",
      val type: String = "UNKNOWN",
      val syntheticStageOwner: String? = null,
      val context: Context = Context()
    ) {

      fun isSyntheticStage() = !syntheticStageOwner.isNullOrEmpty()
    }

    data class Context(val cloudProvider: String? = null, val newState: NewState = NewState())

    data class NewState(val cloudProviders: String? = null)
  }
}
