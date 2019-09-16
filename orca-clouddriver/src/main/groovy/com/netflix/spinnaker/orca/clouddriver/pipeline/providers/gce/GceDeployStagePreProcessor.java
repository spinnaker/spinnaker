/*
 * Copyright 2019 Google, Inc.
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

package com.netflix.spinnaker.orca.clouddriver.pipeline.providers.gce;

import static com.netflix.spinnaker.orca.kato.pipeline.support.ResizeStrategySupport.getSource;

import com.google.common.collect.ImmutableList;
import com.netflix.spinnaker.orca.clouddriver.pipeline.providers.aws.ApplySourceServerGroupCapacityStage;
import com.netflix.spinnaker.orca.clouddriver.pipeline.providers.aws.CaptureSourceServerGroupCapacityTask;
import com.netflix.spinnaker.orca.clouddriver.pipeline.servergroup.ResizeServerGroupStage;
import com.netflix.spinnaker.orca.clouddriver.pipeline.servergroup.strategies.AbstractDeployStrategyStage;
import com.netflix.spinnaker.orca.clouddriver.pipeline.servergroup.strategies.DeployStagePreProcessor;
import com.netflix.spinnaker.orca.clouddriver.pipeline.servergroup.support.TargetServerGroupResolver;
import com.netflix.spinnaker.orca.kato.pipeline.strategy.Strategy;
import com.netflix.spinnaker.orca.kato.pipeline.support.ResizeStrategy;
import com.netflix.spinnaker.orca.kato.pipeline.support.StageData;
import com.netflix.spinnaker.orca.pipeline.model.Stage;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import javax.annotation.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class GceDeployStagePreProcessor implements DeployStagePreProcessor {
  private final ApplySourceServerGroupCapacityStage applySourceServerGroupSnapshotStage;
  private final ResizeServerGroupStage resizeServerGroupStage;
  private final TargetServerGroupResolver targetServerGroupResolver;

  @Autowired
  public GceDeployStagePreProcessor(
      ApplySourceServerGroupCapacityStage applySourceServerGroupCapacityStage,
      ResizeServerGroupStage resizeServerGroupStage,
      TargetServerGroupResolver targetServerGroupResolver) {
    this.applySourceServerGroupSnapshotStage = applySourceServerGroupCapacityStage;
    this.resizeServerGroupStage = resizeServerGroupStage;
    this.targetServerGroupResolver = targetServerGroupResolver;
  }

  @Override
  public ImmutableList<StepDefinition> additionalSteps(Stage stage) {
    return ImmutableList.of(
        new StepDefinition(
            "snapshotSourceServerGroup", CaptureSourceServerGroupCapacityTask.class));
  }

  @Override
  public ImmutableList<StageDefinition> beforeStageDefinitions(Stage stage) {
    StageData stageData = stage.mapTo(StageData.class);
    if (!shouldPinSourceServerGroup(stageData.getStrategy())) {
      return ImmutableList.of();
    }

    Map<String, Object> resizeContext = getResizeContext(stageData);
    resizeContext.put("pinMinimumCapacity", true);

    return ImmutableList.of(
        new StageDefinition(
            String.format("Pin %s", resizeContext.get("serverGroupName")),
            resizeServerGroupStage,
            resizeContext));
  }

  @Override
  public ImmutableList<StageDefinition> afterStageDefinitions(Stage stage) {
    return ImmutableList.of(
        new StageDefinition(
            "restoreMinCapacityFromSnapshot",
            applySourceServerGroupSnapshotStage,
            Collections.emptyMap()));
  }

  @Override
  public ImmutableList<StageDefinition> onFailureStageDefinitions(Stage stage) {
    StageData stageData = stage.mapTo(StageData.class);

    StageDefinition unpinServerGroupStage = buildUnpinServerGroupStage(stageData);
    if (unpinServerGroupStage == null) {
      return ImmutableList.of();
    }

    return ImmutableList.of(unpinServerGroupStage);
  }

  @Override
  public boolean supports(Stage stage) {
    StageData stageData = stage.mapTo(StageData.class);
    return stageData.getCloudProvider().equals("gce");
  }

  private static boolean shouldPinSourceServerGroup(String strategy) {
    return Strategy.fromStrategyKey(strategy) == Strategy.RED_BLACK;
  }

  private Map<String, Object> getResizeContext(StageData stageData) {
    AbstractDeployStrategyStage.CleanupConfig cleanupConfig =
        AbstractDeployStrategyStage.CleanupConfig.fromStage(stageData);
    Map<String, Object> resizeContext = new HashMap<>();

    resizeContext.put(
        cleanupConfig.getLocation().singularType(), cleanupConfig.getLocation().getValue());
    resizeContext.put("cluster", cleanupConfig.getCluster());
    resizeContext.put("moniker", cleanupConfig.getMoniker());
    resizeContext.put("credentials", cleanupConfig.getAccount());
    resizeContext.put("cloudProvider", cleanupConfig.getCloudProvider());

    ResizeStrategy.Source source = getSource(targetServerGroupResolver, stageData, resizeContext);
    resizeContext.put("serverGroupName", source.getServerGroupName());
    resizeContext.put("action", ResizeStrategy.ResizeAction.scale_to_server_group);
    resizeContext.put("source", source);
    resizeContext.put(
        "useNameAsLabel", true); // hint to deck that it should _not_ override the name

    return resizeContext;
  }

  @Nullable
  private StageDefinition buildUnpinServerGroupStage(StageData stageData) {
    if (!shouldPinSourceServerGroup(stageData.getStrategy())) {
      return null;
    }

    if (stageData.getScaleDown()) {
      // source server group has been scaled down, no need to unpin if deploy was successful
      return null;
    }

    Map<String, Object> resizeContext = getResizeContext(stageData);
    resizeContext.put("unpinMinimumCapacity", true);

    return new StageDefinition(
        String.format("Unpin %s", resizeContext.get("serverGroupName")),
        resizeServerGroupStage,
        resizeContext);
  }
}
