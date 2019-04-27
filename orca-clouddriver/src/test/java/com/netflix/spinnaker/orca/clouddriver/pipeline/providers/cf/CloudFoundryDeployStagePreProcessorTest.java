/*
 * Copyright 2019 Pivotal, Inc.
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

package com.netflix.spinnaker.orca.clouddriver.pipeline.providers.cf;

import com.netflix.spinnaker.orca.clouddriver.pipeline.cluster.RollbackClusterStage;
import com.netflix.spinnaker.orca.clouddriver.pipeline.servergroup.ServerGroupForceCacheRefreshStage;
import com.netflix.spinnaker.orca.clouddriver.pipeline.servergroup.strategies.DeployStagePreProcessor;
import com.netflix.spinnaker.orca.kato.pipeline.support.StageData;
import com.netflix.spinnaker.orca.pipeline.StageDefinitionBuilder;
import com.netflix.spinnaker.orca.pipeline.model.Stage;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static java.util.Collections.singletonMap;
import static org.assertj.core.api.Assertions.assertThat;

class CloudFoundryDeployStagePreProcessorTest {
  private RollbackClusterStage rollbackClusterStage = new RollbackClusterStage(null, null);
  private ServerGroupForceCacheRefreshStage serverGroupForceCacheRefreshStage = new ServerGroupForceCacheRefreshStage();
  private CloudFoundryDeployStagePreProcessor preProcessor =
    new CloudFoundryDeployStagePreProcessor(rollbackClusterStage, serverGroupForceCacheRefreshStage);

  @Test
  void onFailureStageDefinitionsReturnsEmptyListForRedBlack() {
    Stage stage = new Stage();
    Map<String, Object> context = new HashMap<>();
    context.put("strategy", "redblack");
    context.put("cloudProvider", "cloudfoundry");
    context.put("rollback", singletonMap("onFailure", true));
    stage.setContext(context);

    List<DeployStagePreProcessor.StageDefinition> results = preProcessor.onFailureStageDefinitions(stage);

    assertThat(results).isEmpty();
  }

  @Test
  void onFailureStageDefinitionsReturnsEmptyListIfRollbackIsNull() {
    Stage stage = new Stage();
    Map<String, Object> context = new HashMap<>();
    context.put("strategy", "redblack");
    context.put("cloudProvider", "cloudfoundry");
    stage.setContext(context);

    List<DeployStagePreProcessor.StageDefinition> results = preProcessor.onFailureStageDefinitions(stage);

    assertThat(results).isEmpty();
  }

  @Test
  void onFailureStageDefinitionsReturnsEmptyListIfRollbackOnFailureIsFalse() {
    Stage stage = new Stage();
    Map<String, Object> context = new HashMap<>();
    context.put("strategy", "redblack");
    context.put("cloudProvider", "cloudfoundry");
    context.put("rollback", singletonMap("onFailure", false));
    stage.setContext(context);

    List<DeployStagePreProcessor.StageDefinition> results = preProcessor.onFailureStageDefinitions(stage);

    assertThat(results).isEmpty();
  }

  @Test
  void onFailureStageDefinitionsReturnsCacheRefreshAndRollbackForCfRollingRedBlack() {
    Stage stage = new Stage();
    StageData.Source source = new StageData.Source();
    source.setServerGroupName("sourceServerGroupName");
    Map<String, Object> context = new HashMap<>();
    context.put("strategy", "cfrollingredblack");
    context.put("cloudProvider", "cloudfoundry");
    context.put("source", source);
    context.put("rollback", singletonMap("onFailure", true));
    stage.setContext(context);

    List<DeployStagePreProcessor.StageDefinition> results = preProcessor.onFailureStageDefinitions(stage);

    assertThat(results.stream().map(stageDefinition -> stageDefinition.stageDefinitionBuilder.getType()))
      .containsExactly(StageDefinitionBuilder.getType(ServerGroupForceCacheRefreshStage.class),
        RollbackClusterStage.PIPELINE_CONFIG_TYPE);
  }
}
