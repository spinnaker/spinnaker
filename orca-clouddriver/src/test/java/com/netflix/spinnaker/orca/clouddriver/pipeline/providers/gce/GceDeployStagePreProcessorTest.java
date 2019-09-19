/*
 * Copyright 2019 Google, Inc.
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
 *
 */

package com.netflix.spinnaker.orca.clouddriver.pipeline.providers.gce;

import static org.assertj.core.api.Assertions.assertThat;

import com.netflix.spinnaker.orca.clouddriver.pipeline.providers.aws.ApplySourceServerGroupCapacityStage;
import com.netflix.spinnaker.orca.clouddriver.pipeline.providers.aws.CaptureSourceServerGroupCapacityTask;
import com.netflix.spinnaker.orca.clouddriver.pipeline.servergroup.strategies.DeployStagePreProcessor.StageDefinition;
import com.netflix.spinnaker.orca.clouddriver.pipeline.servergroup.strategies.DeployStagePreProcessor.StepDefinition;
import com.netflix.spinnaker.orca.clouddriver.pipeline.servergroup.support.TargetServerGroupResolver;
import com.netflix.spinnaker.orca.pipeline.model.Stage;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class GceDeployStagePreProcessorTest {
  private GceDeployStagePreProcessor preProcessor;
  private ApplySourceServerGroupCapacityStage applySourceServerGroupCapacityStage;

  @BeforeEach
  void setUp() {
    applySourceServerGroupCapacityStage = new ApplySourceServerGroupCapacityStage();
    TargetServerGroupResolver targetServerGroupResolver = new TargetServerGroupResolver();
    preProcessor =
        new GceDeployStagePreProcessor(
            applySourceServerGroupCapacityStage, targetServerGroupResolver);
  }

  @Test
  void redBlackStrategyTest() {
    Stage stage = new Stage();
    Map<String, Object> context = createDefaultContext();
    context.put("strategy", "redblack");
    stage.setContext(context);

    // Before Stages
    List<StageDefinition> beforeStages = preProcessor.beforeStageDefinitions(stage);
    assertThat(beforeStages).isEmpty();

    // Additional Steps
    List<StepDefinition> additionalSteps = preProcessor.additionalSteps(stage);
    assertThat(additionalSteps)
        .extracting(StepDefinition::getTaskClass)
        .containsExactly(CaptureSourceServerGroupCapacityTask.class);

    // After Stages
    List<StageDefinition> afterStages = preProcessor.afterStageDefinitions(stage);
    assertThat(afterStages)
        .extracting(stageDefinition -> stageDefinition.stageDefinitionBuilder)
        .containsExactly(applySourceServerGroupCapacityStage);

    // On Failure Stages
    List<StageDefinition> failureStages = preProcessor.onFailureStageDefinitions(stage);
    assertThat(failureStages).isEmpty();
  }

  @Test
  void redBlackStrategyNoExistingServerGroupTest() {
    Stage stage = new Stage();
    Map<String, Object> context = createDefaultContext();
    context.put("strategy", "redblack");
    stage.setContext(context);

    // Before Stages
    List<StageDefinition> beforeStages = preProcessor.beforeStageDefinitions(stage);
    assertThat(beforeStages).isEmpty();

    // Additional Steps
    List<StepDefinition> additionalSteps = preProcessor.additionalSteps(stage);
    assertThat(additionalSteps)
        .extracting(StepDefinition::getTaskClass)
        .containsExactly(CaptureSourceServerGroupCapacityTask.class);

    // After Stages
    List<StageDefinition> afterStages = preProcessor.afterStageDefinitions(stage);
    assertThat(afterStages)
        .extracting(stageDefinition -> stageDefinition.stageDefinitionBuilder)
        .containsExactly(applySourceServerGroupCapacityStage);

    // On Failure Stages
    List<StageDefinition> failureStages = preProcessor.onFailureStageDefinitions(stage);
    assertThat(failureStages).isEmpty();
  }

  @Test
  void noneStrategyTest() {
    Stage stage = new Stage();
    Map<String, Object> context = createDefaultContext();
    context.put("strategy", "none");
    stage.setContext(context);

    // Before Stages
    List<StageDefinition> beforeStages = preProcessor.beforeStageDefinitions(stage);
    assertThat(beforeStages).isEmpty();

    // Additional Steps
    List<StepDefinition> additionalSteps = preProcessor.additionalSteps(stage);
    assertThat(additionalSteps)
        .extracting(StepDefinition::getTaskClass)
        .containsExactly(CaptureSourceServerGroupCapacityTask.class);

    // After Stages
    List<StageDefinition> afterStages = preProcessor.afterStageDefinitions(stage);
    assertThat(afterStages)
        .extracting(stageDefinition -> stageDefinition.stageDefinitionBuilder)
        .containsExactly(applySourceServerGroupCapacityStage);

    // On Failure Stages
    List<StageDefinition> failureStages = preProcessor.onFailureStageDefinitions(stage);
    assertThat(failureStages).isEmpty();
  }

  private Map<String, Object> createDefaultContext() {
    Map<String, Object> context = new HashMap<>();
    context.put("cloudProvider", "gce");
    context.put("zone", "us-central1-f");
    context.put(
        "availabilityZones",
        Collections.singletonMap("us-central1-f", Collections.singletonList("us-central1-f")));
    context.put("application", "my-gce-application");
    return context;
  }
}
