/*
 * Copyright 2016 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.orca.pipeline;

import com.netflix.spinnaker.orca.pipeline.TaskNode.TaskDefinition;
import com.netflix.spinnaker.orca.pipeline.model.Execution;
import com.netflix.spinnaker.orca.pipeline.model.Stage;
import com.netflix.spinnaker.orca.pipeline.model.SyntheticStageOwner;
import com.netflix.spinnaker.orca.pipeline.model.Task;
import com.netflix.spinnaker.orca.pipeline.tasks.NoOpTask;
import lombok.extern.slf4j.Slf4j;

import java.util.Collection;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Supplier;

import static com.google.common.collect.Lists.reverse;
import static com.netflix.spinnaker.orca.ExecutionStatus.NOT_STARTED;
import static com.netflix.spinnaker.orca.pipeline.StageDefinitionBuilder.StageDefinitionBuilderSupport.newStage;
import static com.netflix.spinnaker.orca.pipeline.TaskNode.GraphType.FULL;
import static com.netflix.spinnaker.orca.pipeline.TaskNode.GraphType.HEAD;
import static com.netflix.spinnaker.orca.pipeline.model.SyntheticStageOwner.STAGE_AFTER;
import static com.netflix.spinnaker.orca.pipeline.model.SyntheticStageOwner.STAGE_BEFORE;
import static java.lang.String.format;
import static java.util.Collections.emptyList;
import static java.util.Collections.singleton;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.toList;

@Slf4j
public abstract class ExecutionRunnerSupport implements ExecutionRunner {
  private final Collection<StageDefinitionBuilder> stageDefinitionBuilders;

  public ExecutionRunnerSupport(Collection<StageDefinitionBuilder> stageDefinitionBuilders) {
    this.stageDefinitionBuilders = stageDefinitionBuilders;
  }

  /**
   * Plans the tasks in a stage including any pre and post stages.
   * Implementations may call this directly before executing an individual stage
   * or all in advance.
   *
   * @param stage    the stage with no tasks currently attached.
   * @param callback callback invoked with the tasks of the stage.
   * @param <T>      the execution type.
   */
  protected <T extends Execution<T>> void planStage(
    Stage<T> stage,
    BiConsumer<Collection<Stage<T>>, TaskNode.TaskGraph> callback
  ) {
    StageDefinitionBuilder builder = findBuilderForStage(stage);
    if (builder instanceof BranchingStageDefinitionBuilder) {
      BranchingStageDefinitionBuilder branchBuilder = (BranchingStageDefinitionBuilder) builder;

      // build tasks that should run before the branch
      TaskNode.TaskGraph beforeGraph = branchBuilder.buildPreGraph(stage);
      if (beforeGraph.isEmpty()) {
        callback.accept(singleton(stage), TaskNode.singleton(HEAD, "beginParallel", NoOpTask.class));
      } else {
        callback.accept(singleton(stage), beforeGraph);
      }

      // represent the parallel branches with a synthetic stage for each branch
      Collection<Stage<T>> parallelStages = branchBuilder
        .parallelContexts(stage)
        .stream()
        .map(context ->
          newStage(
            stage.getExecution(),
            context.getOrDefault("type", stage.getType()).toString(),
            context.getOrDefault("name", stage.getName()).toString(),
            context,
            stage,
            STAGE_AFTER
          ))
        .collect(toList());

      // main stage name can be overridden
      stage.setName(branchBuilder.parallelStageName(stage, parallelStages.size() > 1));
      stage.setInitializationStage(true);

      // inject the new parallel branches into the execution model
      parallelStages.forEach(it -> injectStage(stage, it, STAGE_AFTER));

      // the parallel branch type may be different (as in deploy stages) so re-evaluate the builder
      StageDefinitionBuilder parallelBranchBuilder = findBuilderForStage(parallelStages.iterator().next());
      // just build task graph once because it's the same for all branches
      TaskNode.TaskGraph taskGraph = parallelBranchBuilder.buildTaskGraph(parallelStages.iterator().next());
      callback.accept(parallelStages, taskGraph);

      // build tasks that run after the branch
      TaskNode.TaskGraph afterGraph = branchBuilder.buildPostGraph(stage);
      callback.accept(singleton(stage), afterGraph);

      // ensure parallel stages have the correct stage type (ie. createServerGroup -> deploy to satisfy deck)
      parallelStages.forEach(it -> it.setType(branchBuilder.getChildStageType(it)));
    } else {
      TaskNode.TaskGraph taskGraph = builder.buildTaskGraph(stage);
      callback.accept(singleton(stage), taskGraph);
    }
  }

  protected <T extends Execution<T>> void planBeforeOrAfterStages(
    Stage<T> stage,
    SyntheticStageOwner type,
    Consumer<Stage<T>> callback
  ) {
    StageDefinitionBuilder builder = findBuilderForStage(stage);
    Map<SyntheticStageOwner, List<Stage<T>>> aroundStages = builder
      .aroundStages(stage)
      .stream()
      .collect(groupingBy(Stage::getSyntheticStageOwner));

    if (type == STAGE_BEFORE) {
      planExecutionWindow(stage, callback);

      aroundStages
        .getOrDefault(type, emptyList())
        .forEach(syntheticStage -> {
          injectStage(stage, syntheticStage, type);
          callback.accept(syntheticStage);
        });
    } else {
      List<Stage<T>> afterStages = aroundStages
        .getOrDefault(type, emptyList());
      // inject after stages in reverse order as it's easier to calculate index
      reverse(afterStages)
        .forEach(syntheticStage ->
          injectStage(stage, syntheticStage, type)
        );
      // process the stages in execution order
      afterStages.forEach(callback);
    }
  }

  private <T extends Execution<T>> void planExecutionWindow(Stage<T> stage, Consumer<Stage<T>> callback) {
    boolean hasExecutionWindow = (Boolean) stage
      .getContext()
      .getOrDefault("restrictExecutionDuringTimeWindow", false);
    boolean isNonSynthetic = stage.getSyntheticStageOwner() == null &&
      stage.getParentStageId() == null;
    if (hasExecutionWindow && isNonSynthetic) {
      Stage<T> syntheticStage = newStage(
        stage.getExecution(),
        RestrictExecutionDuringTimeWindow.TYPE,
        RestrictExecutionDuringTimeWindow.TYPE, // TODO: base on stage.name?
        stage.getContext(),
        stage,
        STAGE_BEFORE
      );
      injectStage(stage, syntheticStage, STAGE_BEFORE);
      callback.accept(syntheticStage);
    }
  }

  private <T extends Execution<T>> void injectStage(
    Stage<T> parent,
    Stage<T> stage,
    SyntheticStageOwner type
  ) {
    List<Stage<T>> stages = parent.getExecution().getStages();
    if (stages.stream().filter(it -> it.getId().equals(stage.getId())).count() == 0) {
      int index = stages.indexOf(parent);
      int offset = type == STAGE_BEFORE ? 0 : 1;
      stages.add(index + offset, stage);
      stage.setParentStageId(parent.getId());
    }
  }

  // TODO: change callback type to Consumer<TaskDefinition>
  protected <T extends Execution<T>> void planTasks(Stage<T> stage, TaskNode.TaskGraph taskGraph, Supplier<String> idGenerator, Consumer<com.netflix.spinnaker.orca.pipeline.model.Task> callback) {
    if (taskGraph.isEmpty()) {
      taskGraph = TaskNode.singleton(FULL, "no-op", NoOpTask.class);
    }
    for (ListIterator<TaskNode> itr = taskGraph.listIterator(); itr.hasNext(); ) {
      boolean isStart = !itr.hasPrevious();
      // do this after calling itr.hasPrevious because ListIterator is stupid
      TaskNode taskDef = itr.next();
      boolean isEnd = !itr.hasNext();

      if (taskDef instanceof TaskDefinition) {
        planTask(stage, (TaskDefinition) taskDef, taskGraph.getType(), idGenerator, isStart, isEnd, callback);
      } else if (taskDef instanceof TaskNode.TaskGraph) {
        planTasks(stage, (TaskNode.TaskGraph) taskDef, idGenerator, callback);
      } else {
        throw new UnsupportedOperationException(format("Unknown TaskNode type %s", taskDef.getClass().getName()));
      }
    }
  }

  private <T extends Execution<T>> void planTask(
    Stage<T> stage,
    TaskDefinition taskDef,
    TaskNode.GraphType type,
    Supplier<String> idGenerator,
    boolean isStart,
    boolean isEnd,
    Consumer<com.netflix.spinnaker.orca.pipeline.model.Task> callback) {

    String taskId = idGenerator.get();

    com.netflix.spinnaker.orca.pipeline.model.Task task = stage
      .getTasks()
      .stream()
      .filter(it -> it.getId().equals(taskId))
      .findFirst()
      .orElseGet(() -> createTask(stage, taskDef, type, isStart, isEnd, taskId));

    callback.accept(task);
  }

  private <T extends Execution<T>> Task createTask(Stage<T> stage, TaskDefinition taskDef, TaskNode.GraphType type, boolean isStart, boolean isEnd, String taskId) {
    Task task = new Task();
    if (isStart) {
      switch (type) {
        case FULL:
        case HEAD:
          task.setStageStart(true);
          break;
        case LOOP:
          task.setLoopStart(true);
          break;
      }
    }
    task.setId(taskId);
    task.setName(taskDef.getName());
    task.setStatus(NOT_STARTED);
    task.setImplementingClass(taskDef.getImplementingClass().getName());
    if (isEnd) {
      switch (type) {
        case FULL:
        case TAIL:
          task.setStageEnd(true);
          break;
        case LOOP:
          task.setLoopEnd(true);
          break;
      }
    }
    stage.getTasks().add(task);
    return task;
  }

  private <T extends Execution<T>> StageDefinitionBuilder findBuilderForStage(Stage<T> stage) {
    return stageDefinitionBuilders.stream()
      .filter(builder -> builder.getType().equals(stage.getType())
        || (stage.getContext() != null && stage.getContext().get("alias") != null && builder.getType().equals(stage.getContext().get("alias"))))
      .findFirst()
      .orElseThrow(() -> new NoSuchStageDefinitionBuilder(stage.getType()));
  }

}
