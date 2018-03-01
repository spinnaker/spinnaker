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
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository
import com.netflix.spinnaker.orca.q.StartExecution
import com.netflix.spinnaker.orca.q.StartStage
import com.netflix.spinnaker.q.Queue
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
  @Qualifier("queueEventPublisher") private val publisher: ApplicationEventPublisher
) : OrcaMessageHandler<StartExecution> {

  override val messageType = StartExecution::class.java

  val log: Logger
    get() = LoggerFactory.getLogger(javaClass)

  override fun handle(message: StartExecution) {
    message.withExecution { execution ->
      if (execution.status == NOT_STARTED && !execution.isCanceled) {
        val initialStages = execution.initialStages()
        if (initialStages.isEmpty()) {
          log.warn("No initial stages found (executionId: ${message.executionId})")
          repository.updateStatus(message.executionId, TERMINAL)
          publisher.publishEvent(ExecutionComplete(this, message.executionType, message.executionId, TERMINAL))
          return@withExecution
        }

        repository.updateStatus(message.executionId, RUNNING)
        initialStages
          .forEach {
            queue.push(StartStage(message, it.id))
          }
        publisher.publishEvent(ExecutionStarted(this, message.executionType, message.executionId))
      } else {
        if (execution.status == CANCELED || execution.isCanceled) {
          publisher.publishEvent(ExecutionComplete(this, message.executionType, message.executionId, execution.status))
        } else {
          log.warn("Execution (type: ${message.executionType}, id: {}, status: ${execution.status}, application: {})" +
            " cannot be started unless state is NOT_STARTED. Ignoring StartExecution message.",
            value("executionId", message.executionId),
            value("application", message.application))
        }
      }
    }
  }
}
