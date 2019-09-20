/*
 * Copyright 2016 Google, Inc.
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

package com.netflix.spinnaker.orca.clouddriver.pipeline.servergroup.strategies

import com.netflix.spinnaker.orca.ExecutionStatus
import com.netflix.spinnaker.orca.clouddriver.pipeline.cluster.DisableClusterStage
import com.netflix.spinnaker.orca.clouddriver.pipeline.cluster.ScaleDownClusterStage
import com.netflix.spinnaker.orca.clouddriver.pipeline.cluster.ShrinkClusterStage
import com.netflix.spinnaker.orca.clouddriver.pipeline.servergroup.CloneServerGroupStage
import com.netflix.spinnaker.orca.clouddriver.pipeline.servergroup.CreateServerGroupStage
import com.netflix.spinnaker.orca.kato.pipeline.support.StageData
import com.netflix.spinnaker.orca.pipeline.WaitStage
import com.netflix.spinnaker.orca.pipeline.model.Stage
import com.netflix.spinnaker.orca.pipeline.model.SyntheticStageOwner
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.ApplicationContext
import org.springframework.context.ApplicationContextAware
import org.springframework.stereotype.Component
import static com.netflix.spinnaker.orca.pipeline.StageDefinitionBuilder.newStage

@Component
class RedBlackStrategy implements Strategy, ApplicationContextAware {

  final String name = "redblack"

  @Autowired
  ShrinkClusterStage shrinkClusterStage

  @Autowired
  ScaleDownClusterStage scaleDownClusterStage

  @Autowired
  DisableClusterStage disableClusterStage

  @Autowired
  WaitStage waitStage

  ApplicationContext applicationContext

  @Override
  List<Stage> composeBeforeStages(Stage parent) {
    return Collections.emptyList()
  }

  @Override
  List<Stage> composeAfterStages(Stage stage) {
    List<Stage> stages = new ArrayList<>()
    StageData stageData = stage.mapTo(StageData)
    Map<String, Object> baseContext = AbstractDeployStrategyStage.CleanupConfig.toContext(stageData)

    Stage parentCreateServerGroupStage = stage.directAncestors().find() { it.type == CreateServerGroupStage.PIPELINE_CONFIG_TYPE || it.type == CloneServerGroupStage.PIPELINE_CONFIG_TYPE }
    if (parentCreateServerGroupStage == null) {
      throw new IllegalStateException("Failed to determine source server group from parent stage while planning red/black flow")
    }

    StageData parentStageData = parentCreateServerGroupStage.mapTo(StageData)
    StageData.Source sourceServerGroup = parentStageData.source

    // Short-circuit if there is no source server group
    if (sourceServerGroup == null || (sourceServerGroup.serverGroupName == null && sourceServerGroup.asgName == null)) {
      return []
    }

    // We don't want the key propagated if interestingHealthProviderNames isn't defined, since this prevents
    // health providers from the stage's 'determineHealthProviders' task to be added to the context.
    if (stage.context.interestingHealthProviderNames != null) {
      baseContext.interestingHealthProviderNames = stage.context.interestingHealthProviderNames
    }

    if (stageData?.maxRemainingAsgs && (stageData?.maxRemainingAsgs > 0)) {
      Map shrinkContext = baseContext + [
        shrinkToSize         : stageData.maxRemainingAsgs,
        allowDeleteActive    : stageData.allowDeleteActive ?: false,
        retainLargerOverNewer: false
      ]
      stages << newStage(
        stage.execution,
        shrinkClusterStage.type,
        "shrinkCluster",
        shrinkContext,
        stage,
        SyntheticStageOwner.STAGE_AFTER
      )
    }

    if(stageData?.getDelayBeforeCleanup()) {
      def waitContext = [waitTime: stageData?.getDelayBeforeCleanup()]
      stages << newStage(
        stage.execution,
        waitStage.type,
        "Wait Before Disable",
        waitContext,
        stage,
        SyntheticStageOwner.STAGE_AFTER
      )
    }

    def disableContext = baseContext + [
      remainingEnabledServerGroups: 1,
      preferLargerOverNewer       : false
    ]
    stages << newStage(
      stage.execution,
      disableClusterStage.type,
      "disableCluster",
      disableContext,
      stage,
      SyntheticStageOwner.STAGE_AFTER
    )

    if (stageData.scaleDown) {
      if(stageData?.getDelayBeforeScaleDown()) {
        def waitContext = [waitTime: stageData?.getDelayBeforeScaleDown()]
        stages << newStage(
          stage.execution,
          waitStage.type,
          "Wait Before Scale Down",
          waitContext,
          stage,
          SyntheticStageOwner.STAGE_AFTER
        )
      }

      def scaleDown = baseContext + [
        allowScaleDownActive         : stageData.allowScaleDownActive ?: false,
        remainingFullSizeServerGroups: 1,
        preferLargerOverNewer        : false
      ]
      stages << newStage(
        stage.execution,
        scaleDownClusterStage.type,
        "scaleDown",
        scaleDown,
        stage,
        SyntheticStageOwner.STAGE_AFTER
      )
    }

    return stages
  }
}
