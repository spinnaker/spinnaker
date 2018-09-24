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
package com.netflix.spinnaker.orca.q.admin

import com.fasterxml.jackson.annotation.JsonIgnore
import com.netflix.spinnaker.orca.ExecutionStatus.NOT_STARTED
import com.netflix.spinnaker.orca.ExecutionStatus.RUNNING
import com.netflix.spinnaker.orca.RetryableTask
import com.netflix.spinnaker.orca.ext.afterStages
import com.netflix.spinnaker.orca.ext.allAfterStagesSuccessful
import com.netflix.spinnaker.orca.ext.allBeforeStagesSuccessful
import com.netflix.spinnaker.orca.ext.allUpstreamStagesComplete
import com.netflix.spinnaker.orca.ext.beforeStages
import com.netflix.spinnaker.orca.ext.isInitial
import com.netflix.spinnaker.orca.pipeline.model.Execution
import com.netflix.spinnaker.orca.pipeline.model.Stage
import com.netflix.spinnaker.orca.pipeline.model.Task
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionNotFoundException
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository
import com.netflix.spinnaker.orca.q.CompleteExecution
import com.netflix.spinnaker.orca.q.CompleteStage
import com.netflix.spinnaker.orca.q.CompleteTask
import com.netflix.spinnaker.orca.q.RunTask
import com.netflix.spinnaker.orca.q.StartStage
import com.netflix.spinnaker.orca.q.StartTask
import com.netflix.spinnaker.q.Message
import com.netflix.spinnaker.q.Queue
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import rx.Observable
import java.time.Instant
import kotlin.reflect.full.memberProperties

/**
 * Hydrates (best attempt) the queue from current ExecutionRepository state.
 *
 * Does not aim to have an exhaustive coverage of all transitory states of
 * an Execution, but will perform hydration on what it can and report on
 * any Executions skipped. This command will ensure that if any branch of an
 * Execution cannot be hydrated, no parts of the Execution will be.
 */
@Component
class HydrateQueueCommand(
  private val queue: Queue,
  private val executionRepository: ExecutionRepository
) : (HydrateQueueInput) -> HydrateQueueOutput {

  private val log = LoggerFactory.getLogger(javaClass)

  override fun invoke(p1: HydrateQueueInput): HydrateQueueOutput {
    val pInTimeWindow = { execution: Execution -> inTimeWindow(p1, execution) }

    val targets = if (p1.executionId == null) {
      executionRepository
        .retrieveRunning()
        .filter(pInTimeWindow)
        .toList()
        .toBlocking()
        .first()
    } else {
      executionRepository.retrieveSingleRunning(p1.executionId)
        .toList()
        .toBlocking()
        .first()
    }

    val output = HydrateQueueOutput(
      dryRun = p1.dryRun,
      executions = targets.map { it.id to processExecution(it) }.toMap()
    )

    log.info("Hydrating queue from execution repository state (dryRun: ${output.dryRun})")
    output.executions.forEach {
      if (it.value.canApply) {
        log.info("Hydrating execution ${it.key}")
        it.value.actions.forEach { action ->
          // The message is always non-null here
          action.message?.also { message ->
            log.info("Hydrating execution ${it.key} with $message: ${action.fullDescription}")
            if (!output.dryRun) {
              queue.push(message)
            }
          }
        }
      } else {
        val reason = if (it.value.actions.isEmpty()) "could not determine any actions" else "unknown reasons"
        log.warn("Could not hydrate execution ${it.key}: $reason")
        it.value.actions.forEach { action ->
          log.warn("Aborted execution ${it.key} hydration: ${action.fullDescription}")
        }
      }
    }

    return output
  }

  internal fun processExecution(execution: Execution): ProcessedExecution {
    val actions = mutableListOf<Action>()

    execution.stages
      .filter { it.parent == null }
      .forEach { actions.addAll(processStage(it)) }

    val leafStages = execution.stages.filter { it.downstreamStages().isEmpty() }
    if (leafStages.all { it.status.isComplete }) {
      actions.add(Action(
        description = "All leaf stages are complete but execution is still running",
        message = CompleteExecution(execution),
        context = ActionContext()
      ))
    }

    return ProcessedExecution(
      startTime = execution.startTime,
      actions = actions.toList()
    )
  }

  private fun processStage(stage: Stage): List<Action> {
    if (stage.status == NOT_STARTED) {
      if (stage.allUpstreamStagesComplete()) {
        return listOf(Action(
          description = "Stage is not started but all upstream stages are complete",
          message = StartStage(stage),
          context = stage.toActionContext()
        ))
      } else if (stage.isInitial()) {
        return listOf(Action(
          description = "Stage is not started but is an initial stage",
          message = StartStage(stage),
          context = stage.toActionContext()
        ))
      }
    } else if (stage.status == RUNNING) {
      val actions = mutableListOf<Action>()

      val beforeStages = stage.beforeStages()
      beforeStages.forEach { actions.addAll(processStage(it)) }

      if (beforeStages.isEmpty() || beforeStages.all { it.status.isComplete }) {
        val task = stage.tasks.firstOrNull { it.status in listOf(NOT_STARTED, RUNNING) }
        if (task != null) {
          actions.add(processTask(stage, task))
        }
      } else if (stage.tasks.all { it.status.isComplete }) {
        stage.afterStages().forEach { actions.addAll(processStage(it)) }
      }

      if (stage.allBeforeStagesSuccessful() &&
        stage.tasks.all { it.status.isComplete } &&
        stage.allAfterStagesSuccessful()) {
        actions.add(Action(
          description = "All tasks and known synthetic stages are complete",
          message = CompleteStage(stage),
          context = stage.toActionContext()
        ))
      }

      return actions.toList()
    }

    return listOf()
  }

  private fun processTask(stage: Stage, task: Task): Action {
    return if (task.status == RUNNING) {
      if (task.isRetryable()) {
        Action(
          description = "Task is running and is retryable",
          message = RunTask(
            executionType = stage.execution.type,
            executionId = stage.execution.id,
            application = stage.execution.application,
            stageId = stage.id,
            taskId = task.id,
            taskType = task.type
          ),
          context = task.toActionContext(stage)
        )
      } else {
        Action(
          description = "Task is running but is not retryable",
          message = null,
          context = task.toActionContext(stage)
        )
      }
    } else if (task.status == NOT_STARTED) {
      Action(
        description = "Task has not started yet",
        message = StartTask(stage, task),
        context = task.toActionContext(stage)
      )
    } else if (task.status.isComplete) {
      Action(
        description = "Task is complete",
        message = CompleteTask(
          executionType = stage.execution.type,
          executionId = stage.execution.id,
          application = stage.execution.application,
          stageId = stage.id,
          taskId = task.id,
          status = task.status,
          originalStatus = null // We don't know the original status at this point
        ),
        context = task.toActionContext(stage)
      )
    } else {
      Action(
        description = "Could not determine an action to take",
        message = null,
        context = task.toActionContext(stage)
      )
    }
  }

  private fun inTimeWindow(input: HydrateQueueInput, execution: Execution): Boolean =
    execution.startTime
      ?.let { Instant.ofEpochMilli(it) }
      ?.let { !(input.start?.isBefore(it) == true || input.end?.isAfter(it) == true) }
      ?: true

  private fun ExecutionRepository.retrieveRunning(): Observable<Execution> =
    rx.Observable.merge(
      retrieve(Execution.ExecutionType.ORCHESTRATION, ExecutionRepository.ExecutionCriteria().setStatuses(RUNNING)),
      retrieve(Execution.ExecutionType.PIPELINE, ExecutionRepository.ExecutionCriteria().setStatuses(RUNNING))
    )

  private fun ExecutionRepository.retrieveSingleRunning(executionId: String): Observable<Execution> {
    // TODO rz - Ugh. So dumb.
    val execution = try {
      retrieve(Execution.ExecutionType.ORCHESTRATION, executionId)
    } catch (e: ExecutionNotFoundException) {
      try {
        retrieve(Execution.ExecutionType.PIPELINE, executionId)
      } catch (e: ExecutionNotFoundException) {
        null
      }
    }
    return if (execution == null || execution.status != RUNNING) {
      rx.Observable.empty<Execution>()
    } else {
      rx.Observable.just(execution)
    }
  }

  @Suppress("UNCHECKED_CAST")
  private val com.netflix.spinnaker.orca.pipeline.model.Task.type
    get() = Class.forName(implementingClass) as Class<out com.netflix.spinnaker.orca.Task>

  private fun Task.isRetryable(): Boolean =
    RetryableTask::class.java.isAssignableFrom(type)

  private fun Stage.toActionContext() = ActionContext(
    stageId = id,
    stageType = type,
    stageStartTime = startTime
  )

  private fun Task.toActionContext(stage: Stage) = stage.toActionContext().copy(
    taskId = id,
    taskType = name,
    taskStartTime = startTime
  )
}

