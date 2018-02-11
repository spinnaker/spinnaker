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
package com.netflix.spinnaker.keel.intent.processor

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.keel.ConvergeReason
import com.netflix.spinnaker.keel.ConvergeResult
import com.netflix.spinnaker.keel.Intent
import com.netflix.spinnaker.keel.IntentProcessor
import com.netflix.spinnaker.keel.IntentSpec
import com.netflix.spinnaker.keel.dryrun.ChangeSummary
import com.netflix.spinnaker.keel.dryrun.ChangeType
import com.netflix.spinnaker.keel.front50.Front50Service
import com.netflix.spinnaker.keel.front50.model.PipelineConfig
import com.netflix.spinnaker.keel.intent.ANY_MAP_TYPE
import com.netflix.spinnaker.keel.intent.PipelineIntent
import com.netflix.spinnaker.keel.intent.PipelineSpec
import com.netflix.spinnaker.keel.intent.notFound
import com.netflix.spinnaker.keel.intent.processor.converter.PipelineConverter
import com.netflix.spinnaker.keel.model.OrchestrationRequest
import com.netflix.spinnaker.keel.model.OrchestrationTrigger
import com.netflix.spinnaker.keel.state.StateInspector
import com.netflix.spinnaker.keel.tracing.Trace
import com.netflix.spinnaker.keel.tracing.TraceRepository
import org.springframework.stereotype.Component
import retrofit.RetrofitError

@Component
class PipelineIntentProcessor(
  private val traceRepository: TraceRepository,
  private val front50Service: Front50Service,
  private val objectMapper: ObjectMapper,
  private val pipelineConverter: PipelineConverter
): IntentProcessor<PipelineIntent> {

  override fun supports(intent: Intent<IntentSpec>) = intent is PipelineIntent

  override fun converge(intent: PipelineIntent): ConvergeResult {
    val changeSummary = ChangeSummary(intent.id())

    val currentState = getPipelineConfig(intent.spec.application, intent.spec.name)

    if (currentStateUpToDate(intent.id(), currentState, intent.spec, changeSummary)) {
      changeSummary.addMessage(ConvergeReason.UNCHANGED.reason)
      return ConvergeResult(listOf(), changeSummary)
    }

    if (missingApplication(intent.spec.application)) {
      changeSummary.addMessage("The application this pipeline is meant for is missing: ${intent.spec.application}")
      changeSummary.type = ChangeType.FAILED_PRECONDITIONS
      return ConvergeResult(listOf(), changeSummary)
    }

    changeSummary.type = if (currentState == null) ChangeType.CREATE else ChangeType.UPDATE

    traceRepository.record(Trace(
      startingState = if (currentState == null) mapOf() else objectMapper.convertValue(currentState, ANY_MAP_TYPE),
      intent = intent
    ))

    return ConvergeResult(listOf(
      OrchestrationRequest(
        name = (if (currentState == null) "Create" else "Update") + " pipeline '${intent.spec.name}'",
        application = intent.spec.application,
        description = "Converging on desired pipeline state",
        job = pipelineConverter.convertToJob(intent.spec, changeSummary),
        trigger = OrchestrationTrigger(intent.id())
      )
    ), changeSummary)
  }

  private fun currentStateUpToDate(intentId: String,
                                   currentState: PipelineConfig?,
                                   desiredState: PipelineSpec,
                                   changeSummary: ChangeSummary): Boolean {
    val desired = pipelineConverter.convertToState(desiredState)

    if (currentState == null) return false
    val diff = StateInspector(objectMapper).run {
      getDiff(
        intentId = intentId,
        currentState = currentState,
        desiredState = desired,
        modelClass = PipelineConfig::class,
        specClass = PipelineSpec::class
      )
    }
    changeSummary.diff = diff
    return diff.isEmpty()
  }

  private fun getPipelineConfig(application: String, name: String): PipelineConfig? {
    try {
      return front50Service.getPipelineConfigs(application).firstOrNull { it.name == name }
    } catch (e: RetrofitError) {
      if (e.notFound()) {
        return null
      }
      throw e
    }
  }

  private fun missingApplication(application: String): Boolean {
    try {
      front50Service.getApplication(application)
      return false
    } catch (e: RetrofitError) {
      if (e.notFound()) {
        return true
      }
      throw e
    }
  }
}
