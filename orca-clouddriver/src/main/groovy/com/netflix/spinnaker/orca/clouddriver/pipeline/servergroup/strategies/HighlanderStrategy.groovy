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

import com.netflix.spinnaker.orca.clouddriver.pipeline.cluster.ShrinkClusterStage
import com.netflix.spinnaker.orca.pipeline.LinearStage
import com.netflix.spinnaker.orca.pipeline.model.Stage
import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

@Component
@Slf4j
class HighlanderStrategy implements Strategy {

  final String name = "highlander"

  @Autowired
  ShrinkClusterStage shrinkClusterStage

  @Override
  void composeFlow(Stage stage) {
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
    LinearStage.injectAfter(stage, "shrinkCluster", shrinkClusterStage, shrinkContext)
  }
}
