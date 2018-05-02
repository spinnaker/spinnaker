/*
 * Copyright 2018 Netflix, Inc.
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

import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository
import com.netflix.spinnaker.orca.q.*
import com.netflix.spinnaker.orca.q.pending.PendingExecutionService
import com.netflix.spinnaker.q.Queue
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@Component
class StartWaitingExecutionsHandler(
  override val queue: Queue,
  override val repository: ExecutionRepository,
  private val pendingExecutionService: PendingExecutionService
) : OrcaMessageHandler<StartWaitingExecutions> {

  private val log: Logger get() = LoggerFactory.getLogger(javaClass)

  override val messageType = StartWaitingExecutions::class.java

  override fun handle(message: StartWaitingExecutions) {
    if (message.purgeQueue) {
      // when purging the queue, run the latest message and discard the rest
      pendingExecutionService.popNewest(message.pipelineConfigId)
        .also { _ ->
          pendingExecutionService.purge(message.pipelineConfigId) { purgedMessage ->
            when (purgedMessage) {
              is StartExecution -> {
                log.info("Dropping queued pipeline {} {}", purgedMessage.application, purgedMessage.executionId)
                queue.push(CancelExecution(purgedMessage))
              }
              is RestartStage -> {
                log.info("Cancelling restart of {} {}", purgedMessage.application, purgedMessage.executionId)
                // don't need to do anything else
              }
            }
          }
        }
    } else {
      // when not purging the queue, run the messages in the order they came in
      pendingExecutionService.popOldest(message.pipelineConfigId)
    }
      ?.let {
        // spoiler, it always is!
        if (it is ExecutionLevel) {
          log.info("Starting queued pipeline {} {} {}", it.application, it.executionId)
        }
        queue.push(it)
      }
  }
}
