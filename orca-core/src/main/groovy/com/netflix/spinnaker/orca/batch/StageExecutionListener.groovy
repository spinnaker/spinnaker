package com.netflix.spinnaker.orca.batch

import groovy.transform.CompileStatic
import java.util.regex.Pattern
import com.netflix.spinnaker.orca.pipeline.model.Execution
import com.netflix.spinnaker.orca.pipeline.model.Stage
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository
import org.springframework.batch.core.ExitStatus
import org.springframework.batch.core.StepExecution
import org.springframework.batch.core.listener.StepExecutionListenerSupport

/**
 * A base class for Spring Batch listeners that deal with an Orca stage.
 */
@CompileStatic
abstract class StageExecutionListener extends StepExecutionListenerSupport {

  private final Pattern TASK_NAME_PATTERN = ~/(?<=[\.])(\S+)(?=\.)/

  protected final ExecutionRepository executionRepository

  protected StageExecutionListener(ExecutionRepository executionRepository) {
    this.executionRepository = executionRepository
  }

  void beforeStage(Stage stage, StepExecution stepExecution) {
  }

  void afterStage(Stage stage, StepExecution stepExecution) {
  }

  void beforeTask(Stage stage, StepExecution stepExecution) {
  }

  void afterTask(Stage stage, StepExecution stepExecution) {
  }

  @Override
  final void beforeStep(StepExecution stepExecution) {
    def stage = currentStage(stepExecution)
    if (isFirstTaskInStage(stage, stepExecution)) {
      beforeStage(stage, stepExecution)
    }
    beforeTask(stage, stepExecution)
  }

  @Override
  final ExitStatus afterStep(StepExecution stepExecution) {
    def stage = currentStage(stepExecution)
    afterTask(stage, stepExecution)
    if (isLastTaskInStage(stage, stepExecution)) {
      afterStage(stage, stepExecution)
    }
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

  // TODO this won't work for a number of reasons:
  // 1: stage.tasks is empty until StageTaskPropagationListener runs
  // 2: stage.tasks will be incomplete for non-linear stages
  // 3: multiple tasks with the same name will break the logic
  // We need to have a better way to mark the first and last tasks in a stage
  // Maybe a special listener or a special task.

  private boolean isFirstTaskInStage(Stage stage, StepExecution stepExecution) {
    !stage.tasks.empty && stage.tasks.first().name == taskName(stepExecution)
  }

  private boolean isLastTaskInStage(Stage stage, StepExecution stepExecution) {
    !stage.tasks.empty && stage.tasks.last().name == taskName(stepExecution)
  }
}
