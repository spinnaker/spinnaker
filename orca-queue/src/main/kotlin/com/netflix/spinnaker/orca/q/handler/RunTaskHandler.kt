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

import com.netflix.spectator.api.Registry
import com.netflix.spectator.api.histogram.BucketCounter
import com.netflix.spinnaker.orca.*
import com.netflix.spinnaker.orca.ExecutionStatus.*
import com.netflix.spinnaker.orca.exceptions.ExceptionHandler
import com.netflix.spinnaker.orca.exceptions.TimeoutException
import com.netflix.spinnaker.orca.pipeline.model.Execution
import com.netflix.spinnaker.orca.pipeline.model.Stage
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository
import com.netflix.spinnaker.orca.pipeline.util.ContextParameterProcessor
import com.netflix.spinnaker.orca.pipeline.util.StageNavigator
import com.netflix.spinnaker.orca.q.*
import com.netflix.spinnaker.orca.time.toDuration
import com.netflix.spinnaker.orca.time.toInstant
import org.apache.commons.lang.time.DurationFormatUtils
import org.springframework.stereotype.Component
import java.time.Clock
import java.time.Duration
import java.time.Duration.ZERO
import java.time.Instant
import java.time.temporal.TemporalAmount
import java.util.concurrent.TimeUnit

