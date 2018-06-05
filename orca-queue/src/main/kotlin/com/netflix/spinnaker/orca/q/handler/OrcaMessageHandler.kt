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

import com.netflix.spinnaker.orca.ExecutionStatus.RUNNING
import com.netflix.spinnaker.orca.exceptions.ExceptionHandler
import com.netflix.spinnaker.orca.ext.parent
import com.netflix.spinnaker.orca.pipeline.model.Execution
import com.netflix.spinnaker.orca.pipeline.model.Stage
import com.netflix.spinnaker.orca.pipeline.model.Task
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionNotFoundException
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository.ExecutionCriteria
import com.netflix.spinnaker.orca.q.*
import com.netflix.spinnaker.q.Message
import com.netflix.spinnaker.q.MessageHandler

internal interface OrcaMessageHandler<M : Message> : MessageHandler<M> {
  val repository: ExecutionRepository

  fun Collection<ExceptionHandler>.shouldRetry(ex: Exception, taskName: String?): ExceptionHandler.Response? {
    val exceptionHandler = find { it.handles(ex) }
    return exceptionHandler?.handle(taskName ?: "unspecified", ex)
  }

  fun TaskLevel.withTask(block: (Stage, Task) -> Unit) =
    withStage { stage ->
      stage
        .taskById(taskId)
        .let { task ->
          if (task == null) {
            queue.push(InvalidTaskId(this))
          } else {
            block.invoke(stage, task)
          }
        }
    }

  fun StageLevel.withStage(block: (Stage) -> Unit) =
    withExecution { execution ->
      try {
        execution
          .stageById(stageId)
          .let(block)
      } catch (e: IllegalArgumentException) {
        queue.push(InvalidStageId(this))
      }
    }

  fun ExecutionLevel.withExecution(block: (Execution) -> Unit) =
    try {
      val execution = repository.retrieve(executionType, executionId)
      block.invoke(execution)
    } catch (e: ExecutionNotFoundException) {
      queue.push(InvalidExecutionId(this))
    }

  fun Stage.startNext() {
    execution.let { execution ->
      val downstreamStages = downstreamStages()
      val phase = syntheticStageOwner
      if (downstreamStages.isNotEmpty()) {
        downstreamStages.forEach {
          queue.push(StartStage(it))
        }
      } else if (phase != null) {
        queue.push(ContinueParentStage(parent(), phase))
      } else {
        queue.push(CompleteExecution(execution))
      }
    }
  }

  fun Execution.shouldQueue(): Boolean {
    val configId = pipelineConfigId
    return when {
      !isLimitConcurrent -> false
      configId == null   -> false
      else               -> {
        val criteria = ExecutionCriteria().setLimit(2).setStatuses(RUNNING)
        repository
          .retrievePipelinesForPipelineConfigId(configId, criteria)
          .filter { it.id != id }
          .count()
          .toBlocking()
          .first() > 0
      }
    }
  }
}
