/*
 * Copyright 2018 Netflix, Inc.
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
package com.netflix.spinnaker.keel.intent.processor.converter

import com.amazonaws.util.Base64
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.convertValue
import com.netflix.spinnaker.keel.dryrun.ChangeSummary
import com.netflix.spinnaker.keel.front50.model.PipelineConfig
import com.netflix.spinnaker.keel.intent.PipelineFlags
import com.netflix.spinnaker.keel.intent.PipelineProperties
import com.netflix.spinnaker.keel.intent.PipelineSpec
import com.netflix.spinnaker.keel.intent.PipelineStage
import com.netflix.spinnaker.keel.intent.SpecConverter
import com.netflix.spinnaker.keel.intent.Trigger
import com.netflix.spinnaker.keel.model.Job
import org.springframework.stereotype.Component

@Component
class PipelineConverter(
  private val objectMapper: ObjectMapper
) : SpecConverter<PipelineSpec, PipelineConfig> {

  override fun convertToState(spec: PipelineSpec) =
    PipelineConfig(
      application = spec.application,
      name = spec.name,
      parameterConfig = spec.parameters,
      triggers = spec.triggers.map { concreteTrigger ->
        objectMapper.convertValue<MutableMap<String, Any?>>(concreteTrigger).also { trigger ->
          trigger["type"] = trigger["kind"]
          trigger.remove("kind")
        }
      },
      notifications = spec.notifications,
      stages = spec.stages.map {
        it.toMutableMap().also { stage ->
          stage.put("type", it.kind)
          stage.put("requisiteStageRefIds", it.dependsOn)
          stage.remove("kind")
          stage.remove("dependsOn")
        }
      },
      spelEvaluator = spec.properties.spelEvaluator,
      executionEngine = spec.properties.executionEngine,
      limitConcurrent = spec.flags.limitConcurrent,
      keepWaitingPipelines = spec.flags.keepWaitingPipelines,
      id = null,
      index = null,
      stageCounter = null,
      lastModifiedBy = null,
      updateTs = null
    )

  override fun convertFromState(state: PipelineConfig) =
    PipelineSpec(
      application = state.application,
      name = state.name,
      parameters = state.parameterConfig ?: listOf(),
      triggers = state.triggers?.map { triggerMap ->
        triggerMap.toMutableMap().let {
          it["kind"] = it["type"]
          it.remove("type")
          objectMapper.convertValue<Trigger>(it)
        }
      } ?: listOf(),
      notifications = state.notifications ?: listOf(),
      stages = state.stages.map { rawStage ->
        objectMapper.convertValue<PipelineStage>(rawStage).also {
          // TODO rz - This mapping feels kinda funky
          it["kind"] = it["type"]!!
          it["dependsOn"] = it["requisiteStageRefIds"]!!
          it.remove("requisiteStageRefIds")
          it.remove("type")
        }
      },
      properties = PipelineProperties().apply {
        if (state.spelEvaluator != null) {
          put("spelEvaluator", state.spelEvaluator!!)
        }
        if (state.executionEngine != null) {
          put("executionEngine", state.executionEngine!!)
        }
      },
      flags = PipelineFlags().apply {
        if (state.limitConcurrent != null) {
          put("limitConcurrent", state.limitConcurrent!!)
        }
        if (state.keepWaitingPipelines != null) {
          put("keepWaitingPipelines", state.keepWaitingPipelines!!)
        }
      }
    )

  override fun convertToJob(spec: PipelineSpec, changeSummary: ChangeSummary) =
    listOf(
      Job(
        type = "savePipeline",
        m = mutableMapOf(
          "pipeline" to Base64.encodeAsString(*objectMapper.writeValueAsBytes(convertToState(spec)))
        )
      )
    )
}
