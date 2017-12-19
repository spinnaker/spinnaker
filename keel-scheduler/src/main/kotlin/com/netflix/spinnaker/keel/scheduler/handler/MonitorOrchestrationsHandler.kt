/*
 * Copyright 2017 Netflix, Inc.
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

package com.netflix.spinnaker.keel.scheduler.handler

import com.netflix.spectator.api.Registry
import com.netflix.spinnaker.keel.IntentActivityRepository
import com.netflix.spinnaker.keel.orca.OrcaService
import com.netflix.spinnaker.keel.scheduler.MonitorOrchestrations
import com.netflix.spinnaker.q.MessageHandler
import com.netflix.spinnaker.q.Queue
import net.logstash.logback.argument.StructuredArguments.value
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import java.time.Duration


/**
 * This handler follows up on the launched orchestrations, and records
 * success and failure by kind.
 */
@Component
class MonitorOrchestrationsHandler
@Autowired constructor(
  override val queue: Queue,
  private val intentActivityRepository: IntentActivityRepository,
  private val orcaService: OrcaService,
  private val registry: Registry
) : MessageHandler<MonitorOrchestrations> {

  private val backoffMs = Duration.ofMillis(10000)

  private val log = LoggerFactory.getLogger(javaClass)

  private val orchestrationsStatusId = registry.createId("intent.orchestrations.status")

  override fun handle(message: MonitorOrchestrations) {
    val orchestrationIds = intentActivityRepository.getCurrent(message.intentId)
    orchestrationIds.forEach {
      val orcaExecutionStatus = orcaService.getTask(it).status

      when {
        orcaExecutionStatus.isIncomplete() -> {
          log.debug("orchestration for intent has not completed (intentId: {}, taskId: {})", value("intent", message.intentId), value("task", it))
        }
        orcaExecutionStatus.isSuccess() -> {
          log.debug("orchestration for intent has succeeded (intentId: {}, taskId: {})", value("intent", message.intentId), value("task", it))
          intentActivityRepository.removeCurrent(message.intentId, it)
          registry.counter(orchestrationsStatusId.withTag("kind", message.kind).withTag("status", "success")).increment()
        }
        orcaExecutionStatus.isFailure() -> {
          log.debug("orchestration for intent has failed (intentId: {}, taskId: {})", value("intent", message.intentId), value("task", it))
          intentActivityRepository.removeCurrent(message.intentId, it)
          registry.counter(orchestrationsStatusId.withTag("kind", message.kind).withTag("status", "failure")).increment()
        }
        else -> {
          log.debug("orchestration for intent has unhandled status $orcaExecutionStatus (intentId: {}, taskId: {})", value("intent", message.intentId), value("task", it))
          registry.counter(orchestrationsStatusId.withTag("kind", message.kind).withTag("status", "unhandled").withTag("executionStatus", orcaExecutionStatus.toString())).increment()
        }
      }
    }
    if (intentActivityRepository.getCurrent(message.intentId).isNotEmpty()){
      queue.push(MonitorOrchestrations(message.intentId, message.kind), backoffMs)
    }
  }

  override val messageType = MonitorOrchestrations::class.java

}
