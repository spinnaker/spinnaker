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

package com.netflix.spinnaker.orca.batch.stages;

import java.util.List;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.netflix.spinnaker.orca.ExecutionStatus;
import com.netflix.spinnaker.orca.batch.ExecutionListenerProvider;
import com.netflix.spinnaker.orca.batch.StageBuilder;
import com.netflix.spinnaker.orca.listeners.Persister;
import com.netflix.spinnaker.orca.listeners.StageListener;
import com.netflix.spinnaker.orca.pipeline.StageDefinitionBuilder;
import com.netflix.spinnaker.orca.pipeline.TaskNode;
import com.netflix.spinnaker.orca.pipeline.TaskNode.TaskDefinition;
import com.netflix.spinnaker.orca.pipeline.TaskNode.TaskGraph;
import com.netflix.spinnaker.orca.pipeline.model.Execution;
import com.netflix.spinnaker.orca.pipeline.model.Stage;
import com.netflix.spinnaker.orca.pipeline.model.Task;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.job.builder.FlowBuilder;
import static com.netflix.spinnaker.orca.ExecutionStatus.NOT_STARTED;
import static com.netflix.spinnaker.orca.ExecutionStatus.REDIRECT;

@Deprecated
public class RollingPushStageDefinitionBuilder extends StageBuilder {

  private final StageDefinitionBuilder delegate;
  private final ExecutionListenerProvider executionListenerProvider;

  public RollingPushStageDefinitionBuilder(StageDefinitionBuilder delegate,
                                           ExecutionListenerProvider executionListenerProvider) {
    super(delegate.getType());
    this.delegate = delegate;
    this.executionListenerProvider = executionListenerProvider;
  }

  @Override
  protected FlowBuilder buildInternal(FlowBuilder jobBuilder, Stage stage) {
    TaskGraph graph = delegate.buildTaskGraph(stage);
    graph.forEach(node -> {
      if (node instanceof TaskDefinition) {
        TaskDefinition taskDef = (TaskDefinition) node;
        addStep(jobBuilder, stage, taskDef);
      } else if (node instanceof TaskGraph) {
        TaskGraph subGraph = (TaskGraph) node;
        List<TaskNode> taskNodes = Lists.newArrayList(subGraph.iterator());

        Step startOfCycle = addStep(jobBuilder, stage, (TaskDefinition) taskNodes.remove(0));

        while (taskNodes.size() > 1) {
          addStep(jobBuilder, stage, (TaskDefinition) taskNodes.remove(0));
        }

        Step endOfCycle = addStep(jobBuilder, stage, (TaskDefinition) taskNodes.remove(0), new RedirectResetListener());
        jobBuilder.on(REDIRECT.name()).to(startOfCycle);
        jobBuilder.from(endOfCycle);
      }
    });
    return jobBuilder;
  }

  private Step addStep(FlowBuilder jobBuilder, Stage stage, TaskDefinition taskDef) {
    Step step = buildStep(stage, taskDef.getName(), taskDef.getImplementingClass());
    jobBuilder.next(step);
    return step;
  }

  private Step addStep(FlowBuilder jobBuilder, Stage stage, TaskDefinition taskDef, StageListener listener) {
    Step step = buildStep(stage, taskDef.getName(), taskDef.getImplementingClass(), executionListenerProvider.wrap(listener));
    jobBuilder.next(step);
    return step;
  }

  /**
   * A listener that resets the task status of everything in a loop so that it can be re-run without interfering
   * with restart semantics.
   */
  static class RedirectResetListener implements StageListener {
    @Override
    public <T extends Execution<T>> void afterTask(Persister persister, Stage<T> stage, Task task, ExecutionStatus executionStatus, boolean wasSuccessful) {
      if (executionStatus == REDIRECT) {
        List<Task> tasks = stage.getTasks();
        int startIndex = Iterables.indexOf(tasks, it -> it.getName().equals("determineCurrentPhaseTerminations"));
        int endIndex = Iterables.indexOf(tasks, it -> it.getName().equals("checkForRemainingTerminations"));
        tasks.subList(startIndex, endIndex + 1).forEach(it -> {
          it.setStatus(NOT_STARTED);
          it.setEndTime(null);
        });
        persister.save(stage);
      }
    }
  }
}
