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

import com.netflix.spinnaker.orca.ExecutionStatus.NOT_STARTED
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository.ExecutionCriteria
import com.netflix.spinnaker.orca.q.CancelExecution
import com.netflix.spinnaker.orca.q.StartExecution
import com.netflix.spinnaker.orca.q.StartWaitingExecutions
import com.netflix.spinnaker.q.Queue
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@Component
class StartWaitingExecutionsHandler(
  override val queue: Queue,
  override val repository: ExecutionRepository
) : OrcaMessageHandler<StartWaitingExecutions> {

  private val log: Logger get() = LoggerFactory.getLogger(javaClass)

  override val messageType = StartWaitingExecutions::class.java

  override fun handle(message: StartWaitingExecutions) {

    val criteria = ExecutionCriteria().setStatuses(NOT_STARTED).setLimit(Int.MAX_VALUE)
    repository
      .retrievePipelinesForPipelineConfigId(message.pipelineConfigId, criteria)
      .toBlocking()
      .toIterable()
      .let { pipelines ->
        if (message.purgeQueue) {
          pipelines
            .maxBy { it.buildTime ?: 0L }
            ?.let { newest ->
              log.info("Starting queued pipeline {} {} {}", newest.application, newest.name, newest.id)
              queue.push(StartExecution(newest))
              pipelines
                .filter { it.id != newest.id }
                .forEach {
                  log.info("Dropping queued pipeline {} {} {}", it.application, it.name, it.id)
                  queue.push(CancelExecution(it))
                }
            }
        } else {
          pipelines
            .minBy { it.buildTime ?: 0L }
            ?.let { oldest ->
              log.info("Starting queued pipeline {} {} {}", oldest.application, oldest.name, oldest.id)
              queue.push(StartExecution(oldest))
            }
        }
      }
  }
}
