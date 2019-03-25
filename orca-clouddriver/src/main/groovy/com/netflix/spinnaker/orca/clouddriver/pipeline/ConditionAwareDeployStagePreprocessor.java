/*
 * Copyright 2019 Netflix, Inc.
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

package com.netflix.spinnaker.orca.clouddriver.pipeline;

import com.netflix.spinnaker.orca.clouddriver.pipeline.servergroup.strategies.DeployStagePreProcessor;
import com.netflix.spinnaker.orca.conditions.ConditionSupplier;
import com.netflix.spinnaker.orca.kato.pipeline.support.StageData;
import com.netflix.spinnaker.orca.pipeline.WaitForConditionStage;
import com.netflix.spinnaker.orca.pipeline.model.Stage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
@ConditionalOnExpression("${tasks.evaluateCondition.enabled:false}")
@ConditionalOnBean(value = ConditionSupplier.class)
public class ConditionAwareDeployStagePreprocessor implements DeployStagePreProcessor {
  private final WaitForConditionStage waitForConditionStage;

  @Autowired
  public ConditionAwareDeployStagePreprocessor(
    WaitForConditionStage waitForConditionStage
  ) {
    this.waitForConditionStage = waitForConditionStage;
  }

  @Override
  public boolean supports(Stage stage) {
    return true;
  }

  @Override
  public List<StageDefinition> beforeStageDefinitions(Stage stage) {
    final StageData stageData = stage.mapTo(StageData.class);
    Map<String, Object> ctx = new HashMap<>();
    ctx.put("region", stageData.getRegion());
    ctx.put("cluster", stageData.getCluster());

    StageDefinition stageDefinition = new StageDefinition();
    stageDefinition.name = "Wait For Condition";
    stageDefinition.context = ctx;
    stageDefinition.stageDefinitionBuilder = waitForConditionStage;
    return Collections.singletonList(stageDefinition);
  }
}
