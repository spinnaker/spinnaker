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

import groovy.util.logging.Slf4j
import com.netflix.spinnaker.orca.batch.StageBuilderProvider
import com.netflix.spinnaker.orca.clouddriver.pipeline.cluster.ShrinkClusterStage
import com.netflix.spinnaker.orca.pipeline.model.Execution
import com.netflix.spinnaker.orca.pipeline.model.Stage
import com.netflix.spinnaker.orca.pipeline.model.SyntheticStageOwner
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.ApplicationContext
import org.springframework.context.ApplicationContextAware
import org.springframework.stereotype.Component
import static com.netflix.spinnaker.orca.pipeline.StageDefinitionBuilder.StageDefinitionBuilderSupport.newStage

@Component
@Slf4j
class HighlanderStrategy implements Strategy, ApplicationContextAware {

  final String name = "highlander"

  @Autowired
  ShrinkClusterStage shrinkClusterStage

  ApplicationContext applicationContext

  @Override
  <T extends Execution<T>> List<Stage<T>> composeFlow(Stage<T> stage) {
    def cleanupConfig = AbstractDeployStrategyStage.CleanupConfig.fromStage(stage)
    Map shrinkContext = [
        (cleanupConfig.location.singularType()): cleanupConfig.location.value,
        cluster                                : cleanupConfig.cluster,
        credentials                            : cleanupConfig.account,
        cloudProvider                          : cleanupConfig.cloudProvider,
        shrinkToSize                           : 1,
        allowDeleteActive                      : true,
        retainLargerOverNewer                  : false,
        interestingHealthProviderNames         : stage.context.interestingHealthProviderNames
    ]

    return [
      newStage(
        stage.execution,
        shrinkClusterStage.type,
        "shrinkCluster",
        shrinkContext,
        stage,
        SyntheticStageOwner.STAGE_AFTER
      )
    ]
  }

  StageBuilderProvider getStageBuilderProvider() {
    return applicationContext.getBean(StageBuilderProvider)
  }
}
