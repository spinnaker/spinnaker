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

import com.netflix.spinnaker.orca.ExecutionStatus
import com.netflix.spinnaker.orca.batch.exceptions.ExceptionHandler
import com.netflix.spinnaker.orca.pipeline.parallel.WaitForRequisiteCompletionStage
import groovy.transform.CompileDynamic
import groovy.transform.CompileStatic
import groovy.transform.PackageScope
import com.google.common.annotations.VisibleForTesting
import com.google.common.base.Function
import com.google.common.base.Optional
import com.google.common.collect.ImmutableList
import com.netflix.spinnaker.orca.Task
import com.netflix.spinnaker.orca.pipeline.model.*
import org.springframework.batch.core.Step
import org.springframework.batch.core.StepExecutionListener
import org.springframework.batch.core.configuration.annotation.StepBuilderFactory
import org.springframework.batch.core.job.builder.FlowBuilder
import org.springframework.batch.core.job.flow.Flow
import org.springframework.batch.core.job.flow.support.SimpleFlow
import org.springframework.batch.core.step.builder.StepBuilder
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.ApplicationContext
import org.springframework.context.ApplicationContextAware
import org.springframework.core.task.SimpleAsyncTaskExecutor

import static java.util.Collections.EMPTY_LIST

/**
 * Base class for a component that builds a _stage_ to be run as (part of) a
 * _pipeline_ and is backed by an underlying Spring Batch model.
 *
 * Note a _stage_ does not directly correspond to anything in Batch – perhaps a
 * {@link org.springframework.batch.core.job.flow.Flow}
 */
@CompileStatic
abstract class StageBuilder implements ApplicationContextAware {
  private static final int MAX_PARALLEL_CONCURRENCY = 25

  final String type

  private StepBuilderFactory steps
  private TaskTaskletAdapter taskTaskletAdapter
  private List<StepExecutionListener> taskListeners
  private ApplicationContext applicationContext

  @Autowired
  private final List<ExceptionHandler> exceptionHandlers

  StageBuilder(String type) {
    this.type = type
  }

  @VisibleForTesting
  StageBuilder(String type, List<ExceptionHandler> exceptionHandlers) {
    this.type = type
    this.exceptionHandlers = exceptionHandlers
  }

  /**
   * Implementations should construct any steps necessary for the stage and append them to +jobBuilder+.
   *
   * @param jobBuilder the builder for the job. Implementations should append steps to this.
   * @param stage the stage configuration.
   * @return the resulting builder after any steps are appended.
   */
  final FlowBuilder build(FlowBuilder jobBuilder, Stage stage) {
    try {
      if (stage.execution.parallel) {
        return buildParallel(jobBuilder, stage)
      }

      return buildLinear(jobBuilder, stage)
    } catch (Exception e) {
      def exceptionHandler = exceptionHandlers.find { it.handles(e) }
      if (!exceptionHandler) {
        throw e
      }

      def now = System.currentTimeMillis()
      stage.startTime = now
      stage.endTime = now

      stage.status = ExecutionStatus.TERMINAL
      stage.context.exception = exceptionHandler.handle("build", e)

      return jobBuilder
    }
  }

  @Deprecated
  private FlowBuilder buildLinear(FlowBuilder jobBuilder, Stage stage) {
    buildInternal(jobBuilder, stage)
  }

  private FlowBuilder buildParallel(FlowBuilder jobBuilder, Stage stage) {
    if (stage.execution.builtPipelineObjects.contains(stage)) {
      // stage has already been built by another parallel path
      return jobBuilder
    }

    stage.execution.builtPipelineObjects << stage

    buildInternal(jobBuilder, stage)
    buildDependentStages(jobBuilder, stage)

    return jobBuilder
  }

  protected abstract FlowBuilder buildInternal(FlowBuilder jobBuilder, Stage stage)

