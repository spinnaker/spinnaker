/*
 * Copyright 2016 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
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


package com.netflix.spinnaker.orca.clouddriver.pipeline.providers.aws

import com.netflix.spinnaker.orca.clouddriver.pipeline.servergroup.ResizeServerGroupStage
import com.netflix.spinnaker.orca.clouddriver.pipeline.servergroup.strategies.AbstractDeployStrategyStage
import com.netflix.spinnaker.orca.clouddriver.pipeline.servergroup.strategies.DeployStagePreProcessor
import com.netflix.spinnaker.orca.clouddriver.pipeline.servergroup.support.TargetServerGroup
import com.netflix.spinnaker.orca.clouddriver.pipeline.servergroup.support.TargetServerGroupResolver
import com.netflix.spinnaker.orca.kato.pipeline.support.ResizeStrategy
import com.netflix.spinnaker.orca.kato.pipeline.support.StageData
import com.netflix.spinnaker.orca.pipeline.model.Stage
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

import javax.annotation.Nullable

import static com.netflix.spinnaker.orca.kato.pipeline.support.ResizeStrategySupport.getSource

@Component
class AwsDeployStagePreProcessor implements DeployStagePreProcessor {
  @Autowired
  ApplySourceServerGroupCapacityStage applySourceServerGroupSnapshotStage

  @Autowired
  ResizeServerGroupStage resizeServerGroupStage

  @Autowired
  TargetServerGroupResolver targetServerGroupResolver

  @Override
  List<StepDefinition> additionalSteps(Stage stage) {
    def stageData = stage.mapTo(StageData)
    if (stageData.strategy == "rollingredblack") {
      // rolling red/black has no need to snapshot capacities
      return []
    }

    return [
      new StepDefinition(
        name: "snapshotSourceServerGroup",
        taskClass: CaptureSourceServerGroupCapacityTask
      )
    ]
  }

  @Override
  List<StageDefinition> beforeStageDefinitions(Stage stage) {
    def stageData = stage.mapTo(StageData)
    if (shouldPinSourceServerGroup(stageData.strategy)) {
      def optionalResizeContext = getResizeContext(stageData)
      if (!optionalResizeContext.isPresent()) {
        // this means we don't need to resize anything
        // happens in particular when there is no pre-existing source server group
        return []
      }

      def resizeContext = optionalResizeContext.get()
      resizeContext.pinMinimumCapacity = true

      return [
        new StageDefinition(
          name: "Pin ${resizeContext.serverGroupName}",
          stageDefinitionBuilder: resizeServerGroupStage,
          context: resizeContext
        )
      ]
    }

    return []
  }

  @Override
  List<StageDefinition> afterStageDefinitions(Stage stage) {
    def stageData = stage.mapTo(StageData)
    def stageDefinitions = []
    if (stageData.strategy != "rollingredblack") {
      // rolling red/black has no need to apply a snapshotted capacity (on the newly created server group)
      stageDefinitions << new StageDefinition(
        name: "restoreMinCapacityFromSnapshot",
        stageDefinitionBuilder: applySourceServerGroupSnapshotStage,
        context: [:]
      )
    }

    def unpinServerGroupStage = buildUnpinServerGroupStage(stageData, false)
    if (unpinServerGroupStage) {
      stageDefinitions << unpinServerGroupStage
    }

    return stageDefinitions
  }

  @Override
  List<StageDefinition> onFailureStageDefinitions(Stage stage) {
    def stageData = stage.mapTo(StageData)
    def stageDefinitions = []

    def unpinServerGroupStage = buildUnpinServerGroupStage(stageData, true)
    if (unpinServerGroupStage) {
      stageDefinitions << unpinServerGroupStage
    }

    return stageDefinitions
  }

  @Override
  boolean supports(Stage stage) {
    def stageData = stage.mapTo(StageData)
    return stageData.cloudProvider == "aws" // && stageData.useSourceCapacity
  }

  private static boolean shouldPinSourceServerGroup(String strategy) {
    // TODO-AJ consciously only enabling for rolling red/black -- will add support for other strategies after it's working
    return strategy == "rollingredblack"
  }

  private Optional<Map<String, Object>> getResizeContext(StageData stageData) {
    def cleanupConfig = AbstractDeployStrategyStage.CleanupConfig.fromStage(stageData)
    def baseContext = [
      (cleanupConfig.location.singularType()): cleanupConfig.location.value,
      cluster                                : cleanupConfig.cluster,
      moniker                                : cleanupConfig.moniker,
      credentials                            : cleanupConfig.account,
      cloudProvider                          : cleanupConfig.cloudProvider,
    ]

    try {
      def source = getSource(targetServerGroupResolver, stageData, baseContext)
      if (!source) {
        return Optional.empty()
      }

      baseContext.putAll([
        serverGroupName   : source.serverGroupName,
        action            : ResizeStrategy.ResizeAction.scale_to_server_group,
        source            : source,
        useNameAsLabel    : true     // hint to deck that it should _not_ override the name
      ])
      return Optional.of(baseContext)
    } catch(TargetServerGroup.NotFoundException e) {
      return Optional.empty()
    }
  }

  private StageDefinition buildUnpinServerGroupStage(StageData stageData, boolean deployFailed) {
    if (!shouldPinSourceServerGroup(stageData.strategy)) {
      return null;
    }

    if (stageData.scaleDown && !deployFailed) {
      // source server group has been scaled down, no need to unpin if deploy was successful
      return null
    }

    def optionalResizeContext = getResizeContext(stageData)
    if (!optionalResizeContext.isPresent()) {
      // no source server group, no need to unpin anything
      return null
    }

    def resizeContext = optionalResizeContext.get()
    resizeContext.unpinMinimumCapacity = true

    return new StageDefinition(
      name: "Unpin ${resizeContext.serverGroupName}".toString(),
      stageDefinitionBuilder: resizeServerGroupStage,
      context: resizeContext
    )
  }
}
