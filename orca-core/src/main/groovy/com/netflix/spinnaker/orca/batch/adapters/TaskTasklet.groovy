/*
 * Copyright 2014 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
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

package com.netflix.spinnaker.orca.batch.adapters

import com.netflix.spectator.api.ExtendedRegistry
import com.netflix.spectator.api.Id
import com.netflix.spinnaker.orca.*
import com.netflix.spinnaker.orca.batch.BatchStepStatus
import com.netflix.spinnaker.orca.batch.ExecutionContextManager
import com.netflix.spinnaker.orca.batch.exceptions.ExceptionHandler
import com.netflix.spinnaker.orca.pipeline.model.Execution
import com.netflix.spinnaker.orca.pipeline.model.OrchestrationStage
import com.netflix.spinnaker.orca.pipeline.model.PipelineStage
import com.netflix.spinnaker.orca.pipeline.model.Stage
import com.netflix.spinnaker.orca.pipeline.model.Task as TaskModel
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository
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
  private final ExtendedRegistry extendedRegistry

  TaskTasklet(Task task,
              ExecutionRepository executionRepository,
              List<ExceptionHandler> exceptionHandlers,
              ExtendedRegistry extendedRegistry) {
    this.task = task
    this.executionRepository = executionRepository
    this.exceptionHandlers = exceptionHandlers
    this.extendedRegistry = extendedRegistry
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
        return cancel(stage)
      } else if (task.status.complete) {
        // no-op
        log.warn "Skipping task $task.name because its status is $task.status"
      } else {
        def result = executeTask(stage, chunkContext)
        logResult(result, stage, chunkContext)

        // we should reload the execution now, in case it has been affected
        // by a parallel process
        long scheduledTime = stage.scheduledTime
        stage = currentStage(chunkContext)
        // Setting the scheduledTime if it has been set by the task
        stage.scheduledTime = scheduledTime

        if (result.status == ExecutionStatus.TERMINAL) {
          setStopStatus(chunkContext, ExitStatus.FAILED, result.status)
        }

        def stageOutputs = new HashMap(result.stageOutputs)
        if (result.status.complete) {
          stageOutputs.put('batch.task.id.' + taskName(chunkContext), chunkContext.stepContext.stepExecution.id)
        }

        storeExecutionResults(new DefaultTaskResult(result.status, stageOutputs, result.globalOutputs), stage,
                              chunkContext)

        def batchStepStatus = BatchStepStatus.mapResult(result)
        chunkContext.stepContext.stepExecution.with {
          executionContext.put("orcaTaskStatus", result.status)
          status = batchStepStatus.batchStatus
          jobExecution.status = batchStepStatus.batchStatus
        }
        contribution.exitStatus = batchStepStatus.exitStatus
        stage.endTime = !batchStepStatus.repeatStatus.continuable ? System.currentTimeMillis() : null

        return batchStepStatus.repeatStatus
      }
    } finally {
      save(stage)
    }
  }

  private RepeatStatus cancel(Stage stage) {
    if (task instanceof CancellableTask) {
      CancellableTask cancellableTask = (CancellableTask) task
      cancellableTask.cancel(stage)
    }
    stage.status = ExecutionStatus.CANCELED
    stage.endTime = System.currentTimeMillis()
    stage.tasks.findAll { !it.status.complete }.each { it.status = ExecutionStatus.CANCELED }
    return BatchStepStatus.mapResult(new DefaultTaskResult(ExecutionStatus.CANCELED)).repeatStatus
  }

  private void save(Stage stage) {
    if (stage.self instanceof OrchestrationStage) {
      executionRepository.storeStage(stage.self as OrchestrationStage)
    } else if (stage.self instanceof PipelineStage) {
      executionRepository.storeStage(stage.self as PipelineStage)
    }
  }

  private static void setStopStatus(ChunkContext chunkContext, ExitStatus exitStatus, ExecutionStatus executionStatus) {
    chunkContext.stepContext.stepExecution.with {
      setTerminateOnly()
      executionContext.put("orcaTaskStatus", executionStatus)
      it.exitStatus = exitStatus
    }
  }

  protected TaskResult doExecuteTask(Stage stage, ChunkContext chunkContext) {
    return task.execute(stage)
  }

  private TaskResult executeTask(Stage stage, ChunkContext chunkContext) {
    try {
      def currentUser = new User(
        stage.execution?.authentication?.user, null, null, [], stage.execution?.authentication?.allowedAccounts
      )
      return AuthenticatedRequest.propagate({
                                              doExecuteTask(stage.asImmutable(), chunkContext)
                                            }, false, currentUser).call() as TaskResult
    } catch (Exception e) {
      def exceptionHandler = exceptionHandlers.find { it.handles(e) }
      if (!exceptionHandler) {
        throw e
      }

      def taskName = (!stage.tasks.isEmpty() ? stage.tasks[-1].name : null) as String
      def exceptionDetails = exceptionHandler.handle(taskName, e)
      def isRetryable = exceptionDetails.shouldRetry && task instanceof RetryableTask
      return new DefaultTaskResult(isRetryable ? ExecutionStatus.RUNNING : ExecutionStatus.TERMINAL, [
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

  private Stage currentStage(ChunkContext chunkContext) {
    def execution = currentExecution(chunkContext)
    def stage = execution.stages.find { it.id == stageId(chunkContext) }
    return ExecutionContextManager.retrieve(stage, chunkContext)
  }

  private static void storeExecutionResults(TaskResult taskResult, Stage stage, ChunkContext chunkContext) {
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
    Id id = extendedRegistry.createId(METRIC_NAME)
                            .withTag("status", result.status.toString())
                            .withTag("executionType", stage.execution.class.simpleName)
                            .withTag("stageType", stage.type)
                            .withTag("taskName", taskName(chunkContext))
                            .withTag("isComplete", result.status.complete ? "true" : "false")

    extendedRegistry.counter(id).increment()

    def taskLogger = LoggerFactory.getLogger(task.class)
    if (result.status.complete || taskLogger.isDebugEnabled()) {
      def executionId = stage.execution.id + (stage.refId ? ":${stage.refId}" : "")
      def message = "${stage.execution.class.simpleName}:${executionId} ${taskName(chunkContext)} ${result.status} -- Batch step id: ${chunkContext.stepContext.stepExecution.id},  Task Outputs: ${result.outputs},  Stage Context: ${stage.context}"
      if (result.status.complete) {
        taskLogger.info message
      } else {
        taskLogger.debug message
      }
    }
  }
}

