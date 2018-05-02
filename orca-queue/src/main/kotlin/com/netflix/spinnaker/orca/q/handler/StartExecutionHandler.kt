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

package com.netflix.spinnaker.orca.q.handler

import com.netflix.spinnaker.orca.ExecutionStatus.*
import com.netflix.spinnaker.orca.events.ExecutionComplete
import com.netflix.spinnaker.orca.events.ExecutionStarted
import com.netflix.spinnaker.orca.ext.initialStages
import com.netflix.spinnaker.orca.pipeline.model.Execution
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository
import com.netflix.spinnaker.orca.q.CancelExecution
import com.netflix.spinnaker.orca.q.StartExecution
import com.netflix.spinnaker.orca.q.StartStage
import com.netflix.spinnaker.orca.q.pending.PendingExecutionService
import com.netflix.spinnaker.q.Queue
import net.logstash.logback.argument.StructuredArguments.value
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Component
import java.time.Clock
import java.time.Instant

@Component
class StartExecutionHandler(
  override val queue: Queue,
  override val repository: ExecutionRepository,
  private val pendingExecutionService: PendingExecutionService,
  @Qualifier("queueEventPublisher") private val publisher: ApplicationEventPublisher,
  private val clock: Clock
) : OrcaMessageHandler<StartExecution> {

  override val messageType = StartExecution::class.java

  private val log: Logger get() = LoggerFactory.getLogger(javaClass)

  override fun handle(message: StartExecution) {
    message.withExecution { execution ->
      if (execution.status == NOT_STARTED && !execution.isCanceled) {
        if (execution.shouldQueue()) {
          execution.pipelineConfigId?.let {
            log.info("Queueing {} {} {}", execution.application, execution.name, execution.id)
            pendingExecutionService.enqueue(it, message)
          }
        } else {
          start(execution)
        }
      } else {
        terminate(execution)
      }
    }
  }

  private fun start(execution: Execution) {
    if (execution.isAfterStartTimeExpiry()) {
      log.warn("Execution (type ${execution.type}, id {}, application: {}) start was canceled because" +
        "start time would be after defined start time expiry (now: ${clock.millis()}, expiry: ${execution.startTimeExpiry})",
        value("executionId", execution.id),
        value("application", execution.application))
      queue.push(CancelExecution(
        execution.type,
        execution.id,
        execution.application,
        "spinnaker",
        "Could not begin execution before start time expiry"
      ))
    } else {
      val initialStages = execution.initialStages()
      if (initialStages.isEmpty()) {
        log.warn("No initial stages found (executionId: ${execution.id})")
        repository.updateStatus(execution.type, execution.id, TERMINAL)
        publisher.publishEvent(ExecutionComplete(this, execution.type, execution.id, TERMINAL))
      } else {
        repository.updateStatus(execution.type, execution.id, RUNNING)
        initialStages.forEach { queue.push(StartStage(it)) }
        publisher.publishEvent(ExecutionStarted(this, execution.type, execution.id))
      }
    }
  }

  private fun terminate(execution: Execution) {
    if (execution.status == CANCELED || execution.isCanceled) {
      publisher.publishEvent(ExecutionComplete(this, execution.type, execution.id, execution.status))
    } else {
      log.warn("Execution (type: ${execution.type}, id: {}, status: ${execution.status}, application: {})" +
        " cannot be started unless state is NOT_STARTED. Ignoring StartExecution message.",
        value("executionId", execution.id),
        value("application", execution.application))
    }
  }

  private fun Execution.isAfterStartTimeExpiry() =
    startTimeExpiry
      ?.let { Instant.ofEpochMilli(it) }
      ?.isBefore(clock.instant()) ?: false
}
