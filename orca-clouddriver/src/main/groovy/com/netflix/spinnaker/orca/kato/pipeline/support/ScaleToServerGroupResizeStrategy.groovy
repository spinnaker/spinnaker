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
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

@Component
class ScaleToServerGroupResizeStrategy implements ResizeStrategy {

  @Autowired
  OortHelper oortHelper

  @Override
  boolean handles(ResizeStrategy.ResizeAction resizeAction) {
    resizeAction == ResizeStrategy.ResizeAction.scale_to_server_group
  }

  @Override
  ResizeStrategy.Capacity capacityForOperation(Stage stage,
                                               String account,
                                               String serverGroupName,
                                               String cloudProvider,
                                               Location location,
                                               ResizeStrategy.OptionalConfiguration resizeConfig) {
    if (!stage.context.source) {
      throw new IllegalStateException("No source configuration available (${stage.context})")
    }

    StageData stageData = stage.mapTo(StageData)
    ResizeStrategy.Source source = stageData.source
    TargetServerGroup tsg = oortHelper
      .getTargetServerGroup(source.credentials, source.serverGroupName, source.location, source.cloudProvider)
      .orElseThrow({
      source.with {
        new IllegalStateException("no server group found $cloudProvider/$account/$serverGroupName in $location")
      }
    })

    def currentMin = Integer.parseInt(tsg.capacity.min.toString())
    def currentDesired = Integer.parseInt(tsg.capacity.desired.toString())
    def currentMax = Integer.parseInt(tsg.capacity.max.toString())

    def scalePct = resizeConfig.scalePct
    if (scalePct != null) {
      double factor = scalePct / 100.0d

      // scalePct only applies to the desired capacity
      currentDesired = (Integer) Math.ceil(currentDesired * factor)

      // min capacity may need adjusting iff scalePct pushed desired below current min
      currentMin = Math.min(currentMin, currentDesired)
    }

    if (stageData.pinCapacity) {
      return new ResizeStrategy.Capacity(
        currentDesired,
        currentDesired,
        currentDesired
      )
    }

    return new ResizeStrategy.Capacity(
      currentMax,
      currentDesired,
      stageData.pinMinimumCapacity ? currentDesired : currentMin
    )
  }

  private static class StageData {
    ResizeStrategy.Source source

    // whether or not `min` capacity should be set to `desired` capacity
    boolean pinMinimumCapacity

    boolean pinCapacity
  }
}
