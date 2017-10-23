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
import com.netflix.spinnaker.keel.Intent
import com.netflix.spinnaker.keel.IntentProcessor
import com.netflix.spinnaker.keel.IntentSpec
import com.netflix.spinnaker.keel.front50.Front50Service
import com.netflix.spinnaker.keel.front50.model.Application
import com.netflix.spinnaker.keel.intents.ANY_MAP_TYPE
import com.netflix.spinnaker.keel.intents.ApplicationIntent
import com.netflix.spinnaker.keel.model.Job
import com.netflix.spinnaker.keel.model.OrchestrationRequest
import com.netflix.spinnaker.keel.tracing.Trace
import com.netflix.spinnaker.keel.tracing.TraceRepository
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import retrofit.RetrofitError
import java.time.Clock

@Component
class ApplicationIntentProcessor
@Autowired constructor(
  private val traceRepository: TraceRepository,
  private val front50Service: Front50Service,
  private val objectMapper: ObjectMapper,
  private val clock: Clock
): IntentProcessor<ApplicationIntent> {

  override fun supports(intent: Intent<IntentSpec>) = intent is ApplicationIntent

  override fun converge(intent: ApplicationIntent): List<OrchestrationRequest> {
    val currentState = getApplication(intent.spec.name)

    traceRepository.record(Trace(
      startingState = if (currentState == null) mapOf() else objectMapper.convertValue(currentState, ANY_MAP_TYPE),
      intent = intent,
      createTs = clock.millis()
    ))

    return listOf(
      OrchestrationRequest(
        name = if (currentState == null) "Create application" else "Update application",
        application = intent.spec.name,
        description = "Converging on desired application state",
        job = listOf(
          Job(
            type = "upsertApplication",
            m = objectMapper.convertValue(intent.spec, ANY_MAP_TYPE)
          )
        )
      )
    )
  }

  private fun getApplication(name: String): Application? {
    try {
      return front50Service.getApplication(name)
    } catch (e: RetrofitError) {
      if (e.response.status == HttpStatus.NOT_FOUND.value()) {
        return null
      }
      throw e
    }
  }
}
