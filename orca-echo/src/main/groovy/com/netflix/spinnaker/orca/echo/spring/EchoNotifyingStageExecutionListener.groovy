package com.netflix.spinnaker.orca.echo.spring

import com.netflix.spinnaker.orca.ExecutionStatus
import com.netflix.spinnaker.orca.batch.StageExecutionListener
import com.netflix.spinnaker.orca.echo.EchoService
import com.netflix.spinnaker.orca.pipeline.model.Orchestration
import com.netflix.spinnaker.orca.pipeline.model.Pipeline
import com.netflix.spinnaker.orca.pipeline.model.Stage
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import org.springframework.batch.core.BatchStatus
import org.springframework.batch.core.ExitStatus
import org.springframework.batch.core.StepExecution
import org.springframework.beans.factory.annotation.Autowired

/**
 * Converts step execution events to Echo events.
 */
@CompileStatic
@Slf4j
class EchoNotifyingStageExecutionListener extends StageExecutionListener {
  public static final Set<ExecutionStatus> SUPPRESSED_EXECUTION_STATUSES = [
    ExecutionStatus.CANCELED,
    ExecutionStatus.SKIPPED
  ] as Set<ExecutionStatus>

  private final EchoService echoService

  @Autowired
  EchoNotifyingStageExecutionListener(ExecutionRepository executionRepository, EchoService echoService) {
    super(executionRepository)
    this.echoService = echoService
  }

  @Override
  void beforeTask(Stage stage, StepExecution stepExecution) {
    if (stepExecution.status == BatchStatus.STARTED) {
      recordEvent('task', 'starting', stage, stepExecution)
    }
  }

  void afterTask(Stage stage, StepExecution stepExecution) {
    if (!shouldRecordEvent(stepExecution)) {
      return
    }
    recordEvent('task', (wasSuccessful(stepExecution) ? "complete" : "failed"), stage, stepExecution)
    if (stage.execution instanceof Pipeline) {
      if (wasSuccessful(stepExecution)) {
        if (stepExecution.stepName.contains('.stageEnd.')) {
          recordEvent('stage', 'complete', stage, stepExecution)
        } else if (stepExecution.stepName.contains('.stageStart.')) {
          recordEvent('stage', 'starting', stage, stepExecution)
        }
      } else {
        recordEvent('stage', 'failed', stage, stepExecution)
      }
    }
  }

  private void recordEvent(String type, String phase, Stage stage, StepExecution stepExecution) {
    try {
      echoService.recordEvent(
        details: [
          source     : "orca",
          type       : "orca:${type}:${phase}".toString(),
          application: stage.execution.application
        ], content: [
        standalone : stage.execution instanceof Orchestration,
        canceled   : stage.execution.canceled,
        context    : stage.context,
        taskName   : stepExecution.stepName,
        startTime  : stage.startTime,
        endTime    : stage.endTime,
        execution  : stage.execution,
        executionId: stage.execution.id
      ]
      )
    } catch (Exception e) {
      log.error("Failed to send ${type} event ${phase} ${stage.execution.id} ${stepExecution.stepName}")
    }
  }

  /**
   * Determines if the step was a success (from an Orca perspective). Note that
   * even if the Orca task failed we'll get a `stepExecution.status` of
   * `COMPLETED` as the error was handled.
   */
  private static boolean wasSuccessful(StepExecution stepExecution) {
    ExecutionStatus orcaTaskStatus = (ExecutionStatus) stepExecution.executionContext.get("orcaTaskStatus")
    stepExecution.exitStatus.exitCode == ExitStatus.COMPLETED.exitCode || orcaTaskStatus?.isSuccessful()
  }

  /**
   * Determines if an event should be recorded for this execution.
   */
  private static boolean shouldRecordEvent(StepExecution stepExecution) {
    if (stepExecution.status.running) {
      return false
    }

    ExecutionStatus orcaTaskStatus = (ExecutionStatus) stepExecution.executionContext.get("orcaTaskStatus")
    return (!SUPPRESSED_EXECUTION_STATUSES.contains(orcaTaskStatus))
  }
}
