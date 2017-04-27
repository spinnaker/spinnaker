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

import com.netflix.spinnaker.orca.ExecutionStatus.FAILED_CONTINUE
import com.netflix.spinnaker.orca.ExecutionStatus.TERMINAL
import com.netflix.spinnaker.orca.events.ExecutionComplete
import com.netflix.spinnaker.orca.pipeline.model.Execution
import com.netflix.spinnaker.orca.pipeline.model.Pipeline
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository
import com.netflix.spinnaker.orca.q.CompleteExecution
import com.netflix.spinnaker.orca.q.MessageHandler
import com.netflix.spinnaker.orca.q.Queue
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Component

@Component
open class CompleteExecutionHandler
@Autowired constructor(
  override val queue: Queue,
  override val repository: ExecutionRepository,
  private val publisher: ApplicationEventPublisher
) : MessageHandler<CompleteExecution> {

  override fun handle(message: CompleteExecution) {
    val status = if (message.execution.shouldOverrideSuccess()) {
      TERMINAL
    } else {
      message.status
    }
    repository.updateStatus(message.executionId, status)
    publisher.publishEvent(
      ExecutionComplete(this, message.executionType, message.executionId, status)
    )
  }

  private fun Execution<*>.shouldOverrideSuccess(): Boolean =
    getStages()
      .filter { it.getStatus() == FAILED_CONTINUE }
      .any { it.getContext()["completeOtherBranchesThenFail"] == true }

  private val CompleteExecution.execution
    get(): Execution<*> = when (executionType) {
      Pipeline::class.java -> repository.retrievePipeline(executionId)
      else -> repository.retrieveOrchestration(executionId)
    }

  override val messageType = CompleteExecution::class.java
}
