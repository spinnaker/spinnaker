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


package com.netflix.spinnaker.orca.kato.pipeline.support

import com.netflix.spinnaker.orca.clouddriver.pipeline.servergroup.support.Location
import com.netflix.spinnaker.orca.clouddriver.pipeline.servergroup.support.TargetServerGroup
import com.netflix.spinnaker.orca.clouddriver.utils.OortHelper
import com.netflix.spinnaker.orca.pipeline.model.Stage
import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

@Component
@Slf4j
class ScaleToServerGroupResizeStrategy implements ResizeStrategy {

  @Autowired
  OortHelper oortHelper

  @Autowired
  ResizeStrategySupport resizeStrategySupport

  @Override
  boolean handles(ResizeStrategy.ResizeAction resizeAction) {
    resizeAction == ResizeStrategy.ResizeAction.scale_to_server_group
  }

  @Override
  ResizeStrategy.CapacitySet capacityForOperation(Stage stage,
                                                  String account,
                                                  String serverGroupName,
                                                  String cloudProvider,
                                                  Location location,
                                                  ResizeStrategy.OptionalConfiguration resizeConfig) {
    if (!stage.context.source) {
      throw new IllegalStateException("No source configuration available (${stage.context})")
    }

    ResizeStrategy.StageData stageData = stage.mapTo(StageData)
    ResizeStrategy.Source source = stageData.source
    def sourceCapacity = resizeStrategySupport.getCapacity(source.credentials,
      source.serverGroupName, source.cloudProvider, source.location)

    return new ResizeStrategy.CapacitySet(
      sourceCapacity,
      resizeStrategySupport.performScalingAndPinning(sourceCapacity, stage, resizeConfig))
  }
}
