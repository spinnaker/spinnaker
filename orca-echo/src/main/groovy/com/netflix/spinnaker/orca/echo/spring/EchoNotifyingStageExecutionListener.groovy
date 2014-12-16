package com.netflix.spinnaker.orca.echo.spring

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

  @Override
  void beforeTask(Stage stage, StepExecution stepExecution) {
    try {
      if (stepExecution.status == BatchStatus.STARTED) {
        def execution = stage.execution
        echoService.recordEvent(
          details: [
            source     : "Orca",
            type       : "orca:task:starting",
            application: execution.application
          ],
          content: stage.context
        )
      }
    } catch (e) {
      e.printStackTrace()
    }
  }

  void afterTask(Stage stage, StepExecution stepExecution) {
    try {
      if (stepExecution.status.running) {
        return
      }
      def execution = stage.execution
      echoService.recordEvent(
        details: [
          source     : "Orca",
          type       : "orca:task:${(wasSuccessful(stepExecution) ? "complete" : "failed")}",
          application: execution.application
        ],
        content: stage.context
      )
    } catch (e) {
      e.printStackTrace()
    }
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
