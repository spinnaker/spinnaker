package com.netflix.spinnaker.orca.batch

import com.netflix.spinnaker.orca.pipeline.model.Execution
import com.netflix.spinnaker.orca.pipeline.model.Stage
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository
import groovy.transform.CompileStatic
import org.springframework.batch.core.ExitStatus
import org.springframework.batch.core.StepExecution
import org.springframework.batch.core.listener.StepExecutionListenerSupport

/**
 * A base class for Spring Batch listeners that deal with an Orca stage.
 */
@CompileStatic
abstract class StageExecutionListener extends StepExecutionListenerSupport {

  protected final ExecutionRepository executionRepository

  protected StageExecutionListener(ExecutionRepository executionRepository) {
    this.executionRepository = executionRepository
  }

  void beforeTask(Stage stage, StepExecution stepExecution) {
  }

  void afterTask(Stage stage, StepExecution stepExecution) {
  }

  @Override
  final void beforeStep(StepExecution stepExecution) {
    def stage = currentStage(stepExecution)
    beforeTask(stage, stepExecution)
  }

  @Override
  final ExitStatus afterStep(StepExecution stepExecution) {
    def stage = currentStage(stepExecution)
    afterTask(stage, stepExecution)
    return super.afterStep(stepExecution)
  }

  protected final String stageId(StepExecution stepExecution) {
    stepExecution.stepName.tokenize(".").get(0)
  }

  protected final String taskName(StepExecution stepExecution) {
    stepExecution.stepName.tokenize(".").get(2)
  }

  protected final String taskId(StepExecution stepExecution) {
    stepExecution.stepName.tokenize(".").get(3)
  }

  protected final Execution currentExecution(StepExecution stepExecution) {
    if (stepExecution.jobParameters.parameters.containsKey("pipeline")) {
      String id = stepExecution.jobParameters.getString("pipeline")
      executionRepository.retrievePipeline(id)
    } else {
      String id = stepExecution.jobParameters.getString("orchestration")
      executionRepository.retrieveOrchestration(id)
    }
  }

  protected final Stage currentStage(StepExecution stepExecution) {
    currentExecution(stepExecution).stages.find { it.id == stageId(stepExecution) }
  }
}
