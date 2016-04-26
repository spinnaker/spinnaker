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

import com.netflix.spinnaker.orca.clouddriver.pipeline.cluster.DisableClusterStage
import com.netflix.spinnaker.orca.clouddriver.pipeline.cluster.ScaleDownClusterStage
import com.netflix.spinnaker.orca.clouddriver.pipeline.cluster.ShrinkClusterStage
import com.netflix.spinnaker.orca.kato.pipeline.support.StageData
import com.netflix.spinnaker.orca.pipeline.LinearStage
import com.netflix.spinnaker.orca.pipeline.model.Stage
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

@Component
class RedBlackStrategy implements Strategy {

  final String name = "redblack"

  @Autowired
  ShrinkClusterStage shrinkClusterStage

  @Autowired
  ScaleDownClusterStage scaleDownClusterStage

  @Autowired
  DisableClusterStage disableClusterStage

  @Override
  void composeFlow(Stage stage) {
    def stageData = stage.mapTo(StageData)
    def cleanupConfig = AbstractDeployStrategyStage.CleanupConfig.fromStage(stage)

    Map baseContext = [
        (cleanupConfig.location.singularType()): cleanupConfig.location.value,
        cluster                                : cleanupConfig.cluster,
        credentials                            : cleanupConfig.account,
        cloudProvider                          : cleanupConfig.cloudProvider,
        interestingHealthProviderNames         : stage.context.interestingHealthProviderNames
    ]

    if (stageData?.maxRemainingAsgs && (stageData?.maxRemainingAsgs > 0)) {
      Map shrinkContext = baseContext + [
          shrinkToSize         : stageData.maxRemainingAsgs,
          allowDeleteActive    : false,
          retainLargerOverNewer: false
      ]
      LinearStage.injectAfter(stage, "shrinkCluster", shrinkClusterStage, shrinkContext)
    }

    if (stageData.scaleDown) {
      def scaleDown = baseContext + [
          allowScaleDownActive         : true,
          remainingFullSizeServerGroups: 1,
          preferLargerOverNewer        : false
      ]
      LinearStage.injectAfter(stage, "scaleDown", scaleDownClusterStage, scaleDown)
    }

    LinearStage.injectAfter(stage, "disableCluster", disableClusterStage, baseContext + [
        remainingEnabledServerGroups: 1,
        preferLargerOverNewer       : false
    ])
  }
}