/**
 * @param executionId Rehydrates a single execution.
 * @param start The lower boundary of execution start time. If provided,
 * executions older than this value will not be hydrated.
 * @param end The upper boundary of execution start time. If provided,
 * executions newer than this value will not be hydrated.
 * @param dryRun If true, output will be provided about what the command
 * would have done.
 */
data class HydrateQueueInput(
  val executionId: String? = null,
  val start: Instant? = null,
  val end: Instant? = null,
  val dryRun: Boolean = true
)

/**
 * @param dryRun Whether or not this output is the result of a dry run or not.
 * @param executions An Execution ID-indexed list of actions taken.
 */
data class HydrateQueueOutput(
  val dryRun: Boolean,
  val executions: Map<String, ProcessedExecution>
)

/**
 * @param startTime The start time of the execution.
 * @param actions A list of human-readable description of an action and an
 * optional associated message.
 */
data class ProcessedExecution(
  val startTime: Long? = null,
  val actions: List<Action>
) {

  /**
   * If there's an action with no message, it means that there was an error
   * or warning condition, so we shouldn't be hydrating the execution.
   */
  val canApply = actions.isNotEmpty() && actions.none { it.message == null }
}

/**
 * @param description A human-readable reason / description of the action
 * @param message The queue Message that is a result of the action
 * @param context Execution context that lead to this action
 */
data class Action(
  val description: String,
  val message: Message?,
  val context: ActionContext
) {
  @JsonIgnore
  val fullDescription = "$description (${context.description})"
}

data class ActionContext(
  val stageId: String? = null,
  val stageType: String? = null,
  val stageStartTime: Long? = null,
  val taskId: String? = null,
  val taskType: String? = null,
  val taskStartTime: Long? = null
) {

  val description: String
    @JsonIgnore
    get() =
      ActionContext::class.memberProperties
        .filter { it.name != "description" && it.get(this) != null }
        .map { it.name + ": " + it.get(this) }
        .joinToString { it }
}
