/*
 * Copyright 2026 Harness, Inc.
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

package com.netflix.spinnaker.orca.clouddriver.pipeline.providers.aws.lambda;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.netflix.spinnaker.kork.dynamicconfig.DynamicConfigService;
import com.netflix.spinnaker.orca.api.pipeline.Task;
import com.netflix.spinnaker.orca.api.pipeline.graph.TaskNode;
import com.netflix.spinnaker.orca.api.pipeline.models.ExecutionType;
import com.netflix.spinnaker.orca.clouddriver.tasks.providers.aws.lambda.LambdaCacheRefreshTask;
import com.netflix.spinnaker.orca.clouddriver.tasks.providers.aws.lambda.LambdaInvokeTask;
import com.netflix.spinnaker.orca.clouddriver.tasks.providers.aws.lambda.LambdaInvokeVerificationTask;
import com.netflix.spinnaker.orca.clouddriver.tasks.providers.aws.lambda.LambdaResolveInvokeArtifactTask;
import com.netflix.spinnaker.orca.pipeline.model.PipelineExecutionImpl;
import com.netflix.spinnaker.orca.pipeline.model.StageExecutionImpl;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class LambdaInvokeStageTest {

  private DynamicConfigService dynamicConfigService;
  private LambdaInvokeStage lambdaInvokeStage;

  @BeforeEach
  void setUp() {
    dynamicConfigService = mock(DynamicConfigService.class);
    lambdaInvokeStage = new LambdaInvokeStage(dynamicConfigService);
  }

  @Test
  void taskGraph_includesResolveTaskWhenFlagEnabled() {
    when(dynamicConfigService.isEnabled("stages.lambda-invoke.resolve-payload-artifact", false))
        .thenReturn(true);

    StageExecutionImpl stage = buildStage(new HashMap<>());

    TaskNode.TaskGraph graph =
        TaskNode.build(
            TaskNode.GraphType.FULL, builder -> lambdaInvokeStage.taskGraph(stage, builder));

    assertThat(getTaskClasses(graph))
        .containsExactly(
            LambdaResolveInvokeArtifactTask.class,
            LambdaInvokeTask.class,
            LambdaInvokeVerificationTask.class,
            LambdaCacheRefreshTask.class);
  }

  @Test
  void taskGraph_excludesResolveTaskWhenFlagDisabled() {
    when(dynamicConfigService.isEnabled("stages.lambda-invoke.resolve-payload-artifact", false))
        .thenReturn(false);

    StageExecutionImpl stage = buildStage(new HashMap<>());

    TaskNode.TaskGraph graph =
        TaskNode.build(
            TaskNode.GraphType.FULL, builder -> lambdaInvokeStage.taskGraph(stage, builder));

    assertThat(getTaskClasses(graph))
        .containsExactly(
            LambdaInvokeTask.class,
            LambdaInvokeVerificationTask.class,
            LambdaCacheRefreshTask.class);
  }

  private StageExecutionImpl buildStage(Map<String, Object> context) {
    PipelineExecutionImpl pipeline =
        new PipelineExecutionImpl(ExecutionType.PIPELINE, "1", "lambdaApp");
    StageExecutionImpl stage =
        new StageExecutionImpl(pipeline, "Aws.LambdaInvokeStage", "Invoke Lambda", context);
    pipeline.getStages().add(stage);
    return stage;
  }

  private List<Class<? extends Task>> getTaskClasses(TaskNode.TaskGraph graph) {
    List<Class<? extends Task>> classes = new ArrayList<>();
    for (TaskNode node : graph) {
      classes.add(((TaskNode.TaskDefinition) node).getImplementingClass());
    }
    return classes;
  }
}
