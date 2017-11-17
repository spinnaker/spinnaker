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
import com.netflix.spinnaker.keel.front50.annotations.Computed
import com.netflix.spinnaker.keel.front50.model.Application
import com.netflix.spinnaker.keel.intents.ANY_MAP_TYPE
import com.netflix.spinnaker.keel.intents.ApplicationIntent
import com.netflix.spinnaker.keel.intents.BaseApplicationSpec
import com.netflix.spinnaker.keel.intents.NetflixApplicationSpec
import com.netflix.spinnaker.keel.intents.processors.converters.ApplicationConverter
import com.netflix.spinnaker.keel.model.Job
import com.netflix.spinnaker.keel.model.OrchestrationRequest
import com.netflix.spinnaker.keel.model.Trigger
import com.netflix.spinnaker.keel.tracing.Trace
import com.netflix.spinnaker.keel.tracing.TraceRepository
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import retrofit.RetrofitError
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.full.primaryConstructor

import net.logstash.logback.argument.StructuredArguments.value

@Component
class ApplicationIntentProcessor
@Autowired constructor(
  private val traceRepository: TraceRepository,
  private val front50Service: Front50Service,
  private val objectMapper: ObjectMapper,
  private val applicationConverter: ApplicationConverter
): IntentProcessor<ApplicationIntent> {

  private val log = LoggerFactory.getLogger(javaClass)

  override fun supports(intent: Intent<IntentSpec>) = intent is ApplicationIntent

  override fun converge(intent: ApplicationIntent): ConvergeResult {
    log.info("Converging state for {}", value("intent", intent.id))

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
          trigger = Trigger(intent.id)
        )
      ),
      if (currentState == null) "Application does not exist" else "Application has been updated"
    )
  }

  private fun currentStateUpToDate(currentState: Application?, desiredState: BaseApplicationSpec): Boolean {
    log.info("Determining if state has drifted for application ${desiredState.name}")
    if (currentState == null) return false

    // Some values are computed, and we shouldn't consider them when
    // determining if the current state is equal to the desired state
    val ignoredCurrentStateParameters: MutableList<String> = mutableListOf()
    Application::class.primaryConstructor!!.parameters.filter { param ->
        param.findAnnotation<Computed>()?.ignore == true
      }.mapTo(ignoredCurrentStateParameters) { param ->
        param.name.toString()
      }
    ignoredCurrentStateParameters.addAll(currentState.computedPropertiesToIgnore())

    val ignoredDesiredStateParameters: MutableList<String> = mutableListOf()
    // TODO eb: make this abstract!
    val specPrimaryConstructor = NetflixApplicationSpec::class.primaryConstructor!!
    specPrimaryConstructor.parameters.filter { param ->
      param.findAnnotation<Computed>()?.ignore == true
    }.mapTo(ignoredDesiredStateParameters) { param ->
      param.name.toString()
    }

    // Convert to map to compare because front50 isn't typed
    // TODO eb: make this better
    val currentStateMap = applicationConverter.convertToMap(currentState)
    ignoredCurrentStateParameters.forEach { param ->
      currentStateMap.remove(param)
    }
    // TODO eb: deal with name better, keel should lowercase
    currentStateMap.put("name", currentStateMap["name"].toString().toLowerCase())

    val desiredStateMap = applicationConverter.convertToMap(desiredState)
    ignoredDesiredStateParameters.forEach { param ->
      desiredStateMap.remove(param)
    }
    desiredStateMap.put("name", desiredStateMap["name"].toString().toLowerCase())

    var matching = true
    desiredStateMap.forEach { key, value ->
      val curVal = currentStateMap[key]
      if (curVal != value ) {
        log.debug("$key has drifted from \"$value\" to \"$curVal\" for application ${desiredState.name}")
        matching = false
      }
    }
    return matching
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
