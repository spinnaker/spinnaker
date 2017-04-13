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

package com.netflix.spinnaker.orca.batch

import com.netflix.spectator.api.Id
import com.netflix.spectator.api.Registry
import com.netflix.spinnaker.orca.*
import com.netflix.spinnaker.orca.batch.exceptions.ExceptionHandler
import com.netflix.spinnaker.orca.pipeline.StageDefinitionBuilder
import com.netflix.spinnaker.orca.pipeline.model.Execution
import com.netflix.spinnaker.orca.pipeline.model.OptionalStageSupport
import com.netflix.spinnaker.orca.pipeline.model.Stage
import com.netflix.spinnaker.orca.pipeline.model.Task as TaskModel
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository
import com.netflix.spinnaker.orca.pipeline.util.ContextParameterProcessor
import com.netflix.spinnaker.orca.pipeline.util.StageNavigator
import com.netflix.spinnaker.security.AuthenticatedRequest
import com.netflix.spinnaker.security.User
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import org.slf4j.LoggerFactory
import org.springframework.batch.core.ExitStatus
import org.springframework.batch.core.StepContribution
import org.springframework.batch.core.scope.context.ChunkContext
import org.springframework.batch.core.step.tasklet.Tasklet
import org.springframework.batch.repeat.RepeatStatus

@Slf4j
@CompileStatic
class TaskTasklet implements Tasklet {
  private static final String METRIC_NAME = "task.invocations"

  private final Task task
  private final ExecutionRepository executionRepository
  private final List<ExceptionHandler> exceptionHandlers
  private final Registry registry
  private final StageNavigator stageNavigator
  private final ContextParameterProcessor contextParameterProcessor

  TaskTasklet(Task task,
              ExecutionRepository executionRepository,
              List<ExceptionHandler> exceptionHandlers,
              Registry registry,
              StageNavigator stageNavigator,
              ContextParameterProcessor contextParameterProcessor) {
    this.task = task
    this.executionRepository = executionRepository
    this.exceptionHandlers = exceptionHandlers
    this.registry = registry
    this.stageNavigator = stageNavigator
    this.contextParameterProcessor = contextParameterProcessor
  }

  Class<? extends Task> getTaskType() {
    task.getClass()
  }

