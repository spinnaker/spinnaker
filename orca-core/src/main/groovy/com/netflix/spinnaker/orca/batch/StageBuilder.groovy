/*
 * Copyright 2014 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
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

package com.netflix.spinnaker.orca.batch

import groovy.transform.CompileStatic
import groovy.transform.PackageScope
import groovy.transform.TypeCheckingMode
import com.google.common.annotations.VisibleForTesting
import com.google.common.base.Function
import com.google.common.base.Optional
import com.google.common.collect.ImmutableList
import com.netflix.spinnaker.orca.Task
import com.netflix.spinnaker.orca.pipeline.model.*
import org.springframework.batch.core.Step
import org.springframework.batch.core.StepExecutionListener
import org.springframework.batch.core.configuration.annotation.StepBuilderFactory
import org.springframework.batch.core.job.builder.JobFlowBuilder
import org.springframework.batch.core.step.builder.StepBuilder
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.ApplicationContext
import org.springframework.context.ApplicationContextAware
import static java.util.Collections.EMPTY_LIST
import static java.util.UUID.randomUUID

/**
 * Base class for a component that builds a _stage_ to be run as (part of) a
 * _pipeline_ and is backed by an underlying Spring Batch model.
 *
 * Note a _stage_ does not directly correspond to anything in Batch – perhaps a
 * {@link org.springframework.batch.core.job.flow.Flow}
 */
@CompileStatic
abstract class StageBuilder implements ApplicationContextAware {

  final String type

  private StepBuilderFactory steps
  private TaskTaskletAdapter taskTaskletAdapter
  private List<StepExecutionListener> taskListeners
  private ApplicationContext applicationContext

  StageBuilder(String type) {
    this.type = type
  }

  /**
   * Implementations should construct any steps necessary for the stage and append them to +jobBuilder+. This method
   * is typically called when the stage is not the first in the pipeline.
   *
   * @param jobBuilder the builder for the job. Implementations should append steps to this.
   * @param stage the stage configuration.
   * @return the resulting builder after any steps are appended.
   */
  abstract JobFlowBuilder build(JobFlowBuilder jobBuilder, Stage stage)

  /**
   * Builds a Spring Batch +Step+ from an Orca +Task+ using required naming
   * convention.
   *
   * @param taskName The simple name for the task within the context of the stage.
   * @param taskType The +Task+ implementation class.
   * @return a +Step+ that will execute an instance of the required +Task+.
   */
  protected Step buildStep(Stage stage, String taskName, Class<? extends Task> taskType) {
    buildStep stage, taskName, applicationContext.getBean(taskType)
  }

  /**
   * Builds a Spring Batch +Step+ from an Orca +Task+ using required naming
   * convention.
   *
   * @param taskName The simple name for the task within the context of the stage.
   * @param task The +Task+ implementation.
   * @return a +Step+ that will execute the specified +Task+.
   */
  protected Step buildStep(Stage stage, String taskName, Task task) {
    createStepWithListeners(stage, taskName)
      .tasklet(taskTaskletAdapter.decorate(task))
      .build()
  }

  @CompileStatic(TypeCheckingMode.SKIP)
  private StepBuilder createStepWithListeners(Stage stage, String taskName) {
    def stepBuilder = steps.get(stepName(stage, taskName))
    getTaskListeners().inject(stepBuilder) { StepBuilder builder, StepExecutionListener listener ->
      builder.listener(listener)
    } as StepBuilder
  }

  private String stepName(Stage stage, String taskName) {
    def task = new DefaultTask(id: randomUUID().toString(), name: taskName)
    stage.tasks.add(task)

    "${stage.id}.${type}.${taskName}.${task.id}"
  }

  @Autowired
  void setSteps(StepBuilderFactory steps) {
    this.steps = steps
  }

  @Autowired
  void setTaskTaskletAdapter(TaskTaskletAdapter taskTaskletAdapter) {
    this.taskTaskletAdapter = taskTaskletAdapter
  }

  @Autowired
  void setTaskListeners(List<StepExecutionListener> taskListeners) {
    this.taskListeners = taskListeners
  }

  @Override
  void setApplicationContext(ApplicationContext applicationContext) {
    this.applicationContext = applicationContext
  }

  ApplicationContext getApplicationContext() {
    return applicationContext
  }

  @VisibleForTesting
  @PackageScope
  List<StepExecutionListener> getTaskListeners() {
    Optional.fromNullable(taskListeners)
            .transform(ImmutableList.&copyOf as Function)
            .or(EMPTY_LIST)
  }

  static Stage newStage(Execution execution, String type, String name, Map<String, Object> context,
                        Stage parent, Stage.SyntheticStageOwner stageOwner) {
    def stage
    if (execution instanceof Orchestration) {
      stage = new OrchestrationStage(execution, type, context)
    } else {
      stage = new PipelineStage((Pipeline) execution, type, name, context)
    }
    stage.parentStageId = parent.id
    stage.syntheticStageOwner = stageOwner
    stage
  }
}
