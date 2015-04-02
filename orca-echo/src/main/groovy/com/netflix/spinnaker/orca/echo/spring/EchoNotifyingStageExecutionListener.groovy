package com.netflix.spinnaker.orca.echo.spring

import com.netflix.spinnaker.orca.pipeline.model.Orchestration
import groovy.transform.CompileStatic
import com.netflix.spinnaker.orca.batch.StageExecutionListener
import com.netflix.spinnaker.orca.echo.EchoService
import com.netflix.spinnaker.orca.pipeline.model.Stage
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository
import org.springframework.batch.core.BatchStatus
import org.springframework.batch.core.ExitStatus
import org.springframework.batch.core.StepExecution
import org.springframework.beans.factory.annotation.Autowired

/**
 * Converts step execution events to Echo events.
 */
@CompileStatic
class EchoNotifyingStageExecutionListener extends StageExecutionListener {

  private final EchoService echoService

  @Autowired
  EchoNotifyingStageExecutionListener(ExecutionRepository executionRepository, EchoService echoService) {
    super(executionRepository)
    this.echoService = echoService
  }

//  @Override
//  void beforeStage(Stage stage, StepExecution stepExecution) {
//    echoService.recordEvent(
//        details: [
//            source     : "Orca",
//            type       : "orca:stage:starting",
//            application: stage.execution.application
//        ],
//        content: stage.context
//    )
//  }
//
//  @Override
//  void afterStage(Stage stage, StepExecution stepExecution) {
//    echoService.recordEvent(
//        details: [
//            source     : "Orca",
//            type       : "orca:stage:${(wasSuccessful(stepExecution) ? "complete" : "failed")}".toString(),
//            application: stage.execution.application
//        ],
//        content: stage.context
//    )
//  }

  @Override
  void beforeTask(Stage stage, StepExecution stepExecution) {
    if (stepExecution.status == BatchStatus.STARTED) {
      echoService.recordEvent(
          details: [
              source     : "orca",
              type       : "orca:task:starting",
              application: stage.execution.application
          ],
          content: [
              standalone : stage.execution instanceof Orchestration,
              context    : stage.context,
              executionId: stage.execution.id,
              taskName   : stepExecution.stepName,
              startTime  : stage.execution.startTime,
              endTime    : stage.execution.endTime
          ]
      )
    }
  }

  void afterTask(Stage stage, StepExecution stepExecution) {
    if (stepExecution.status.running) {
      return
    }
    echoService.recordEvent(
        details: [
            source     : "orca",
            type       : "orca:task:${(wasSuccessful(stepExecution) ? "complete" : "failed")}".toString(),
            application: stage.execution.application
        ], content: [
            standalone : stage.execution instanceof Orchestration,
            context    : stage.context,
            executionId: stage.execution.id,
            taskName   : stepExecution.stepName,
            startTime  : stage.execution.startTime,
            endTime    : stage.execution.endTime,
            canceled   : stage.execution.canceled
        ]
    )
  }

  /**
   * Determines if the step was a success (from an Orca perspective). Note that
   * even if the Orca task failed we'll get a `stepExecution.status` of
   * `COMPLETED` as the error was handled.
   */
  private static boolean wasSuccessful(StepExecution stepExecution) {
    stepExecution.exitStatus.exitCode == ExitStatus.COMPLETED.exitCode
  }
}
