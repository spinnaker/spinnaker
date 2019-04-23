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

import com.netflix.spinnaker.orca.clouddriver.pipeline.conditions.Condition;
import com.netflix.spinnaker.orca.clouddriver.pipeline.conditions.ConditionSupplier;
import com.netflix.spinnaker.orca.clouddriver.pipeline.conditions.WaitForConditionStage;
import com.netflix.spinnaker.orca.clouddriver.pipeline.servergroup.strategies.DeployStagePreProcessor;
import com.netflix.spinnaker.orca.kato.pipeline.support.StageData;
import com.netflix.spinnaker.orca.pipeline.model.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

@Component
@ConditionalOnBean(ConditionSupplier.class)
@ConditionalOnExpression("${tasks.evaluateCondition.enabled:false}")
public class ConditionAwareDeployStagePreprocessor implements DeployStagePreProcessor {
  private final Logger log = LoggerFactory.getLogger(ConditionAwareDeployStagePreprocessor.class);
  private final WaitForConditionStage waitForConditionStage;
  private final List<ConditionSupplier> conditionSuppliers;

  @Autowired
  public ConditionAwareDeployStagePreprocessor(
    WaitForConditionStage waitForConditionStage,
    List<ConditionSupplier> conditionSuppliers
  ) {
    this.waitForConditionStage = waitForConditionStage;
    this.conditionSuppliers = conditionSuppliers;
  }

  @Override
  public boolean supports(Stage stage) {
    return true;
  }

  @Override
  public List<StageDefinition> beforeStageDefinitions(Stage stage) {
    try {
      final StageData stageData = stage.mapTo(StageData.class);
      Set<Condition> conditions = conditionSuppliers
        .stream()
        .flatMap(supplier -> supplier.getConditions(
          stageData.getCluster(),
          stageData.getRegion(),
          stageData.getAccount()
        ).stream()).filter(Objects::nonNull)
        .collect(Collectors.toSet());
      if (conditions.isEmpty()) {
        // do no inject the stage if there are no active conditions
        return Collections.emptyList();
      }

      Map<String, Object> ctx = new HashMap<>();
      // defines what is required by condition suppliers
      ctx.put("region", stageData.getRegion());
      ctx.put("cluster", stageData.getCluster());
      ctx.put("account", stageData.getAccount());
      StageDefinition stageDefinition = new StageDefinition();
      stageDefinition.name = "Wait For Condition";
      stageDefinition.context = ctx;
      stageDefinition.stageDefinitionBuilder = waitForConditionStage;
      return Collections.singletonList(stageDefinition);
    } catch (Exception e) {
      log.error("Error determining active conditions. Proceeding with execution {}", stage.getExecution().getId());
    }

    return Collections.emptyList();
  }
}
