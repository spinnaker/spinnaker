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

import com.netflix.spinnaker.orca.ExecutionStatus.CANCELED
import com.netflix.spinnaker.orca.ExecutionStatus.NOT_STARTED
import com.netflix.spinnaker.orca.ExecutionStatus.RUNNING
import com.netflix.spinnaker.orca.events.ExecutionComplete
import com.netflix.spinnaker.orca.events.ExecutionStarted
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository
import com.netflix.spinnaker.orca.q.*
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Component
import net.logstash.logback.argument.StructuredArguments.value

@Component
class StartExecutionHandler(
  override val queue: Queue,
  override val repository: ExecutionRepository,
  private val publisher: ApplicationEventPublisher
) : MessageHandler<StartExecution> {

  override val messageType = StartExecution::class.java

  val log: Logger
    get() = LoggerFactory.getLogger(javaClass)

  override fun handle(message: StartExecution) {
    message.withExecution { execution ->
      if (execution.status == NOT_STARTED && !execution.isCanceled) {
        repository.updateStatus(message.executionId, RUNNING)

        val initialStages = execution.initialStages()
        if (initialStages.isEmpty()) {
          log.warn("No initial stages found (executionId: ${message.executionId})")
        }

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
            " cannot bes started unless state is NOT_STARTED. Ignoring StartExecution message.",
            value("executionId", message.executionId),
            value("application", message.application))
        }
      }
    }
  }
}
