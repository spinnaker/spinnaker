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

import com.netflix.spectator.api.BasicTag
import com.netflix.spectator.api.Registry
import com.netflix.spinnaker.orca.*
import com.netflix.spinnaker.orca.ExecutionStatus.*
import com.netflix.spinnaker.orca.exceptions.ExceptionHandler
import com.netflix.spinnaker.orca.exceptions.TimeoutException
import com.netflix.spinnaker.orca.ext.beforeStages
import com.netflix.spinnaker.orca.ext.failureStatus
import com.netflix.spinnaker.orca.ext.isManuallySkipped
import com.netflix.spinnaker.orca.pipeline.RestrictExecutionDuringTimeWindow
import com.netflix.spinnaker.orca.pipeline.model.Execution
import com.netflix.spinnaker.orca.pipeline.model.Execution.ExecutionType
import com.netflix.spinnaker.orca.pipeline.model.Stage
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository
import com.netflix.spinnaker.orca.pipeline.util.ContextParameterProcessor
import com.netflix.spinnaker.orca.pipeline.util.StageNavigator
import com.netflix.spinnaker.orca.q.CompleteTask
import com.netflix.spinnaker.orca.q.InvalidTaskType
import com.netflix.spinnaker.orca.q.PauseTask
import com.netflix.spinnaker.orca.q.RunTask
import com.netflix.spinnaker.orca.q.metrics.MetricsTagHelper
import com.netflix.spinnaker.orca.time.toDuration
import com.netflix.spinnaker.orca.time.toInstant
import com.netflix.spinnaker.q.Message
import com.netflix.spinnaker.q.Queue
import org.apache.commons.lang3.time.DurationFormatUtils
import org.slf4j.MDC
import org.springframework.stereotype.Component
import java.time.Clock
import java.time.Duration
import java.time.Duration.ZERO
import java.time.Instant
import java.time.temporal.TemporalAmount
import java.util.concurrent.TimeUnit
import kotlin.collections.set

