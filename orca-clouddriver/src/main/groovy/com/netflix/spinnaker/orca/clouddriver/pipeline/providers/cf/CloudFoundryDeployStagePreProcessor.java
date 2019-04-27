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

import com.fasterxml.jackson.annotation.JsonProperty;
import com.netflix.spinnaker.orca.clouddriver.pipeline.cluster.RollbackClusterStage;
import com.netflix.spinnaker.orca.clouddriver.pipeline.servergroup.ServerGroupForceCacheRefreshStage;
import com.netflix.spinnaker.orca.clouddriver.pipeline.servergroup.strategies.DeployStagePreProcessor;
import com.netflix.spinnaker.orca.kato.pipeline.strategy.Strategy;
import com.netflix.spinnaker.orca.kato.pipeline.support.StageData;
import com.netflix.spinnaker.orca.pipeline.model.Stage;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.TimeUnit;

@Component
@AllArgsConstructor
class CloudFoundryDeployStagePreProcessor implements DeployStagePreProcessor {
  private RollbackClusterStage rollbackClusterStage;
  private ServerGroupForceCacheRefreshStage serverGroupForceCacheRefreshStage;

  @Override
  public List<StageDefinition> onFailureStageDefinitions(Stage stage) {
    CfRollingRedBlackStageData stageData = stage.mapTo(CfRollingRedBlackStageData.class);
    List<StageDefinition> stageDefinitions = new ArrayList<>();
    Strategy strategy = Strategy.fromStrategy(stageData.getStrategy());

    if (strategy.equals(Strategy.CF_ROLLING_RED_BLACK) && (stageData.getRollback() != null && stageData.getRollback().isOnFailure())) {
      StageDefinition forceCacheRefreshStageDefinition = new StageDefinition();
      forceCacheRefreshStageDefinition.stageDefinitionBuilder = serverGroupForceCacheRefreshStage;
      forceCacheRefreshStageDefinition.name = "Force Cache Refresh";
      forceCacheRefreshStageDefinition.context = createBasicContext(stageData);
      stageDefinitions.add(forceCacheRefreshStageDefinition);

      StageDefinition rollbackStageDefinition = new StageDefinition();
      Map<String, Object> rollbackContext = createBasicContext(stageData);
      rollbackContext.put("serverGroup", stageData.getSource().getServerGroupName());
      rollbackContext.put("stageTimeoutMs", TimeUnit.MINUTES.toMillis(30)); // timebox a rollback to 30 minutes
      rollbackStageDefinition.stageDefinitionBuilder = rollbackClusterStage;
      rollbackStageDefinition.name = "Rolling back to previous deployment";
      rollbackStageDefinition.context = rollbackContext;
      stageDefinitions.add(rollbackStageDefinition);
    }

    return stageDefinitions;
  }

  @Override
  public boolean supports(Stage stage) {
    return "cloudfoundry".equals(stage.mapTo(StageData.class).getCloudProvider());
  }

  private Map<String, Object> createBasicContext(CfRollingRedBlackStageData stageData) {
    Map<String, Object> context = new HashMap<>();
    String credentials = stageData.getCredentials() != null ? stageData.getCredentials() : stageData.getAccount();
    context.put("credentials", credentials);
    context.put("cloudProvider", stageData.getCloudProvider());
    context.put("regions", Collections.singletonList(stageData.getRegion()));
    context.put("deploy.server.groups", stageData.getDeployedServerGroups());
    return context;
  }


  @EqualsAndHashCode(callSuper = true)
  @Data
  private static class CfRollingRedBlackStageData extends StageData {
    private Rollback rollback;

    @JsonProperty("deploy.server.groups")
    Map<String, List<String>> deployedServerGroups = new HashMap<>();

    @Data
    private static class Rollback {
      private boolean onFailure;
    }
  }
}
