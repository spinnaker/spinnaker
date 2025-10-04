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

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.orca.api.pipeline.models.ExecutionStatus.RUNNING
import com.netflix.spinnaker.orca.api.pipeline.models.PipelineExecution
import com.netflix.spinnaker.orca.api.pipeline.models.StageExecution
import com.netflix.spinnaker.orca.api.pipeline.models.TaskExecution
import com.netflix.spinnaker.orca.exceptions.ExceptionHandler
import com.netflix.spinnaker.orca.ext.parent
import com.netflix.spinnaker.orca.jackson.OrcaObjectMapper
import com.netflix.spinnaker.orca.pipeline.model.StageExecutionImpl
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionNotFoundException
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository.ExecutionCriteria
import com.netflix.spinnaker.orca.q.CompleteExecution
import com.netflix.spinnaker.orca.q.ContinueParentStage
import com.netflix.spinnaker.orca.q.ExecutionLevel
import com.netflix.spinnaker.orca.q.InvalidExecutionId
import com.netflix.spinnaker.orca.q.InvalidStageId
import com.netflix.spinnaker.orca.q.InvalidTaskId
import com.netflix.spinnaker.orca.q.StageLevel
import com.netflix.spinnaker.orca.q.StartStage
import com.netflix.spinnaker.orca.q.TaskLevel
import com.netflix.spinnaker.q.Message
import com.netflix.spinnaker.q.MessageHandler
import java.time.Duration
import org.slf4j.Logger
import org.slf4j.LoggerFactory

internal interface OrcaMessageHandler<M : Message> : MessageHandler<M> {
  companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
    val mapper: ObjectMapper = OrcaObjectMapper.getInstance()
    val MIN_PAGE_SIZE = 2
  }

  val repository: ExecutionRepository

  fun Collection<ExceptionHandler>.shouldRetry(ex: Exception, taskName: String?): ExceptionHandler.Response? {
    val exceptionHandler = find { it.handles(ex) }
    return exceptionHandler?.handle(taskName ?: "unspecified", ex)
  }

  fun TaskLevel.withTask(block: (StageExecution, TaskExecution) -> Unit) =
    withStage { stage ->
      stage
        .taskById(taskId)
        .let { task ->
          if (task == null) {
            log.error("InvalidTaskId: Unable to find task {} in stage '{}' while processing message {}", taskId, mapper.writeValueAsString(stage), this)
            queue.push(InvalidTaskId(this))
          } else {
            block.invoke(stage, task)
          }
        }
    }

  fun StageLevel.withStage(block: (StageExecution) -> Unit) =
    withExecution { execution ->
      try {
        execution
          .stageById(stageId)
          .also {
            /**
             * Mutates it.context in a required way (such as removing refId and requisiteRefIds from the
             * context map) for some non-linear stage features.
             */
            StageExecutionImpl(execution, it.type, it.context)
          }
          .let(block)
      } catch (e: IllegalArgumentException) {
        log.error("Failed to locate stage with id: {}", stageId, e)
        queue.push(InvalidStageId(this))
      }
    }

  fun ExecutionLevel.withExecution(block: (PipelineExecution) -> Unit) =
    try {
      val execution = repository.retrieve(executionType, executionId)
      block.invoke(execution)
    } catch (e: ExecutionNotFoundException) {
      queue.push(InvalidExecutionId(this))
    }

  fun StageExecution.startNext() {
    execution.let { execution ->
      val downstreamStages = downstreamStages()
      val phase = syntheticStageOwner
      if (downstreamStages.isNotEmpty()) {
        downstreamStages.forEach {
          queue.push(StartStage(it))
        }
      } else if (phase != null) {
        queue.ensure(ContinueParentStage(parent(), phase), Duration.ZERO)
      } else {
        queue.push(CompleteExecution(execution))
      }
    }
  }

  fun PipelineExecution.shouldQueue(): Boolean {
    val configId = pipelineConfigId
    return when {
      configId == null -> false
      !isLimitConcurrent -> {
        return when {
          maxConcurrentExecutions > 0 -> {
            val criteria = ExecutionCriteria().setPageSize(maxConcurrentExecutions+MIN_PAGE_SIZE).setStatuses(RUNNING)
            repository
              .retrievePipelinesForPipelineConfigId(configId, criteria)
              .filter { it.id != id }
              .count()
              .blockingGet() >= maxConcurrentExecutions
          }
          else -> false
        }
      }
      else -> {
        val criteria = ExecutionCriteria().setPageSize(MIN_PAGE_SIZE).setStatuses(RUNNING)
        repository
          .retrievePipelinesForPipelineConfigId(configId, criteria)
          .filter { it.id != id }
          .count()
          .blockingGet() > 0
      }
    }
  }
}
