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
package com.netflix.spinnaker.keel.controllers.v1

import com.netflix.spinnaker.keel.Intent
import com.netflix.spinnaker.keel.IntentActivityRepository
import com.netflix.spinnaker.keel.IntentRepository
import com.netflix.spinnaker.keel.IntentSpec
import com.netflix.spinnaker.keel.IntentStatus
import com.netflix.spinnaker.keel.dryrun.DryRunIntentLauncher
import com.netflix.spinnaker.keel.event.AfterIntentDeleteEvent
import com.netflix.spinnaker.keel.event.AfterIntentUpsertEvent
import com.netflix.spinnaker.keel.model.UpsertIntentRequest
import com.netflix.spinnaker.keel.tracing.TraceRepository
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.ApplicationEventPublisher
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestMethod
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import javax.ws.rs.QueryParam

@RestController
@RequestMapping("/v1/intents")
class IntentController
@Autowired constructor(
  private val dryRunIntentLauncher: DryRunIntentLauncher,
  private val intentRepository: IntentRepository,
  private val intentActivityRepository: IntentActivityRepository,
  private val traceRepository: TraceRepository,
  private val applicationEventPublisher: ApplicationEventPublisher
) {

  private val log = LoggerFactory.getLogger(javaClass)

  @RequestMapping(method = arrayOf(RequestMethod.GET))
  fun getIntents(@QueryParam("status") status: Array<IntentStatus>? ): List<Intent<IntentSpec>> {
    status?.let {
      return intentRepository.getIntents(status.toList())
    }
    return intentRepository.getIntents()
  }

  @RequestMapping(value = "/{id}", method = arrayOf(RequestMethod.GET))
  fun getIntent(@PathVariable("id") id: String) = intentRepository.getIntent(id)

  @RequestMapping(value = "", method = arrayOf(RequestMethod.POST))
  @ResponseStatus(HttpStatus.ACCEPTED)
  fun upsertIntent(@RequestBody req: UpsertIntentRequest): Any {
    // TODO rz - validate intents
    // TODO rz - calculate graph

    // TODO rz - config for running immediately
    // TODO rz - add "notes" API property for history/audit

    if (req.dryRun) {
      return req.intents.map { dryRunIntentLauncher.launch(it) }
    }

    val intentList = mutableListOf<UpsertIntentResponse>()

    req.intents.forEach { intent ->
      intentRepository.upsertIntent(intent)
      intentList.add(UpsertIntentResponse(intent.id(), intent.status))
      applicationEventPublisher.publishEvent(AfterIntentUpsertEvent(intent))
    }

    return intentList
  }

  @RequestMapping(value = "/{id}", method = arrayOf(RequestMethod.DELETE))
  @ResponseStatus(HttpStatus.NO_CONTENT)
  fun deleteIntent(@PathVariable("id") id: String, @RequestParam("status", required = false) status: IntentStatus?) {
    if (status != null) {
      // TODO rz - Add updateStatus method to intentRepository
      throw NotImplementedError("soft-deleting intents is not currently supported")
    }
    intentRepository.getIntent(id)
      .takeIf { it != null }
      ?.run {
        intentRepository.deleteIntent(id, true)
        applicationEventPublisher.publishEvent(AfterIntentDeleteEvent(this))
      }
  }

  @RequestMapping(value = "/{id}/history", method = arrayOf(RequestMethod.GET))
  fun getIntentHistory(@PathVariable("id") id: String) = intentActivityRepository.getHistory(id)

  @RequestMapping(value = "/{id}/traces", method = arrayOf(RequestMethod.GET))
  fun getIntentTrace(@PathVariable("id") id: String) = traceRepository.getForIntent(id)
}

data class UpsertIntentResponse(
  val intentId: String,
  val intentStatus: IntentStatus
)
