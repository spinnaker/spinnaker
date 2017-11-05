/*
 * Copyright 2017 Netflix, Inc.
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
package com.netflix.spinnaker.keel.intents.processors

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.keel.*
import com.netflix.spinnaker.keel.front50.Front50Service
import com.netflix.spinnaker.keel.front50.model.Application
import com.netflix.spinnaker.keel.intents.ANY_MAP_TYPE
import com.netflix.spinnaker.keel.intents.ApplicationIntent
import com.netflix.spinnaker.keel.intents.BaseApplicationSpec
import com.netflix.spinnaker.keel.model.Job
import com.netflix.spinnaker.keel.model.OrchestrationRequest
import com.netflix.spinnaker.keel.model.Trigger
import com.netflix.spinnaker.keel.tracing.Trace
import com.netflix.spinnaker.keel.tracing.TraceRepository
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import retrofit.RetrofitError

@Component
class ApplicationIntentProcessor
@Autowired constructor(
  private val traceRepository: TraceRepository,
  private val front50Service: Front50Service,
  private val objectMapper: ObjectMapper
): IntentProcessor<ApplicationIntent> {

  private val log = LoggerFactory.getLogger(javaClass)

  override fun supports(intent: Intent<IntentSpec>) = intent is ApplicationIntent

  override fun converge(intent: ApplicationIntent): ConvergeResult {
    log.info("Converging state for ${intent.getId()}")

    val currentState = getApplication(intent.spec.name)

    if (currentStateUpToDate(currentState, intent.spec)) {
      return ConvergeResult(listOf(), ConvergeReason.UNCHANGED.reason)
    }

    traceRepository.record(Trace(
      startingState = if (currentState == null) mapOf() else objectMapper.convertValue(currentState, ANY_MAP_TYPE),
      intent = intent
    ))

    return ConvergeResult(listOf(
        OrchestrationRequest(
          name = if (currentState == null) "Create application" else "Update application",
          application = intent.spec.name,
          description = "Converging on desired application state",
          job = listOf(
            Job(
              type = "upsertApplication",
              m = mutableMapOf(
                "application" to objectMapper.convertValue(intent.spec, ANY_MAP_TYPE)
              )
            )
          ),
          trigger = Trigger(intent.getId())
        )
      ),
      if (currentState == null) "Application does not exist" else "Application has been updated"
    )
  }

  private fun currentStateUpToDate(currentState: Application?, desiredState: BaseApplicationSpec): Boolean {
    log.warn("Current state update check is not implemented")
    return false
  }

  private fun getApplication(name: String): Application? {
    try {
      return front50Service.getApplication(name)
    } catch (e: RetrofitError) {
      if (e.notFound()) {
        return null
      }
      throw e
    }
  }
}
