/*
 * Copyright 2017 Netflix, Inc.
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

package com.netflix.spinnaker.orca.clouddriver.pipeline.cluster;

import com.netflix.spinnaker.orca.clouddriver.pipeline.servergroup.RollbackServerGroupStage;
import com.netflix.spinnaker.orca.clouddriver.tasks.cluster.DetermineRollbackCandidatesTask;
import com.netflix.spinnaker.orca.pipeline.StageDefinitionBuilder;
import com.netflix.spinnaker.orca.pipeline.TaskNode;
import com.netflix.spinnaker.orca.pipeline.model.Stage;
import com.netflix.spinnaker.orca.pipeline.model.SyntheticStageOwner;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.netflix.spinnaker.orca.pipeline.StageDefinitionBuilder.newStage;

@Component
public class RollbackClusterStage implements StageDefinitionBuilder {
  public static final String PIPELINE_CONFIG_TYPE = "rollbackCluster";

  private final RollbackServerGroupStage rollbackServerGroupStage;

  @Autowired
  public RollbackClusterStage(RollbackServerGroupStage rollbackServerGroupStage) {
    this.rollbackServerGroupStage = rollbackServerGroupStage;
  }

  @Override
  public void taskGraph(@Nonnull Stage stage, @Nonnull TaskNode.Builder builder) {
    builder
      .withTask("determineRollbackCandidates", DetermineRollbackCandidatesTask.class);
  }

  @Nonnull
  @Override
  public List<Stage> afterStages(@Nonnull Stage stage) {
    StageData stageData = stage.mapTo(StageData.class);

    List<Stage> stages = new ArrayList<>();
    for (String region : stageData.regions) {
      Map<String, Object> context = new HashMap<>();
      context.put(
        "rollbackType",
        ((Map) stage.getOutputs().get("rollbackTypes")).get(region)
      );
      context.put(
        "rollbackContext",
        ((Map) stage.getOutputs().get("rollbackContexts")).get(region)
      );
      context.put("type", rollbackServerGroupStage.getType());
      context.put("region", region);
      context.put("credentials", stageData.account);
      context.put("cloudProvider", stageData.cloudProvider);

      stages.add(
        newStage(
          stage.getExecution(),
          rollbackServerGroupStage.getType(),
          "Rollback " + region,
          context,
          stage,
          SyntheticStageOwner.STAGE_AFTER
        )
      );
    }

    // TODO-AJ Consider wait stages between regions

    return stages;
  }

  static class StageData {
    public String account;
    public String cloudProvider;

    public List<String> regions;

    public Long waitTimeBetweenRegions;
  }
}