  @VisibleForTesting
  @PackageScope
  FlowBuilder buildDependentStages(FlowBuilder jobBuilder, Stage stage) {
    def stageBuilders = applicationContext.getBeansOfType(StageBuilder).values()
    def dependantStages = stage.execution.stages.findAll {
      it.requisiteStageRefIds?.contains(stage.refId)
    }

    if (!dependantStages) {
      return jobBuilder
    }

    def flows = []
    dependantStages.each { Stage childStage ->
      if (stage.execution.builtPipelineObjects.contains(childStage)) {
        // stage has already been built, no need to re-build
        return
      }

      def childStageBuilder = stageBuilders.find { it.type == childStage.type }
      if (!childStageBuilder) {
        throw new IllegalStateException("Unable to find stage builder for type ${childStage.type}")
      }
      if (childStage.requisiteStageRefIds.size() > 1) {
        // multi parent child, insert an artificial join stage that will wait for all parents to complete
        def waitForStageBuilder = stageBuilders.find { it.type == WaitForRequisiteCompletionStage.MAYO_CONFIG_TYPE }
        def waitForStage = newStage(
          childStage.execution,
          waitForStageBuilder.type,
          "Wait For Parent Tasks",
          [requisiteIds: childStage.requisiteStageRefIds] as Map,
          null as Stage,
          null as Stage.SyntheticStageOwner
        )
        ((AbstractStage) waitForStage).id = "${childStage.id}-waitForRequisite"

        def stageIdx = stage.execution.stages.indexOf(childStage)
        stage.execution.stages.add(stageIdx, waitForStage)

        def waitForBuilder = new FlowBuilder<Flow>("WaitForRequisite.${childStage.refId}.${childStage.id}")
        waitForStageBuilder.build(waitForBuilder, waitForStage)

        // child stage should be added after the artificial join stage
        def childFlowBuilder = new FlowBuilder<Flow>("ChildExecution.${childStage.refId}.${childStage.id}")
        flows << waitForBuilder.next(childStageBuilder.build(childFlowBuilder, childStage).build() as Flow).build()
      } else {
        // single parent child, no need for an artificial join stage
        def flowBuilder = new FlowBuilder<Flow>("ChildExecution.${childStage.refId}.${childStage.id}")
        flows << childStageBuilder.build(flowBuilder, childStage).build()
      }
    }

    addFlowsToBuilder(jobBuilder, stage, flows as Flow[])
    return jobBuilder
  }

  /**
   * @param jobBuilder the builder for the job.
   * @param stage the stage configuration.
   * @param flows the flows that should be appended to builder (> 1, a parallel builder should be built and appended).
   * @return the resulting builder after any steps are appended.
   */
  @VisibleForTesting
  @PackageScope
  final FlowBuilder addFlowsToBuilder(FlowBuilder jobBuilder, Stage stage, Flow[] flows) {
    if (flows.size() > 1) {
      def executor = new SimpleAsyncTaskExecutor()
      executor.setConcurrencyLimit(MAX_PARALLEL_CONCURRENCY)

      // children of a fan-out stage should be executed in parallel
      def parallelFlowBuilder = new FlowBuilder<Flow>("ParallelChildren.${stage.refId}")
        .start(new SimpleFlow("NoOp"))
        .split(executor)
        .add(flows)

      return jobBuilder.next(parallelFlowBuilder.build())
    } else if (flows.size() == 1) {
      return jobBuilder.next(flows[0] as Flow)
    }

    return jobBuilder
  }

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

  @CompileDynamic
  private StepBuilder createStepWithListeners(Stage stage, String taskName) {
    def stepBuilder = steps.get(stepName(stage, taskName))
    getTaskListeners().inject(stepBuilder) { StepBuilder builder, StepExecutionListener listener ->
      builder.listener(listener)
    } as StepBuilder
  }

  private String stepName(Stage stage, String taskName) {
    def id = nextTaskId(stage)
    if (!stage.tasks*.id.contains(id)) {
      def task = new DefaultTask(id: id, name: taskName)
      stage.tasks.add(task)
    }

    "${stage.id}.${type}.${taskName}.${id}"
  }

  private String nextTaskId(Stage stage) {
    return stage.taskCounter.incrementAndGet()
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
      stage = new PipelineStage(execution as Pipeline, type, name, context)
    }
    stage.parentStageId = parent?.id
    stage.syntheticStageOwner = stageOwner

    // Look upstream until you find the ultimate ancestor parent (parent w/ no parentStageId)
    while (parent?.parentStageId != null) {
      parent = execution.stages.find { it.id == parent.parentStageId }
    }

    if (parent) {
      // If a parent exists, the new stage id should be deterministically generated
      stage.id = parent.id + "-" + ((AbstractStage) parent).stageCounter.incrementAndGet() + "-" + stage.name?.replaceAll("[^A-Za-z0-9]", "")
    }

    stage
  }
}
