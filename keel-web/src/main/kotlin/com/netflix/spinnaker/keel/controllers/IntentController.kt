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
package com.netflix.spinnaker.keel.controllers

import com.netflix.spinnaker.keel.IntentActivityRepository
import com.netflix.spinnaker.keel.IntentRepository
import com.netflix.spinnaker.keel.IntentStatus
import com.netflix.spinnaker.keel.dryrun.DryRunIntentLauncher
import com.netflix.spinnaker.keel.model.UpsertIntentRequest
import com.netflix.spinnaker.keel.orca.OrcaIntentLauncher
import com.netflix.spinnaker.keel.tracing.TraceRepository
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.*
import javax.ws.rs.QueryParam

@RestController
@RequestMapping("/intents")
class IntentController
@Autowired constructor(
  private val orcaIntentLauncher: OrcaIntentLauncher,
  private val dryRunIntentLauncher: DryRunIntentLauncher,
  private val intentRepository: IntentRepository,
  private val intentActivityRepository: IntentActivityRepository,
  private val traceRepository: TraceRepository
) {

  @RequestMapping(method = arrayOf(RequestMethod.PUT))
  @ResponseStatus(HttpStatus.ACCEPTED)
  fun upsertIntent(@RequestBody req: UpsertIntentRequest): Any {
    // TODO rz - validate intents
    // TODO rz - calculate graph

    if (req.dryRun) {
      return req.intents.map { dryRunIntentLauncher.launch(it) }
    }

    req.intents.forEach { intent ->
      intentRepository.upsertIntent(intent)
      orcaIntentLauncher.launch(intent).also { result ->
        intentActivityRepository.addOrchestrations(intent.getId(), result.orchestrationIds)
      }
    }

    // TODO rz - what to return here?
    return req
  }

  @RequestMapping(method = arrayOf(RequestMethod.GET))
  fun getIntents(@QueryParam("statuses") statuses: List<IntentStatus>)
    = if (statuses.isEmpty()) intentRepository.getIntents() else intentRepository.getIntents(statuses)

  @RequestMapping(value = "/{id}", method = arrayOf(RequestMethod.GET))
  fun getIntent(@PathVariable("id") id: String) = intentRepository.getIntent(id)

  @RequestMapping(value = "/{id}", method = arrayOf(RequestMethod.DELETE))
  @ResponseStatus(HttpStatus.NO_CONTENT)
  fun deleteIntent(@RequestParam("status", defaultValue = "CANCELED") status: IntentStatus): Nothing = TODO()

  @RequestMapping(value = "/{id}/history", method = arrayOf(RequestMethod.GET))
  fun getIntentHistory(@PathVariable("id") id: String) = intentActivityRepository.getHistory(id)

  @RequestMapping(value = "/{id}/traces", method = arrayOf(RequestMethod.GET))
  fun getIntentTrace(@PathVariable("id") id: String) = traceRepository.getForIntent(id)
}