@Component
class RunTaskHandler(
  override val queue: Queue,
  override val repository: ExecutionRepository,
  override val stageNavigator: StageNavigator,
  override val contextParameterProcessor: ContextParameterProcessor,
  private val tasks: Collection<Task>,
  private val clock: Clock,
  private val exceptionHandlers: List<ExceptionHandler>,
  private val registry: Registry
) : MessageHandler<RunTask>, ExpressionAware, AuthenticationAware {

  override fun handle(message: RunTask) {
    message.withTask { stage, taskModel, task ->
      val execution = stage.execution
      try {
        if (execution.isCanceled || execution.status.isComplete) {
          queue.push(CompleteTask(message, CANCELED))
        } else if (execution.status == PAUSED) {
          queue.push(PauseTask(message))
        } else {
          task.checkForTimeout(stage, taskModel, message)

          stage.withAuth {
            task.execute(stage.withMergedContext()).let { result: TaskResult ->
              // TODO: rather send this data with CompleteTask message
              stage.processTaskOutput(result)
              when (result.status) {
                RUNNING -> {
                  queue.push(message, task.backoffPeriod(taskModel))
                  trackResult(stage, taskModel, result.status)
                }
                SUCCEEDED, REDIRECT, FAILED_CONTINUE -> {
                  queue.push(CompleteTask(message, result.status))
                  trackResult(stage, taskModel, result.status)
                }
                TERMINAL, CANCELED -> {
                  val status = stage.failureStatus(default = result.status)
                  queue.push(CompleteTask(message, status))
                  trackResult(stage, taskModel, status)
                }
                else ->
                  TODO("Unhandled task status ${result.status}")
              }
            }
          }
        }
      } catch (e: Exception) {
        val exceptionDetails = exceptionHandlers.shouldRetry(e, taskModel.name)
        if (exceptionDetails?.shouldRetry == true) {
          log.warn("Error running ${message.taskType.simpleName} for ${message.executionType}[${message.executionId}]")
          queue.push(message, task.backoffPeriod(taskModel))
          trackResult(stage, taskModel, RUNNING)
        } else if (e is TimeoutException && stage.context["markSuccessfulOnTimeout"] == true) {
          queue.push(CompleteTask(message, SUCCEEDED))
        } else {
          log.error("Error running ${message.taskType.simpleName} for ${message.executionType}[${message.executionId}]", e)
          stage.context["exception"] = exceptionDetails
          repository.storeStage(stage)
          queue.push(CompleteTask(message, stage.failureStatus()))
          trackResult(stage, taskModel, stage.failureStatus())
        }
      }
    }
  }

  private fun trackResult(stage: Stage, taskModel: com.netflix.spinnaker.orca.pipeline.model.Task, status: ExecutionStatus) {
    val id = registry.createId("task.invocations")
      .withTag("status", status.toString())
      .withTag("executionType", stage.execution.type.name.capitalize())
      .withTag("isComplete", status.isComplete.toString())
      .withTag("application", stage.execution.application)
      .let { id ->
        stage.context["cloudProvider"]?.let {
          id.withTag("cloudProvider", it.toString())
        } ?: id
      }
    registry.counter(id).increment()

    val distributionId = registry.createId("task.invocations.duration").withTags(id.tags())
    BucketCounter
      .get(registry, distributionId, { v -> bucketDuration(v) })
      .record(System.currentTimeMillis() - (taskModel.startTime ?: 0))
  }

  fun bucketDuration(duration: Long): String {
    return if (duration > TimeUnit.MINUTES.toMillis(60)) {
      "gt60m"
    } else if (duration > TimeUnit.MINUTES.toMillis(30)) {
      "gt30m"
    } else if (duration > TimeUnit.MINUTES.toMillis(15)) {
      "gt15m"
    } else if (duration > TimeUnit.MINUTES.toMillis(5)) {
      "gt5m"
    } else {
      "lt5m"
    }
  }

  override val messageType = RunTask::class.java

  private fun RunTask.withTask(block: (Stage, com.netflix.spinnaker.orca.pipeline.model.Task, Task) -> Unit) =
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

  private fun Task.backoffPeriod(taskModel: com.netflix.spinnaker.orca.pipeline.model.Task): TemporalAmount =
    when (this) {
      is RetryableTask -> Duration.ofMillis(
        getDynamicBackoffPeriod(Duration.ofMillis(System.currentTimeMillis() - (taskModel.startTime ?: 0)))
      )
      else -> Duration.ofSeconds(1)
    }

  private fun formatTimeout(timeout: Long): String {
    return DurationFormatUtils.formatDurationWords(timeout, true, true)
  }

  private fun Task.checkForTimeout(stage: Stage, taskModel: com.netflix.spinnaker.orca.pipeline.model.Task, message: Message) {
    checkForStageTimeout(stage)
    checkForTaskTimeout(taskModel, stage, message)
  }

  private fun Task.checkForTaskTimeout(taskModel: com.netflix.spinnaker.orca.pipeline.model.Task, stage: Stage, message: Message) {
    if (this is RetryableTask) {
      val startTime = taskModel.startTime.toInstant()
      val pausedDuration = stage.execution.pausedDurationRelativeTo(startTime)
      val elapsedTime = Duration.between(startTime, clock.instant())
      val throttleTime = message.getAttribute<TotalThrottleTimeAttribute>()?.totalThrottleTimeMs ?: 0
      val actualTimeout = (
        if (this is OverridableTimeoutRetryableTask && stage.topLevelTimeout.isPresent)
          stage.topLevelTimeout.get().toDuration()
        else
          timeout.toDuration()
        )
      if (elapsedTime.minus(pausedDuration).minusMillis(throttleTime) > actualTimeout) {
        val durationString = formatTimeout(elapsedTime.toMillis())
        val msg = StringBuilder("${javaClass.simpleName} of stage ${stage.name} timed out after $durationString. ")
        msg.append("pausedDuration: ${formatTimeout(pausedDuration.toMillis())}, ")
        msg.append("throttleTime: ${formatTimeout(throttleTime)}, ")
        msg.append("elapsedTime: ${formatTimeout(elapsedTime.toMillis())},")
        msg.append("timeoutValue: ${formatTimeout(actualTimeout.toMillis())}")

        log.warn(msg.toString())
        throw TimeoutException(msg.toString())
      }
    }
  }

  private fun checkForStageTimeout(stage: Stage) {
    stage.topLevelTimeout.map(Duration::ofMillis).ifPresent({
      val startTime = stage.topLevelStage.startTime.toInstant()
      val elapsedTime = Duration.between(startTime, clock.instant())
      val pausedDuration = stage.execution.pausedDurationRelativeTo(startTime)
      if (elapsedTime.minus(pausedDuration) > it) {
        throw TimeoutException("Stage ${stage.name} timed out after ${formatTimeout(elapsedTime.toMillis())}")
      }
    })
  }


  private fun Execution.pausedDurationRelativeTo(instant: Instant?): Duration {
    val pausedDetails = paused
    return if (pausedDetails != null) {
      if (pausedDetails.pauseTime.toInstant()?.isAfter(instant) == true) {
        Duration.ofMillis(pausedDetails.pausedMs)
      } else ZERO
    } else ZERO
  }

  /**
   * Keys that should never be added to global context. Eventually this will
   * disappear along with global context itself.
   */
  private val blacklistGlobalKeys = setOf(
    "propertyIdList",
    "originalProperties",
    "propertyAction"
  )

  private fun Stage.processTaskOutput(result: TaskResult) {
    val filteredOutputs = result.outputs.filterKeys { it != "stageTimeoutMs" }
    if (result.context.isNotEmpty() || filteredOutputs.isNotEmpty()) {
      context.putAll(result.context)
      outputs.putAll(filteredOutputs)
      repository.storeStage(this)
    }
    filteredOutputs.filterKeys { it !in blacklistGlobalKeys }.let {
      if (it.isNotEmpty()) {
        repository.storeExecutionContext(execution.id, it)
      }
    }
  }
}
