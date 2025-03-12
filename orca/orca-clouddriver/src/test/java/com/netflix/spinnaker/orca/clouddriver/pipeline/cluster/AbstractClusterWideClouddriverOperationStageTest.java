/*
 * Copyright 2021 Salesforce.com, Inc.
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

package com.netflix.spinnaker.orca.clouddriver.pipeline.cluster;

import static com.netflix.spinnaker.orca.api.pipeline.models.ExecutionType.PIPELINE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.netflix.spinnaker.kork.dynamicconfig.DynamicConfigService;
import com.netflix.spinnaker.orca.api.pipeline.graph.TaskNode;
import com.netflix.spinnaker.orca.clouddriver.tasks.cluster.AbstractClusterWideClouddriverTask;
import com.netflix.spinnaker.orca.clouddriver.tasks.cluster.AbstractWaitForClusterWideClouddriverTask;
import com.netflix.spinnaker.orca.clouddriver.tasks.cluster.CheckIfApplicationExistsForClusterTask;
import com.netflix.spinnaker.orca.clouddriver.tasks.cluster.ShrinkClusterTask;
import com.netflix.spinnaker.orca.clouddriver.tasks.cluster.WaitForClusterShrinkTask;
import com.netflix.spinnaker.orca.pipeline.model.PipelineExecutionImpl;
import com.netflix.spinnaker.orca.pipeline.model.StageExecutionImpl;
import java.util.HashMap;
import java.util.Iterator;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

public class AbstractClusterWideClouddriverOperationStageTest {
  private DynamicConfigService dynamicConfigService;
  TestStage testStage;
  StageExecutionImpl stageExecution;

  @BeforeEach
  public void setup() {
    dynamicConfigService = mock(DynamicConfigService.class);
    testStage = new TestStage(dynamicConfigService);
    PipelineExecutionImpl pipeline = new PipelineExecutionImpl(PIPELINE, "1", "testapp");

    // Test Stage
    stageExecution =
        new StageExecutionImpl(pipeline, TestStage.STAGE_TYPE, "Test Stage", new HashMap<>());
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  public void testPresenceOfCheckIfApplicationExistsForClusterTask(boolean isTaskEnabled) {
    // setup:
    when(dynamicConfigService.isEnabled("stages.test-stage.check-if-application-exists", false))
        .thenReturn(isTaskEnabled);

    // when:
    TaskNode.TaskGraph taskGraph = testStage.buildTaskGraph(stageExecution);

    // then:
    Iterator<TaskNode> iterator = taskGraph.iterator();
    AtomicBoolean doesTaskExist = new AtomicBoolean(false);
    iterator.forEachRemaining(
        element -> {
          if (element instanceof TaskNode.TaskDefinition) {
            if (((TaskNode.TaskDefinition) element)
                .getImplementingClass()
                .equals(CheckIfApplicationExistsForClusterTask.class)) {
              doesTaskExist.set(true);
            }
          }
        });

    assertThat(doesTaskExist.get()).isEqualTo(isTaskEnabled);
  }

  /**
   * helper class that is created only for test purposes. It mimics the ShrinkClusterStage class
   * with the way some methods are overridden in it. But this class exists since I wanted to test
   * the abstract class itself and not any one specific class that extends it.
   */
  private static class TestStage extends AbstractClusterWideClouddriverOperationStage {
    protected static final String STAGE_TYPE = "testStage";

    protected TestStage(DynamicConfigService dynamicConfigService) {
      super(dynamicConfigService);
    }

    protected Class<? extends AbstractClusterWideClouddriverTask> getClusterOperationTask() {
      return ShrinkClusterTask.class;
    }

    protected Class<? extends AbstractWaitForClusterWideClouddriverTask> getWaitForTask() {
      return WaitForClusterShrinkTask.class;
    }
  }
}