  @Override
  RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) {
    def stage = currentStage(chunkContext)
    def task = currentTask(chunkContext)

    try {
      if (stage.execution.canceled) {
        setStopStatus(chunkContext, ExitStatus.STOPPED, ExecutionStatus.CANCELED)
        contribution.exitStatus = ExitStatus.STOPPED
        return cancel(stage)
      } else if (!OptionalStageSupport.isOptional(stage, contextParameterProcessor) && (task.status.complete || task.status.halt)) {
        // no-op if task is already complete AND stage is NOT optional
        log.warn "Skipping task $task.name because its status is $task.status"
        chunkContext.stepContext.stepExecution.executionContext.put("orcaTaskStatus", task.status)

        if (task.status.halt) {
          setStopStatus(chunkContext, BatchStepStatus.translate(task.status), task.status)
        }

        return RepeatStatus.FINISHED
      } else {
        // fetch the current stage (w/ global context merged in)
        stage = currentStage(chunkContext, true)

        def result = executeTask(stage, chunkContext)
        result = applyStageStatusOverrides(stage, result)

        logResult(result, stage, chunkContext)

        if (result.status.isFailure()) {
          try {
            // ensure that any cleanup behavior is invoked when a task fails (if stage is `Cancellable`)
            cancel(stage, false)
          } catch (Exception e) {
            log.error("Error occurred canceling stage '${stage.id}' in execution '${stage.execution.id}'", e)
          }
        }

        if (result.status == ExecutionStatus.TERMINAL) {
          setStopStatus(chunkContext, ExitStatus.FAILED, result.status)
          // cancel execution which will halt any parallel branches and run
          // cancellation routines
          executionRepository.cancel(stage.execution.id)
        }

        def stageOutputs = new HashMap(result.stageOutputs)
        if (result.status.complete) {
          stageOutputs.put('batch.task.id.' + taskName(chunkContext), chunkContext.stepContext.stepExecution.id)
        }

        storeExecutionResults(new TaskResult(result.status, stageOutputs, result.globalOutputs), stage, chunkContext)

        return taskToBatchStatus(contribution, chunkContext, result.status)
      }
    } finally {
      save(stage, chunkContext)
    }
  }

  private RepeatStatus taskToBatchStatus(StepContribution contribution, ChunkContext chunkContext, ExecutionStatus taskStatus) {
    def batchStepStatus = BatchStepStatus.mapResult(taskStatus)
    chunkContext.stepContext.stepExecution.with {
      executionContext.put("orcaTaskStatus", taskStatus)
      status = batchStepStatus.batchStatus
      jobExecution.status = batchStepStatus.batchStatus
    }
    contribution.exitStatus = batchStepStatus.exitStatus
    return batchStepStatus.repeatStatus
  }

  private
  static TaskResult applyStageStatusOverrides(Stage stage, TaskResult result) {
    if (result.status == ExecutionStatus.TERMINAL) {
      def shouldFailPipeline = (stage.context.failPipeline == null ? true : stage.context.failPipeline) as String
      def terminalStatus = Boolean.valueOf(shouldFailPipeline) ? ExecutionStatus.TERMINAL : ExecutionStatus.STOPPED
      def shouldContinuePipeline = stage.context.continuePipeline == null ? false : stage.context.continuePipeline
      result = new TaskResult(terminalStatus, result.stageOutputs, result.globalOutputs)
      if (shouldContinuePipeline) {
        // ignore failure is selected
        result = new TaskResult(ExecutionStatus.FAILED_CONTINUE, result.stageOutputs, result.globalOutputs)
      }
    }

    return result
  }

  private RepeatStatus cancel(Stage stage, boolean adjustStageStatusAndTasks = true) {
    doCancel(stage, adjustStageStatusAndTasks)
    return RepeatStatus.FINISHED
  }

  protected void doCancel(Stage stage, boolean adjustStageStatusAndTasks) {
    def cancelResults = stage.ancestors({ Stage s, StageDefinitionBuilder stageBuilder ->
      !s.status.complete && stageBuilder instanceof CancellableStage
    }).collect {
      ((CancellableStage) it.stageBuilder).cancel(stage)
    }
    stage.context.cancelResults = cancelResults

    if (adjustStageStatusAndTasks) {
      stage.status = ExecutionStatus.CANCELED
      stage.endTime = System.currentTimeMillis()
      stage.tasks.findAll { !it.status.complete }.each {
        it.status = ExecutionStatus.CANCELED
      }
    }

    log.info("${stage.execution.class.simpleName} ${stage.execution.id} was canceled")
  }

  private void save(Stage stage, ChunkContext chunkContext) {
    executionRepository.storeStage(stage)
    executionRepository.storeExecutionContext(stage.execution.id, chunkContext.stepContext.jobExecutionContext)
  }

  private
  static void setStopStatus(ChunkContext chunkContext, ExitStatus exitStatus, ExecutionStatus executionStatus) {
    chunkContext.stepContext.stepExecution.with {
      executionContext.put("orcaTaskStatus", executionStatus)
      it.exitStatus = exitStatus
    }
  }

  protected TaskResult doExecuteTask(Stage stage, ChunkContext chunkContext) {
    return task.execute(stage)
  }

  private TaskResult executeTask(Stage stage, ChunkContext chunkContext) {
    if (OptionalStageSupport.isOptional(stage, contextParameterProcessor)) {
      return new TaskResult(ExecutionStatus.SKIPPED)
    }

    try {
      // An AuthenticatedStage can override the default pipeline authentication credentials
      def authenticatedUser = stageNavigator.findAll(stage, { Stage ancestorStage, StageDefinitionBuilder stageBuilder ->
        return stageBuilder instanceof AuthenticatedStage
      }).findResult {
        return ((AuthenticatedStage) it.stageBuilder).authenticatedUser(it.stage).orElse(null)
      }

      def currentUser = authenticatedUser ?: new User(
        email: stage.execution?.authentication?.user,
        allowedAccounts: stage.execution?.authentication?.allowedAccounts).asImmutable()

      return AuthenticatedRequest.propagate({
        doExecuteTask(stage, chunkContext)
      }, false, currentUser).call() as TaskResult
    } catch (Exception e) {
      def exceptionHandler = exceptionHandlers.find { it.handles(e) }
      if (!exceptionHandler) {
        throw e
      }

      def taskName = (!stage.tasks.isEmpty() ? stage.tasks[-1].name : null) as String
      def exceptionDetails = exceptionHandler.handle(taskName, e)
      def isRetryable = exceptionDetails.shouldRetry && task instanceof RetryableTask

      return new TaskResult(isRetryable ? ExecutionStatus.RUNNING : ExecutionStatus.TERMINAL, [
        "exception": exceptionDetails
      ])
    }
  }

  private Execution currentExecution(ChunkContext chunkContext) {
    if (chunkContext.stepContext.jobParameters.containsKey("pipeline")) {
      def id = chunkContext.stepContext.jobParameters.pipeline as String
      executionRepository.retrievePipeline(id)
    } else {
      def id = chunkContext.stepContext.jobParameters.orchestration as String
      executionRepository.retrieveOrchestration(id)
    }
  }

  private TaskModel currentTask(ChunkContext chunkContext) {
    def stage = currentStage(chunkContext)
    stage.tasks.find { TaskModel it -> it.id == taskId(chunkContext) }
  }

  private Stage currentStage(ChunkContext chunkContext, boolean includeGlobalContext = false) {
    def execution = currentExecution(chunkContext)
    def stage = execution.stages.find { it.id == stageId(chunkContext) }
    return includeGlobalContext ? ExecutionContextManager.retrieve(stage, chunkContext, contextParameterProcessor) : stage
  }

  private
  static void storeExecutionResults(TaskResult taskResult, Stage stage, ChunkContext chunkContext) {
    stage.context.putAll(taskResult.stageOutputs)
    ExecutionContextManager.store(chunkContext, taskResult)
  }

  private static String stageId(ChunkContext chunkContext) {
    chunkContext.stepContext.stepName.tokenize(".").first()
  }

  private static String taskName(ChunkContext chunkContext) {
    chunkContext.stepContext.stepName.tokenize(".").getAt(2) ?: "Unknown"
  }

  private static String taskId(ChunkContext chunkContext) {
    chunkContext.stepContext.stepName.tokenize(".").getAt(3) ?: "Unknown"
  }

  private void logResult(TaskResult result, Stage stage, ChunkContext chunkContext) {
    Id id = registry.createId(METRIC_NAME)
      .withTag("status", result.status.toString())
      .withTag("executionType", stage.execution.class.simpleName)
      .withTag("stageType", stage.type)
      .withTag("taskName", taskName(chunkContext))
      .withTag("isComplete", result.status.complete ? "true" : "false")

    if (stage.execution.application) {
      id = id.withTag("sourceApplication", stage.execution.application)
    }

    if (stage.context?.cloudProvider) {
      id = id.withTag("cloudProvider", stage.context['cloudProvider'].toString())
    }

    registry.counter(id).increment()

    def taskLogger = LoggerFactory.getLogger(task.class)
    if (result.status.complete || taskLogger.isDebugEnabled()) {
      def executionId = stage.execution.id + (stage.refId ? ":${stage.refId}" : "")
      def outputs = DebugSupport.prettyPrint(result.stageOutputs)
      def ctx = DebugSupport.prettyPrint(stage.context)
      def message = "${stage.execution.class.simpleName}:${executionId} ${taskName(chunkContext)} ${result.status} -- Batch step id: ${chunkContext.stepContext.stepExecution.id},  Task Outputs: ${outputs},  Stage Context: ${ctx}"
      if (result.status.complete) {
        taskLogger.info message
      } else {
        taskLogger.debug message
      }
    }
  }
}

