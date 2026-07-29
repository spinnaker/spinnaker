/*
 * Copyright 2026 McIntosh.farm
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
package com.netflix.spinnaker.orca.clouddriver.pipeline.providers.aws;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import com.netflix.spinnaker.orca.api.pipeline.models.ExecutionStatus;
import com.netflix.spinnaker.orca.api.pipeline.TaskResult;
import com.netflix.spinnaker.orca.clouddriver.CloudDriverService;
import com.netflix.spinnaker.orca.pipeline.model.PipelineExecutionImpl;
import com.netflix.spinnaker.orca.pipeline.model.StageExecutionImpl;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class ModifyWarmPoolStageTest {

  CloudDriverService cloudDriverService = mock(CloudDriverService.class);

  static Stream<Arguments> warmPoolPresenceScenarios() {
    return Stream.of(
        Arguments.of("upsert", Map.of("minSize", 2), ExecutionStatus.SUCCEEDED),
        Arguments.of("upsert", null, ExecutionStatus.RUNNING),
        Arguments.of("delete", null, ExecutionStatus.SUCCEEDED),
        Arguments.of("delete", Map.of("minSize", 2), ExecutionStatus.RUNNING));
  }

  @ParameterizedTest
  @MethodSource("warmPoolPresenceScenarios")
  void shouldOnlySucceedWhenWarmPoolPresenceReflectsDesiredState(
      String action, Object warmPoolConfiguration, ExecutionStatus expectedStatus) {
    ModifyWarmPoolStage.WaitForWarmPool task =
        new ModifyWarmPoolStage.WaitForWarmPool(cloudDriverService);

    Map<String, Object> context = new HashMap<>();
    context.put("credentials", "test");
    context.put("region", "us-east-1");
    context.put("asgName", "test-asg");
    context.put("action", action);

    StageExecutionImpl stage =
        new StageExecutionImpl(
            PipelineExecutionImpl.newPipeline("orca"), "", "", context);

    Map<String, Object> asgMap = new HashMap<>();
    asgMap.put("warmPoolConfiguration", warmPoolConfiguration);
    when(cloudDriverService.getTargetServerGroupAsMap("test", "test-asg", "us-east-1"))
        .thenReturn(Optional.of(Collections.singletonMap("asg", asgMap)));

    TaskResult result = task.execute(stage);

    assertThat((Object) result.getStatus()).isEqualTo(expectedStatus);
  }

  static Stream<Arguments> regionAndNameVariants() {
    return Stream.of(
        Arguments.of("us-east-1", null, "test-asg", null, "us-east-1", "test-asg"),
        Arguments.of(null, List.of("us-east-1"), "test-asg", null, "us-east-1", "test-asg"),
        Arguments.of(null, List.of("us-east-1"), null, "test-asg", "us-east-1", "test-asg"));
  }

  @ParameterizedTest
  @MethodSource("regionAndNameVariants")
  void shouldSupportRegionRegionsAsgNameServerGroupName(
      String region,
      List<String> regions,
      String asgName,
      String serverGroupName,
      String expectedRegion,
      String expectedServerGroupName) {
    Map<String, Object> context = new HashMap<>();
    if (region != null) context.put("region", region);
    if (regions != null) context.put("regions", regions);
    if (asgName != null) context.put("asgName", asgName);
    if (serverGroupName != null) context.put("serverGroupName", serverGroupName);

    StageExecutionImpl stage =
        new StageExecutionImpl(PipelineExecutionImpl.newPipeline("orca"), "", "", context);

    ModifyWarmPoolStage.WaitForWarmPool.WaitStageData stageData =
        stage.mapTo(ModifyWarmPoolStage.WaitForWarmPool.WaitStageData.class);

    assertThat(stageData.getRegion()).isEqualTo(expectedRegion);
    assertThat(stageData.getServerGroupName()).isEqualTo(expectedServerGroupName);
  }
}
