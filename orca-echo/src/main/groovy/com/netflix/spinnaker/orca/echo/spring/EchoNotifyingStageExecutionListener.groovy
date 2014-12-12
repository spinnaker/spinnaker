package com.netflix.spinnaker.orca.echo.spring

import groovy.transform.CompileStatic
import com.netflix.spinnaker.orca.batch.StageExecutionListener
import com.netflix.spinnaker.orca.echo.EchoService
import com.netflix.spinnaker.orca.pipeline.model.Stage
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository
import org.springframework.batch.core.BatchStatus
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
    if (stepExecution.status == BatchStatus.STARTING) {
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
  }

  void afterTask(Stage stage, StepExecution stepExecution) {
    if (stepExecution.status.running) {
      return
    }
    def execution = stage.execution
    echoService.recordEvent(
        details: [
            source     : "Orca",
            type       : "orca:task:${stepExecution.status.unsuccessful ? "failed" : "complete"}",
            application: execution.application
        ],
        content: stage.context
    )
  }
}
