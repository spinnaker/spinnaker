/*
 * Copyright 2016 Netflix, Inc.
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

package com.netflix.spinnaker.orca.batch.listeners;

import java.util.List;
import com.netflix.spinnaker.orca.ExecutionStatus;
import com.netflix.spinnaker.orca.listeners.DefaultPersister;
import com.netflix.spinnaker.orca.listeners.Persister;
import com.netflix.spinnaker.orca.listeners.StageListener;
import com.netflix.spinnaker.orca.pipeline.model.Execution;
import com.netflix.spinnaker.orca.pipeline.model.Stage;
import com.netflix.spinnaker.orca.pipeline.model.Task;
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.listener.StepExecutionListenerSupport;
import static java.lang.String.format;

public class SpringBatchStageListener extends StepExecutionListenerSupport implements StageListener {
  private final ExecutionRepository executionRepository;
  private final StageListener stageListener;
  private final Persister defaultPersister;

  public SpringBatchStageListener(ExecutionRepository executionRepository,
                                  StageListener stageListener) {
    this.executionRepository = executionRepository;
    this.stageListener = stageListener;
    this.defaultPersister = new DefaultPersister(executionRepository);
  }

  @Override
  public void beforeTask(Persister persister, Stage stage, Task task) {
    stageListener.beforeTask(defaultPersister, stage, task);
  }

  @Override
  public void beforeStage(Persister persister, Stage stage) {
    stageListener.beforeStage(defaultPersister, stage);
  }

  @Override
  public void afterTask(Persister persister,
                        Stage stage,
                        Task task,
                        ExecutionStatus executionStatus,
                        boolean wasSuccessful) {
    stageListener.afterTask(defaultPersister, stage, task, executionStatus, wasSuccessful);
  }

  @Override
  public void afterStage(Persister persister, Stage stage) {
    stageListener.afterStage(defaultPersister, stage);
  }

  @Override
  public final void beforeStep(StepExecution stepExecution) {
    Stage stage = currentStage(stepExecution);

    List<Task> tasks = stage.getTasks();
    String taskId = taskId(stepExecution);

    Task task = tasks
      .stream()
      .filter(t -> t.getId().equals(taskId))
      .findFirst()
      .orElseThrow(() -> new IllegalStateException(
        format("Task '%s' not found in stage '%s' of execution '%s'", taskId, stage.getId(), stage.getExecution().getId())
      ));

    if (task.isStageStart()) {
      beforeStage(defaultPersister, stage);
    }
    beforeTask(defaultPersister, stage, task);
  }

  @Override
  public final ExitStatus afterStep(StepExecution stepExecution) {
    Stage stage = currentStage(stepExecution);

    List<Task> tasks = stage.getTasks();
    String taskId = taskId(stepExecution);

    Task task = tasks
      .stream()
      .filter(t -> t.getId().equals(taskId))
      .findFirst()
      .orElseThrow(() -> new IllegalStateException(
        format("Task '%s' not found in stage '%s' of execution '%s'", taskId, stage.getId(), stage.getExecution().getId())
      ));

    ExecutionStatus executionStatus = (ExecutionStatus) stepExecution.getExecutionContext().get("orcaTaskStatus");

    afterTask(defaultPersister, stage, task, executionStatus, wasSuccessful(stepExecution));
    if (task.isStageEnd()) {
      afterStage(defaultPersister, stage);
    }

    return super.afterStep(stepExecution);
  }

  private String stageId(StepExecution stepExecution) {
    return stepExecution.getStepName().split("\\.")[0];
  }

  private String taskName(StepExecution stepExecution) {
    return stepExecution.getStepName().split("\\.")[2];
  }

  private String taskId(StepExecution stepExecution) {
    return stepExecution.getStepName().split("\\.")[3];
  }

  private Execution currentExecution(StepExecution stepExecution) {
    if (stepExecution.getJobParameters().getParameters().containsKey("pipeline")) {
      String id = stepExecution.getJobParameters().getString("pipeline");
      return executionRepository.retrievePipeline(id);
    }

    String id = stepExecution.getJobParameters().getString("orchestration");
    return executionRepository.retrieveOrchestration(id);
  }

  private Stage currentStage(StepExecution stepExecution) {
    List<Stage> stages = currentExecution(stepExecution).getStages();
    return stages
      .stream()
      .filter(stage -> stage.getId().equals(stageId(stepExecution)))
      .findFirst()
      .orElse(null);
  }

  /**
   * Determines if the step was a success (from an Orca perspective). Note that
   * even if the Orca task failed we'll get a `stepExecution.status` of
   * `COMPLETED` as the error was handled.
   */
  private static boolean wasSuccessful(StepExecution stepExecution) {
    ExecutionStatus orcaTaskStatus = (ExecutionStatus) stepExecution.getExecutionContext().get("orcaTaskStatus");
    return stepExecution.getExitStatus().getExitCode().equals(ExitStatus.COMPLETED.getExitCode()) || (orcaTaskStatus != null && orcaTaskStatus.isSuccessful());
  }
}
