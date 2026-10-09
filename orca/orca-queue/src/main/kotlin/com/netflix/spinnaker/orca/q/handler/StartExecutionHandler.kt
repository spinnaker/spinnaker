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

import com.netflix.spinnaker.orca.api.pipeline.models.ExecutionStatus.CANCELED
import com.netflix.spinnaker.orca.api.pipeline.models.ExecutionStatus.NOT_STARTED
import com.netflix.spinnaker.orca.api.pipeline.models.ExecutionStatus.RUNNING
import com.netflix.spinnaker.orca.api.pipeline.models.ExecutionStatus.TERMINAL
import com.netflix.spinnaker.orca.api.pipeline.models.PipelineExecution
import com.netflix.spinnaker.orca.events.ExecutionComplete
import com.netflix.spinnaker.orca.events.ExecutionStarted
import com.netflix.spinnaker.orca.ext.initialStages
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionNotFoundException
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository
import com.netflix.spinnaker.orca.q.CancelExecution
import com.netflix.spinnaker.orca.q.StartExecution
import com.netflix.spinnaker.orca.q.StartStage
import com.netflix.spinnaker.orca.q.StartWaitingExecutions
import com.netflix.spinnaker.orca.q.pending.PendingExecutionService
import com.netflix.spinnaker.q.Queue
import java.time.Clock
import java.time.Instant
import net.logstash.logback.argument.StructuredArguments.value
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Component

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

  private fun start(execution: PipelineExecution) {
    if (execution.isAfterStartTimeExpiry()) {
      log.warn(
        "Execution (type ${execution.type}, id {}, application: {}) start was canceled because" +
          "start time would be after defined start time expiry (now: ${clock.millis()}, expiry: ${execution.startTimeExpiry})",
        value("executionId", execution.id),
        value("application", execution.application)
      )
      queue.push(
        CancelExecution(
          execution.type,
          execution.id,
          execution.application,
          "spinnaker",
          "Could not begin execution before start time expiry"
        )
      )
    } else {
      val initialStages = execution.initialStages()
      if (initialStages.isEmpty()) {
        log.warn("No initial stages found (executionId: ${execution.id})")
        execution.updateStatus(TERMINAL)
        repository.updateStatus(execution)
        publisher.publishEvent(ExecutionComplete(this, execution))
      } else {
        execution.updateStatus(RUNNING)
        // The same StartExecution message can be delivered to another worker (for example when
        // handling it exceeds the queue's ack timeout). Only move the execution to RUNNING if it
        // is still NOT_STARTED, so a stale copy can't overwrite a newer, possibly terminal, state.
        if (!repository.updateStatusIfExpected(execution, NOT_STARTED)) {
          log.warn(
            "Not starting execution (type ${execution.type}, id {}, application: {}): {}." +
              " Ignoring StartExecution message.",
            value("executionId", execution.id),
            value("application", execution.application),
            describeStoredStatus(execution)
          )
          return
        }
        try {
          initialStages.forEach { queue.push(StartStage(it)) }
        } catch (e: Exception) {
          // Restore to NOT_STARTED so the ack-timeout retried StartExecution
          // message isn't dropped by the status guard above.
          execution.updateStatus(NOT_STARTED)
          repository.updateStatus(execution)
          throw e
        }
        publisher.publishEvent(ExecutionStarted(this, execution))
      }
    }
  }

  private fun terminate(execution: PipelineExecution) {
    if (execution.status == CANCELED || execution.isCanceled) {
      publisher.publishEvent(ExecutionComplete(this, execution))
      execution.pipelineConfigId?.let {
        queue.push(StartWaitingExecutions(it, purgeQueue = !execution.isKeepWaitingPipelines))
      }
    } else {
      log.warn(
        "Execution (type: ${execution.type}, id: {}, status: ${execution.status}, application: {})" +
          " cannot be started unless state is NOT_STARTED. Ignoring StartExecution message.",
        value("executionId", execution.id),
        value("application", execution.application)
      )
    }
  }

  /**
   * Explains why the conditional update to RUNNING was rejected. Only called on that rare path, so
   * the extra read is cheap and makes the log say what actually happened.
   */
  private fun describeStoredStatus(execution: PipelineExecution): String =
    try {
      "its stored status is ${repository.retrieve(execution.type, execution.id, true).status}"
    } catch (e: ExecutionNotFoundException) {
      "it no longer exists"
    } catch (e: Exception) {
      "its stored status is no longer NOT_STARTED (it may have been started by another worker, canceled or deleted)"
    }

  private fun PipelineExecution.isAfterStartTimeExpiry() =
    startTimeExpiry
      ?.let { Instant.ofEpochMilli(it) }
      ?.isBefore(clock.instant()) ?: false
}
