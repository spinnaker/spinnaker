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

import com.netflix.spinnaker.orca.AuthenticatedStage
import com.netflix.spinnaker.orca.ExecutionStatus.*
import com.netflix.spinnaker.orca.RetryableTask
import com.netflix.spinnaker.orca.Task
import com.netflix.spinnaker.orca.TaskResult
import com.netflix.spinnaker.orca.batch.exceptions.ExceptionHandler
import com.netflix.spinnaker.orca.pipeline.model.Execution
import com.netflix.spinnaker.orca.pipeline.model.Pipeline
import com.netflix.spinnaker.orca.pipeline.model.Stage
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository
import com.netflix.spinnaker.orca.pipeline.util.ContextParameterProcessor
import com.netflix.spinnaker.orca.q.*
import com.netflix.spinnaker.orca.time.toDuration
import com.netflix.spinnaker.orca.time.toInstant
import com.netflix.spinnaker.security.AuthenticatedRequest.propagate
import com.netflix.spinnaker.security.User
import org.slf4j.Logger
import org.slf4j.LoggerFactory.getLogger
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import java.time.Clock
import java.time.Duration
import java.time.Duration.ZERO
import java.time.Instant
import java.time.temporal.TemporalAmount

@Component
open class RunTaskHandler
@Autowired constructor(
  override val queue: Queue,
  override val repository: ExecutionRepository,
  private val tasks: Collection<Task>,
  private val clock: Clock,
  private val exceptionHandlers: Collection<ExceptionHandler<in Exception>>,
  private val contextParameterProcessor: ContextParameterProcessor
) : MessageHandler<RunTask> {

  private val log: Logger = getLogger(javaClass)

  override fun handle(message: RunTask) {
    message.withTask { stage, taskModel, task ->
      val execution = stage.getExecution()
      if (execution.isCanceled() || execution.getStatus().isComplete) {
        queue.push(CompleteTask(message, CANCELED))
      } else if (execution.getStatus() == PAUSED) {
        queue.push(PauseTask(message))
      } else if (task.isTimedOut(stage, taskModel)) {
        // TODO: probably want something specific in the execution log
        queue.push(CompleteTask(message, TERMINAL))
      } else {
        try {
          task.executeTask(stage) { result ->
            // TODO: rather send this data with CompleteTask message
            stage.processTaskOutput(result)
            when (result.status) {
              RUNNING ->
                queue.push(message, task.backoffPeriod())
              SUCCEEDED, REDIRECT ->
                queue.push(CompleteTask(message, result.status))
              TERMINAL ->
                if (stage.shouldContinueOnFailure()) {
                  queue.push(CompleteTask(message, FAILED_CONTINUE))
                } else if (!stage.shouldFailPipeline()) {
                  queue.push(CompleteTask(message, STOPPED))
                } else {
                  queue.push(CompleteTask(message, result.status))
                }
              else ->
                TODO("handle other states such as cancellation, suspension, etc.")
            }
          }
        } catch(e: Exception) {
          val exceptionDetails = shouldRetry(e, taskModel)
          if (exceptionDetails?.shouldRetry ?: false) {
            log.warn("Error running ${message.taskType.simpleName} for ${message.executionType.simpleName}[${message.executionId}]")
            queue.push(message, task.backoffPeriod())
          } else {
            log.error("Error running ${message.taskType.simpleName} for ${message.executionType.simpleName}[${message.executionId}]", e)
            stage.getContext()["exception"] = exceptionDetails
            repository.storeStage(stage)
            queue.push(CompleteTask(message, TERMINAL))
          }
        }
      }
    }
  }

  private fun Task.executeTask(stage: Stage<*>, function: (TaskResult) -> Unit) {
    // An AuthenticatedStage can override the default pipeline authentication credentials
    val authenticatedUser = stage
      .ancestors()
      .filter { it.stageBuilder is AuthenticatedStage }
      .firstOrNull()
      ?.let { (it.stageBuilder as AuthenticatedStage).authenticatedUser(it.stage).orElse(null) }

    val currentUser = authenticatedUser ?: User().apply {
      email = stage.getExecution().getAuthentication()?.user
      allowedAccounts = stage.getExecution().getAuthentication()?.allowedAccounts
    }

    propagate({
      execute(stage.withMergedContext()).let(function)
    }, false, currentUser).call()
  }

  override val messageType = RunTask::class.java

  private fun RunTask.withTask(block: (Stage<*>, com.netflix.spinnaker.orca.pipeline.model.Task, Task) -> Unit) =
    withTask { stage, taskModel ->
      tasks
        .find { taskType.isAssignableFrom(it.javaClass) }
        .let { task ->
          if (task == null) {
            queue.push(InvalidTaskType(this, taskType.name))
          } else {
            block.invoke(stage, taskModel, task)
          }
        }
    }

  private fun Task.backoffPeriod(): TemporalAmount =
    when (this) {
      is RetryableTask -> Duration.ofMillis(backoffPeriod)
      else -> Duration.ofSeconds(1)
    }

  private fun Task.isTimedOut(stage: Stage<*>, taskModel: com.netflix.spinnaker.orca.pipeline.model.Task): Boolean =
    when (this) {
      is RetryableTask -> {
        val startTime = taskModel.startTime.toInstant()
        val pausedDuration = stage.getExecution().pausedDurationRelativeTo(startTime)
        if (Duration.between(startTime, clock.instant()).minus(pausedDuration) > timeoutDuration(stage)) {
          log.warn("${javaClass.simpleName} of stage ${stage.getName()} timed out after ${Duration.between(startTime, clock.instant())}")
          true
        } else {
          false
        }
      }
      else -> false
    }

  private fun RetryableTask.timeoutDuration(stage: Stage<*>): Duration {
    val durationOverride = (stage.getContext()["stageTimeoutMs"] as Number?)?.toInt()
    return durationOverride?.toLong()?.toDuration() ?: timeout.toDuration()
  }


  private fun Execution<*>.pausedDurationRelativeTo(instant: Instant?): Duration {
    val pausedDetails = getPaused()
    if (pausedDetails != null) {
      return if (pausedDetails.pauseTime.toInstant()?.isAfter(instant) ?: false) {
        Duration.ofMillis(pausedDetails.pausedMs)
      } else ZERO
    } else return ZERO
  }

  private fun Stage<*>.processTaskOutput(result: TaskResult) {
    if (result.stageOutputs.isNotEmpty()) {
      getContext().putAll(result.stageOutputs)
      repository.storeStage(this)
    }
    if (result.globalOutputs.isNotEmpty()) {
      repository.storeExecutionContext(
        getExecution().getId(),
        result.globalOutputs
      )
    }
  }

  private fun Stage<*>.shouldFailPipeline() =
    getContext()["failPipeline"] in listOf(null, true)

  private fun Stage<*>.shouldContinueOnFailure() =
    getContext()["continuePipeline"] == true

  private fun shouldRetry(ex: Exception, task: com.netflix.spinnaker.orca.pipeline.model.Task?): ExceptionHandler.Response? {
    val exceptionHandler = exceptionHandlers.find { it.handles(ex) }
    return exceptionHandler?.handle(task?.name, ex)
  }

  private fun Stage<*>.withMergedContext(): Stage<*> {
    val processed = processEntries(this)
    val execution = getExecution()
    this.setContext(object : MutableMap<String, Any?> by processed {
      override fun get(key: String): Any? {
        if (execution is Pipeline) {
          if (key == "trigger") {
            return execution.trigger
          }

          if (key == "execution") {
            return execution
          }
        }

        val result = processed[key] ?: execution.getContext()[key]

        if (result is String && ContextParameterProcessor.containsExpression(result)) {
          val augmentedContext = processed.augmentContext(execution)
          return contextParameterProcessor.process(mapOf(key to result), augmentedContext, true)[key]
        }

        return result
      }
    })
    return this
  }

  private fun processEntries(stage: Stage<*>) =
    contextParameterProcessor.process(
      stage.getContext(),
      stage.getContext().augmentContext(stage.getExecution()),
      true
    )

  private fun Map<String, Any?>.augmentContext(execution: Execution<*>) =
    if (execution is Pipeline) {
      this + execution.context + mapOf("trigger" to execution.trigger, "execution" to execution)
    } else {
      this
    }
}
