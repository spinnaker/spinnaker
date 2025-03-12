/*
 * Copyright 2020 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
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
package com.netflix.spinnaker.orca.clouddriver.pipeline;

import static java.lang.String.format;

import com.netflix.spinnaker.orca.api.pipeline.graph.StageDefinitionBuilder;
import com.netflix.spinnaker.orca.api.pipeline.graph.StageGraphBuilder;
import com.netflix.spinnaker.orca.api.pipeline.graph.TaskNode;
import com.netflix.spinnaker.orca.api.pipeline.models.StageExecution;
import com.netflix.spinnaker.orca.clouddriver.model.OperationLifecycle;
import com.netflix.spinnaker.orca.clouddriver.tasks.MonitorCloudOperationTask;
import com.netflix.spinnaker.orca.clouddriver.tasks.SubmitCloudOperationTask;
import com.netflix.spinnaker.orca.kato.pipeline.Nameable;
import java.util.*;
import javax.annotation.Nonnull;
import lombok.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class CloudOperationStage implements StageDefinitionBuilder, Nameable {

  private static final String LIFECYCLE_KEY = "operationLifecycle";

  protected TaskGraphConfigurer configureTaskGraph(@Nonnull StageExecution stage) {
    return new TaskGraphConfigurer();
  }

  @Override
  public final void taskGraph(@Nonnull StageExecution stage, @Nonnull TaskNode.Builder builder) {
    if (isTopLevel(stage)) {
      // The lifecycle key is internal-only; it cannot be set by end-users.
      stage.getContext().remove(LIFECYCLE_KEY);
    }

    if (stage.getContext().containsKey(LIFECYCLE_KEY)) {
      String lifecycle =
          StringUtils.capitalize(stage.getContext().get(LIFECYCLE_KEY).toString().toLowerCase());
      builder
          .withTask(format("submit%sOperation", lifecycle), SubmitCloudOperationTask.class)
          .withTask(format("monitor%sOperation", lifecycle), MonitorCloudOperationTask.class);
    } else {
      TaskGraphConfigurer configurer = configureTaskGraph(stage);

      configurer.beforeTasks.forEach(builder::withTask);

      builder
          .withTask(configurer.submitOperationTaskName, SubmitCloudOperationTask.class)
          .withTask(configurer.monitorOperationTaskName, MonitorCloudOperationTask.class);

      configurer.afterTasks.forEach(builder::withTask);
    }
  }

  @Override
  public void beforeStages(@Nonnull StageExecution parent, @Nonnull StageGraphBuilder graph) {
    if (!isTopLevel(parent)) {
      return;
    }

    graph.append(
        it -> {
          Map<String, Object> context = new HashMap<>(parent.getContext());
          context.put(LIFECYCLE_KEY, OperationLifecycle.BEFORE);

          it.setType(getType());
          it.setName(format("Before %s", getName()));
          it.setContext(context);
        });
  }

  @Override
  public void afterStages(@Nonnull StageExecution parent, @Nonnull StageGraphBuilder graph) {
    if (!isTopLevel(parent)) {
      return;
    }

    graph.append(
        it -> {
          Map<String, Object> context = new HashMap<>(parent.getContext());
          context.put(LIFECYCLE_KEY, OperationLifecycle.AFTER);

          it.setType(getType());
          it.setName(format("After %s", getName()));
          it.setContext(context);
        });
  }

  @Override
  public void onFailureStages(@Nonnull StageExecution stage, @Nonnull StageGraphBuilder graph) {
    if (!isTopLevel(stage)) {
      return;
    }

    graph.append(
        it -> {
          Map<String, Object> context = new HashMap<>(stage.getContext());
          context.put(LIFECYCLE_KEY, OperationLifecycle.FAILURE);

          it.setType(getType());
          it.setName(format("Cleanup %s", getName()));
          it.setContext(context);
        });
  }

  @Override
  public String getName() {
    return "cloudOperation";
  }

  private static boolean isTopLevel(StageExecution stageExecution) {
    return stageExecution.getParentStageId() == null;
  }

  /** Value object for configuring the Task graph of the stage. */
  @Value
  public static class TaskGraphConfigurer {

    /**
     * Tasks that should be added to the {@link TaskNode.Builder} before the submit and monitor
     * tasks.
     */
    List<TaskNode.TaskDefinition> beforeTasks;

    /**
     * Tasks that shouild be added to the {@link TaskNode.Builder} after the submit and monitor
     * tasks.
     */
    List<TaskNode.TaskDefinition> afterTasks;

    /** The name of the submitOperation task name. */
    String submitOperationTaskName;

    /** The name of the monitorOperation task name. */
    String monitorOperationTaskName;

    /** Defaults. */
    public TaskGraphConfigurer() {
      beforeTasks = Collections.emptyList();
      afterTasks = Collections.emptyList();
      submitOperationTaskName = "submitOperation";
      monitorOperationTaskName = "monitorOperation";
    }
  }
}
