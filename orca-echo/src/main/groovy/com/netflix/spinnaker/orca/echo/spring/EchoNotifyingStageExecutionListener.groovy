package com.netflix.spinnaker.orca.echo.spring

import com.netflix.spinnaker.orca.batch.StageExecutionListener
import com.netflix.spinnaker.orca.echo.EchoService
import com.netflix.spinnaker.orca.pipeline.model.Orchestration
import com.netflix.spinnaker.orca.pipeline.model.Stage
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository
import groovy.transform.CompileStatic
import org.springframework.batch.core.BatchStatus
import org.springframework.batch.core.ExitStatus
import org.springframework.batch.core.StepExecution
import org.springframework.beans.factory.annotation.Autowired

import java.util.concurrent.ScheduledThreadPoolExecutor
import java.util.concurrent.TimeUnit

/**
 * Converts step execution events to Echo events.
 */
@CompileStatic
class EchoNotifyingStageExecutionListener extends StageExecutionListener {

  private final EchoService echoService

  final ScheduledThreadPoolExecutor executor = new ScheduledThreadPoolExecutor(25)

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
      recordEvent('starting', stage, stepExecution)
    }
  }

  void afterTask(Stage stage, StepExecution stepExecution) {
    if (stepExecution.status.running) {
      return
    }
    executor.schedule(new Runnable() {
      @Override
      public void run() {
        recordEvent((wasSuccessful(stepExecution) ? "complete" : "failed"), stage, stepExecution)
      }
    }, 250, TimeUnit.MILLISECONDS)
  }

  private void recordEvent(String phase, Stage stage, StepExecution stepExecution) {
    echoService.recordEvent(
      details: [
        source     : "orca",
        type       : "orca:task:${phase}".toString(),
        application: stage.execution.application
      ], content: [
        standalone : stage.execution instanceof Orchestration,
        canceled   : stage.execution.canceled,
        context    : stage.context,
        taskName   : stepExecution.stepName,
        startTime  : stage.startTime,
        endTime    : stage.endTime,
        execution  : stage.execution instanceof Orchestration ?
          executionRepository.retrieveOrchestration(stage.execution.id) :
          executionRepository.retrievePipeline(stage.execution.id)
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