@Component
class RunTaskHandler(
  override val queue: Queue,
  override val repository: ExecutionRepository,
  override val stageNavigator: StageNavigator,
  override val contextParameterProcessor: ContextParameterProcessor,
  private val tasks: Collection<Task>,
  private val clock: Clock,
  private val exceptionHandlers: List<ExceptionHandler>,
  private val taskExecutionInterceptors: List<TaskExecutionInterceptor>,
  private val registry: Registry
) : OrcaMessageHandler<RunTask>, ExpressionAware, AuthenticationAware {

  override fun handle(message: RunTask) {
    message.withTask { origStage, taskModel, task ->
      var stage = origStage

      val thisInvocationStartTimeMs = clock.millis()
      val execution = stage.execution

      try {
        taskExecutionInterceptors.forEach { t -> stage = t.beforeTaskExecution(task, stage) }

        if (execution.isCanceled) {
          task.onCancel(stage)
          queue.push(CompleteTask(message, CANCELED))
        } else if (execution.status.isComplete) {
          queue.push(CompleteTask(message, CANCELED))
        } else if (execution.status == PAUSED) {
          queue.push(PauseTask(message))
        } else if (stage.isManuallySkipped()) {
          queue.push(CompleteTask(message, SKIPPED))
        } else {
          try {
            task.checkForTimeout(stage, taskModel, message)
          } catch (e: TimeoutException) {
            registry
              .timeoutCounter(stage.execution.type, stage.execution.application, stage.type, taskModel.name)
              .increment()
            task.onTimeout(stage)
            throw e
          }

          stage.withAuth {
            stage.withLoggingContext(taskModel) {
              var taskResult = task.execute(stage.withMergedContext())
              taskExecutionInterceptors.forEach { t -> taskResult = t.afterTaskExecution(task, stage, taskResult) }
              taskResult.let { result: TaskResult ->
                // TODO: rather send this data with CompleteTask message
                stage.processTaskOutput(result)
                when (result.status) {
                  RUNNING -> {
                    queue.push(message, task.backoffPeriod(taskModel, stage))
                    trackResult(stage, thisInvocationStartTimeMs, taskModel, result.status)
                  }
                  SUCCEEDED, REDIRECT, SKIPPED, FAILED_CONTINUE, STOPPED -> {
                    queue.push(CompleteTask(message, result.status))
                    trackResult(stage, thisInvocationStartTimeMs, taskModel, result.status)
                  }
                  CANCELED -> {
                    task.onCancel(stage)
                    val status = stage.failureStatus(default = result.status)
                    queue.push(CompleteTask(message, status, result.status))
                    trackResult(stage, thisInvocationStartTimeMs, taskModel, status)
                  }
                  TERMINAL -> {
                    val status = stage.failureStatus(default = result.status)
                    queue.push(CompleteTask(message, status, result.status))
                    trackResult(stage, thisInvocationStartTimeMs, taskModel, status)
                  }
                  else ->
                    TODO("Unhandled task status ${result.status}")
                }
              }
            }
          }
        }
      } catch (e: Exception) {
        val exceptionDetails = exceptionHandlers.shouldRetry(e, taskModel.name)
        if (exceptionDetails?.shouldRetry == true) {
          log.warn("Error running ${message.taskType.simpleName} for ${message.executionType}[${message.executionId}]")
          queue.push(message, task.backoffPeriod(taskModel, stage))
          trackResult(stage, thisInvocationStartTimeMs, taskModel, RUNNING)
        } else if (e is TimeoutException && stage.context["markSuccessfulOnTimeout"] == true) {
          queue.push(CompleteTask(message, SUCCEEDED))
        } else {
          log.error("Error running ${message.taskType.simpleName} for ${message.executionType}[${message.executionId}]", e)
          stage.context["exception"] = exceptionDetails
          repository.storeStage(stage)
          queue.push(CompleteTask(message, stage.failureStatus()))
          trackResult(stage, thisInvocationStartTimeMs, taskModel, stage.failureStatus())
        }
      }
    }
  }

  private fun maxBackoff(): Long =
    taskExecutionInterceptors.fold(Long.MAX_VALUE) {
      backoff, interceptor ->
      Math.min(backoff, interceptor.maxTaskBackoff())
    }

  private fun trackResult(stage: Stage, thisInvocationStartTimeMs: Long, taskModel: com.netflix.spinnaker.orca.pipeline.model.Task, status: ExecutionStatus) {
    val commonTags = MetricsTagHelper.commonTags(stage, taskModel, status)
    val detailedTags = MetricsTagHelper.detailedTaskTags(stage, taskModel, status)

    val elapsedMillis = clock.millis() - thisInvocationStartTimeMs

    hashMapOf(
      "task.invocations.duration" to commonTags + BasicTag("application", stage.execution.application),
      "task.invocations.duration.withType" to commonTags + detailedTags
    ).forEach {
      name, tags ->
        registry.timer(name, tags).record(elapsedMillis, TimeUnit.MILLISECONDS)
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

  private fun Task.backoffPeriod(taskModel: com.netflix.spinnaker.orca.pipeline.model.Task, stage: Stage): TemporalAmount =
    when (this) {
      is RetryableTask -> Duration.ofMillis(
        Math.min(getDynamicBackoffPeriod(stage, Duration.ofMillis(System.currentTimeMillis() - (taskModel.startTime
          ?: 0))), maxBackoff())
      )
      else             -> Duration.ofSeconds(1)
    }

  private fun formatTimeout(timeout: Long): String {
    return DurationFormatUtils.formatDurationWords(timeout, true, true)
  }

  private fun Task.checkForTimeout(stage: Stage, taskModel: com.netflix.spinnaker.orca.pipeline.model.Task, message: Message) {
    if (stage.type == RestrictExecutionDuringTimeWindow.TYPE) {
      return
    } else {
      checkForStageTimeout(stage)
      checkForTaskTimeout(taskModel, stage, message)
    }
  }

  private fun Task.checkForTaskTimeout(taskModel: com.netflix.spinnaker.orca.pipeline.model.Task, stage: Stage, message: Message) {
    if (this is RetryableTask) {
      val startTime = taskModel.startTime.toInstant()
      if (startTime != null) {
        val pausedDuration = stage.execution.pausedDurationRelativeTo(startTime)
        val elapsedTime = Duration.between(startTime, clock.instant())
        val actualTimeout = (
          if (this is OverridableTimeoutRetryableTask && stage.parentWithTimeout.isPresent)
            stage.parentWithTimeout.get().timeout.get().toDuration()
          else
            timeout.toDuration()
          )
        if (elapsedTime.minus(pausedDuration) > actualTimeout) {
          val durationString = formatTimeout(elapsedTime.toMillis())
          val msg = StringBuilder("${javaClass.simpleName} of stage ${stage.name} timed out after $durationString. ")
          msg.append("pausedDuration: ${formatTimeout(pausedDuration.toMillis())}, ")
          msg.append("elapsedTime: ${formatTimeout(elapsedTime.toMillis())}, ")
          msg.append("timeoutValue: ${formatTimeout(actualTimeout.toMillis())}")

          log.warn(msg.toString())
          throw TimeoutException(msg.toString())
        }
      }
    }
  }

  private fun checkForStageTimeout(stage: Stage) {
    stage.parentWithTimeout.ifPresent {
      val startTime = it.startTime.toInstant()
      if (startTime != null) {
        val elapsedTime = Duration.between(startTime, clock.instant())
        val pausedDuration = stage.execution.pausedDurationRelativeTo(startTime)
        val executionWindowDuration = stage.executionWindow?.duration ?: ZERO
        val timeout = Duration.ofMillis(it.timeout.get())
        if (elapsedTime.minus(pausedDuration).minus(executionWindowDuration) > timeout) {
          throw TimeoutException("Stage ${stage.name} timed out after ${formatTimeout(elapsedTime.toMillis())}")
        }
      }
    }
  }

  private val Stage.executionWindow: Stage?
    get() = beforeStages()
      .firstOrNull { it.type == RestrictExecutionDuringTimeWindow.TYPE }

  private val Stage.duration: Duration
    get() = run {
      if (startTime == null || endTime == null) {
        throw IllegalStateException("Only valid on completed stages")
      }
      Duration.between(startTime.toInstant(), endTime.toInstant())
    }

  private fun Registry.timeoutCounter(executionType: ExecutionType,
                                      application: String,
                                      stageType: String,
                                      taskType: String) =
    counter(
      createId("queue.task.timeouts")
        .withTags(mapOf(
          "executionType" to executionType.toString(),
          "application" to application,
          "stageType" to stageType,
          "taskType" to taskType
        ))
    )

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
    "propertyAction",
    "propertyAction",
    "deploymentDetails"
  )

  private fun Stage.processTaskOutput(result: TaskResult) {
    val filteredOutputs = result.outputs.filterKeys { it != "stageTimeoutMs" }
    if (result.context.isNotEmpty() || filteredOutputs.isNotEmpty()) {
      context.putAll(result.context)
      outputs.putAll(filteredOutputs)
      repository.storeStage(this)
    }
  }

  private fun Stage.withLoggingContext(taskModel: com.netflix.spinnaker.orca.pipeline.model.Task, block: () -> Unit) {
    try {
      MDC.put("stageType", type)
      MDC.put("taskType", taskModel.implementingClass)

      if (taskModel.startTime != null) {
        MDC.put("taskStartTime", taskModel.startTime.toString())
      }

      block.invoke()
    } finally {
      MDC.remove("stageType")
      MDC.remove("taskType")
      MDC.remove("taskStartTime")
    }
  }
}
