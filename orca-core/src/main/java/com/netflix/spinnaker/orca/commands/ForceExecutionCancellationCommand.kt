/*
 * Copyright 2018 Netflix, Inc.
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
package com.netflix.spinnaker.orca.commands

import com.netflix.spinnaker.orca.ExecutionStatus.CANCELED
import com.netflix.spinnaker.orca.ExecutionStatus.NOT_STARTED
import com.netflix.spinnaker.orca.pipeline.model.Execution
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.time.Clock

/**
 * When an [Execution] is zombied and cannot be rehydrated back onto the queue, this
 * command can be used to cleanup.
 *
 * TODO(rz): Fix zombies.
 */
@Component
class ForceExecutionCancellationCommand(
  private val executionRepository: ExecutionRepository,
  private val clock: Clock
) {

  private val log = LoggerFactory.getLogger(javaClass)

  fun forceCancel(executionType: Execution.ExecutionType, executionId: String, canceledBy: String) {
    log.info("Forcing cancel of $executionType:$executionId by: $canceledBy")
    val execution = executionRepository.retrieve(executionType, executionId)

    if (forceCancel(execution, canceledBy)) {
      executionRepository.store(execution)
    }
  }

  private fun forceCancel(execution: Execution, canceledBy: String): Boolean {
    val now = clock.instant().toEpochMilli()

    var changes = false
    execution.stages
      .filter { !it.status.isComplete && it.status != NOT_STARTED }
      .forEach { stage ->
        stage.tasks.forEach { task ->
          task.status = CANCELED
          task.endTime = now
        }
        stage.status = CANCELED
        stage.endTime = now

        changes = true
      }

    if (!execution.status.isComplete) {
      execution.status = CANCELED
      execution.canceledBy = canceledBy
      execution.cancellationReason = "Force canceled by admin"
      execution.endTime = now

      changes = true
    }

    return changes
  }
}
