package com.netflix.spinnaker.orca.echo.spring

import groovy.transform.CompileStatic
import com.netflix.spinnaker.orca.echo.EchoService
import com.netflix.spinnaker.orca.pipeline.model.Execution
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository
import org.springframework.batch.core.ExitStatus
import org.springframework.batch.core.JobExecution
import org.springframework.batch.core.JobExecutionListener

@CompileStatic
class EchoNotifyingPipelineExecutionListener implements JobExecutionListener {

  protected final ExecutionRepository executionRepository
  private final EchoService echoService

  EchoNotifyingPipelineExecutionListener(ExecutionRepository executionRepository, EchoService echoService) {
    this.executionRepository = executionRepository
    this.echoService = echoService
  }

  @Override
  void beforeJob(JobExecution jobExecution) {
    def execution = currentExecution(jobExecution)
    echoService.recordEvent(
        details: [
            source     : "orca",
            type       : "orca:pipeline:starting",
            application: execution.application,
        ],
        content: [
            context    : execution.stages*.context,
            executionId: execution.id
        ]
    )
  }

  @Override
  void afterJob(JobExecution jobExecution) {
    def execution = currentExecution(jobExecution)
    echoService.recordEvent(
        details: [
            source     : "orca",
            type       : "orca:pipeline:${(wasSuccessful(jobExecution) ? "complete" : "failed")}".toString(),
            application: execution.application,
        ],
        content: [
            context    : execution.stages*.context,
            executionId: execution.id
        ]
    )
  }

  // TODO: this is dupe of method in StageExecutionListener
  protected final Execution currentExecution(JobExecution jobExecution) {
    if (jobExecution.jobParameters.parameters.containsKey("pipeline")) {
      String id = jobExecution.jobParameters.getString("pipeline")
      executionRepository.retrievePipeline(id)
    } else {
      String id = jobExecution.jobParameters.getString("orchestration")
      executionRepository.retrieveOrchestration(id)
    }
  }

  /**
   * Determines if the step was a success (from an Orca perspective). Note that
   * even if the Orca task failed we'll get a `jobExecution.status` of
   * `COMPLETED` as the error was handled.
   */
  private static boolean wasSuccessful(JobExecution jobExecution) {
    jobExecution.exitStatus.exitCode == ExitStatus.COMPLETED.exitCode
  }
}
