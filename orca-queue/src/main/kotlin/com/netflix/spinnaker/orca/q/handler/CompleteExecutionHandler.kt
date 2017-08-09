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

import com.netflix.spinnaker.orca.ExecutionStatus
import com.netflix.spinnaker.orca.ExecutionStatus.*
import com.netflix.spinnaker.orca.events.ExecutionComplete
import com.netflix.spinnaker.orca.pipeline.model.Execution
import com.netflix.spinnaker.orca.pipeline.model.Stage
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository
import com.netflix.spinnaker.orca.q.*
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Component
import java.time.Duration

@Component
open class CompleteExecutionHandler
@Autowired constructor(
  override val queue: Queue,
  override val repository: ExecutionRepository,
  private val publisher: ApplicationEventPublisher,
  @Value("\${queue.retry.delay.ms:30000}") retryDelayMs: Long
) : MessageHandler<CompleteExecution> {

  private val log = LoggerFactory.getLogger(javaClass)
  private val retryDelay = Duration.ofMillis(retryDelayMs)

  override fun handle(message: CompleteExecution) {
    message.withExecution { execution ->
      if (execution.getStatus().isComplete) {
        log.info("Execution ${execution.getId()} already completed with ${execution.getStatus()} status")
      } else {
        message.determineFinalStatus(execution) { status ->
          repository.updateStatus(message.executionId, status)
          publisher.publishEvent(
            ExecutionComplete(this, message.executionType, message.executionId, status)
          )
          if (status != SUCCEEDED) {
            execution.topLevelStages.filter { it.getStatus() == RUNNING }.forEach {
              queue.push(CancelStage(it))
            }
          }
        }
      }
    }
  }

  private fun CompleteExecution.determineFinalStatus(
    execution: Execution<*>,
    block: (ExecutionStatus) -> Unit
  ) {
    execution.topLevelStages.let { stages ->
      if (stages.map { it.getStatus() }.all { it in setOf(SUCCEEDED, SKIPPED, FAILED_CONTINUE) }) {
        block.invoke(SUCCEEDED)
      } else if (stages.any { it.getStatus() == TERMINAL }) {
        block.invoke(TERMINAL)
      } else if (stages.any { it.getStatus() == CANCELED }) {
        block.invoke(CANCELED)
      } else if (stages.any { it.getStatus() == STOPPED } && !stages.otherBranchesIncomplete()) {
        block.invoke(if (execution.shouldOverrideSuccess()) TERMINAL else SUCCEEDED)
      } else {
        log.warn("Re-queuing $this as the execution is not yet complete")
        queue.push(this, retryDelay)
      }
    }
  }

  private val Execution<*>.topLevelStages
    get(): List<Stage<*>> = getStages().filter { it.getParentStageId() == null }

  private fun Execution<*>.shouldOverrideSuccess(): Boolean =
    getStages()
      .filter { it.getStatus() == STOPPED }
      .any { it.getContext()["completeOtherBranchesThenFail"] == true }

  private fun List<Stage<*>>.otherBranchesIncomplete() =
    any { it.getStatus() == RUNNING } ||
      any { it.getStatus() == NOT_STARTED && it.allUpstreamStagesComplete() }

  override val messageType = CompleteExecution::class.java
}
