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

import com.netflix.spinnaker.kork.dynamicconfig.DynamicConfigService;
import com.netflix.spinnaker.orca.api.pipeline.models.StageExecution;
import com.netflix.spinnaker.orca.pipeline.graph.StageGraphBuilderImpl;
import com.netflix.spinnaker.orca.pipeline.model.PipelineExecutionImpl;
import com.netflix.spinnaker.orca.pipeline.model.StageExecutionImpl;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.platform.runner.JUnitPlatform;
import org.junit.runner.RunWith;

@RunWith(JUnitPlatform.class)
public class ShrinkClusterStageTest {

  private PipelineExecutionImpl pipeline;

  private ShrinkClusterStage shrinkClusterStage;

  @BeforeEach
  public void setup() {
    DynamicConfigService dynamicConfigService = mock(DynamicConfigService.class);
    DisableClusterStage disableClusterStage = mock(DisableClusterStage.class);

    pipeline = new PipelineExecutionImpl(PIPELINE, "3", "testapp");

    shrinkClusterStage = new ShrinkClusterStage(dynamicConfigService, disableClusterStage);
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  public void testPresenceOfRunDisableClusterStepWhenAllowDeleteActiveIsTrue(
      boolean runDisableClusterStep) {
    // given:
    // set up stage context
    Map<String, Object> stageContext = new HashMap<>();
    stageContext.put("allowDeleteActive", true);
    stageContext.put("refId", "Shrink Cluster and Keep Newest");
    stageContext.put("runDisableClusterStep", runDisableClusterStep);

    // Shrink Cluster Stage
    StageExecutionImpl stage =
        new StageExecutionImpl(
            pipeline,
            ShrinkClusterStage.STAGE_TYPE,
            "Shrink Cluster and Keep Newest",
            stageContext);

    pipeline.getStages().add(stage);

    // when:
    Iterable<StageExecution> allStages = addAdditionalBeforeStages(shrinkClusterStage, stage);

    // then:
    if (runDisableClusterStep) {
      assertThat(allStages).isNotEmpty();
      // disableCluster stage should be there if runDisableClusterStep is true
      assertThat(allStages).extracting(StageExecution::getName).containsExactly("disableCluster");
    } else {
      assertThat(allStages).isEmpty();
    }
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  public void testAbsenceOfRunDisableClusterStepWhenAllowDeleteActiveIsFalse(
      boolean runDisableClusterStep) {
    // given:
    // set up stage context
    Map<String, Object> stageContext = new HashMap<>();
    stageContext.put("allowDeleteActive", false);
    stageContext.put("refId", "Shrink Cluster and Keep Newest");
    stageContext.put("runDisableClusterStep", runDisableClusterStep);

    // Shrink Cluster Stage
    StageExecutionImpl stage =
        new StageExecutionImpl(
            pipeline,
            ShrinkClusterStage.STAGE_TYPE,
            "Shrink Cluster and Keep Newest",
            stageContext);

    pipeline.getStages().add(stage);

    // when:
    Iterable<StageExecution> allStages = addAdditionalBeforeStages(shrinkClusterStage, stage);

    // then: disableCluster step should not be there
    assertThat(allStages).isEmpty();
  }

  private Iterable<StageExecution> addAdditionalBeforeStages(
      ShrinkClusterStage shrinkClusterStage, StageExecutionImpl stage) {
    StageGraphBuilderImpl graph = StageGraphBuilderImpl.afterStages(stage);
    shrinkClusterStage.addAdditionalBeforeStages(stage, graph);
    return graph.build();
  }
}
