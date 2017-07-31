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

package com.netflix.spinnaker.orca.q

import com.netflix.spinnaker.orca.exceptions.ExceptionHandler
import com.netflix.spinnaker.orca.pipeline.model.*
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionNotFoundException
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository

/**
 * Implementations handle a single message type from the queue.
 */
interface MessageHandler<M : Message> : (Message) -> Unit {

  val messageType: Class<M>
  val queue: Queue
  val repository: ExecutionRepository

  override fun invoke(message: Message): Unit =
    if (messageType.isAssignableFrom(message.javaClass)) {
      @Suppress("UNCHECKED_CAST")
      handle(message as M)
    } else {
      throw IllegalArgumentException("Unsupported message type ${message.javaClass.simpleName}")
    }

  fun Collection<ExceptionHandler>.shouldRetry(ex: Exception, taskName: String?): ExceptionHandler.Response? {
    val exceptionHandler = find { it.handles(ex) }
    return exceptionHandler?.handle(taskName ?: "unspecified", ex)
  }

  fun handle(message: M): Unit

  fun TaskLevel.withTask(block: (Stage<*>, Task) -> Unit) =
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

  fun StageLevel.withStage(block: (Stage<*>) -> Unit) =
    withExecution { execution ->
      execution
        .stageById(stageId)
        .let { stage ->
          if (stage == null) {
            queue.push(InvalidStageId(this))
          } else {
            block.invoke(stage)
          }
        }
    }

  fun ExecutionLevel.withExecution(block: (Execution<*>) -> Unit) =
    try {
      val execution = when (executionType) {
        Pipeline::class.java ->
          repository.retrievePipeline(executionId)
        Orchestration::class.java ->
          repository.retrieveOrchestration(executionId)
        else ->
          throw IllegalArgumentException("Unknown execution type $executionType")
      }
      block.invoke(execution)
    } catch(e: ExecutionNotFoundException) {
      queue.push(InvalidExecutionId(this))
    }
}
