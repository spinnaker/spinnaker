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

import java.util.Collection;
import java.util.Map;
import com.netflix.spinnaker.orca.pipeline.model.Execution;
import com.netflix.spinnaker.orca.pipeline.model.Stage;
import static com.netflix.spinnaker.orca.pipeline.TaskNode.Builder;
import static com.netflix.spinnaker.orca.pipeline.TaskNode.GraphType.HEAD;
import static com.netflix.spinnaker.orca.pipeline.TaskNode.GraphType.TAIL;

/**
 * Implement for stages that will create parallel branches to perform the same
 * tasks for multiple contexts. For example, a multi-region bake or deploy.
 */
public interface BranchingStageDefinitionBuilder extends StageDefinitionBuilder {

  /**
   * Produce the different contexts for each parallel branch.
   */
  <T extends Execution<T>> Collection<Map<String, Object>> parallelContexts(Stage<T> stage);

  default TaskNode.TaskGraph buildPreGraph(Stage<?> stage) {
    TaskNode.Builder graphBuilder = Builder(HEAD);
    preBranchGraph(stage, graphBuilder);
    return graphBuilder.build();
  }

  /**
   * Define any tasks that should run _before_ the parallel split.
   */
  default void preBranchGraph(Stage<?> stage, TaskNode.Builder builder) {
  }

  default TaskNode.TaskGraph buildPostGraph(Stage<?> stage) {
    Builder graphBuilder = Builder(TAIL);
    postBranchGraph(stage, graphBuilder);
    return graphBuilder.build();
  }

  /**
   * Define any tasks that should run _after_ the parallel split.
   */
  default void postBranchGraph(Stage<?> stage, TaskNode.Builder builder) {
  }

  /**
   * Override this to rename the stage if it has parallel flows.
   * This affects the base stage not the individual parallel synthetic stages.
   */
  default String parallelStageName(Stage<?> stage, boolean hasParallelFlows) {
    return stage.getName();
  }

  /**
   * Determines the type of child stage.
   */
  default String getChildStageType(Stage childStage) {
    return childStage.getType();
  }
}

