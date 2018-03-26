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
package com.netflix.spinnaker.keel.scheduler.handler

import com.netflix.spectator.api.BasicTag
import com.netflix.spectator.api.Registry
import com.netflix.spinnaker.keel.IntentRepository
import com.netflix.spinnaker.keel.IntentStatus.ACTIVE
import com.netflix.spinnaker.keel.IntentStatus.ISOLATED_ACTIVE
import com.netflix.spinnaker.keel.event.BeforeIntentScheduleEvent
import com.netflix.spinnaker.keel.filter.Filter
import com.netflix.spinnaker.keel.scheduler.ScheduleConvergence
import com.netflix.spinnaker.keel.scheduler.ScheduleService
import com.netflix.spinnaker.q.MessageHandler
import com.netflix.spinnaker.q.Queue
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Component

@Component
class ScheduleConvergeHandler
@Autowired constructor(
  override val queue: Queue,
  private val scheduleService: ScheduleService,
  private val intentRepository: IntentRepository,
  private val filters: List<Filter>,
  private val registry: Registry,
  private val applicationEventPublisher: ApplicationEventPublisher
) : MessageHandler<ScheduleConvergence> {

  private val log = LoggerFactory.getLogger(javaClass)

  private val invocations = registry.createId("scheduler.invocations", listOf(BasicTag("type", "convergence")))

  override fun handle(message: ScheduleConvergence) {
    log.info("Scheduling intent convergence work")

    try {
      intentRepository.getIntents(status = listOf(ACTIVE, ISOLATED_ACTIVE))
        .also { log.info("Attempting to schedule ${it.size} active intents") }
        .filter { intent ->
          applicationEventPublisher.publishEvent(BeforeIntentScheduleEvent(intent))
          return@filter filters.all { it.filter(intent) }
        }
        .also { log.info("Scheduling ${it.size} active intents after filters") }
        .forEach {
          scheduleService.converge(it)
        }
      registry.counter(invocations.withTag("result", "success")).increment()
    } catch (e: Exception) {
      log.error("Failed scheduling convergence", e)
      registry.counter(invocations.withTag("result", "failed")).increment()
    }
  }

  override val messageType = ScheduleConvergence::class.java
}
