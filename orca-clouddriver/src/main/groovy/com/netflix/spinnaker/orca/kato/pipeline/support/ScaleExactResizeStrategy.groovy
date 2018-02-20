/*
 * Copyright 2015 Netflix, Inc.
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

package com.netflix.spinnaker.orca.kato.pipeline.support

import com.netflix.spinnaker.orca.clouddriver.pipeline.servergroup.support.Location
import com.netflix.spinnaker.orca.clouddriver.pipeline.servergroup.support.TargetServerGroup
import com.netflix.spinnaker.orca.clouddriver.utils.OortHelper
import com.netflix.spinnaker.orca.pipeline.model.Stage
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

@Component
class ScaleExactResizeStrategy implements ResizeStrategy {

  @Autowired
  OortHelper oortHelper

  @Override
  boolean handles(ResizeAction resizeAction) {
    resizeAction == ResizeAction.scale_exact
  }

  @Override
  CapacitySet capacityForOperation(Stage stage,
                                   String account,
                                   String serverGroupName,
                                   String cloudProvider,
                                   Location location,
                                   OptionalConfiguration resizeConfig) {
    TargetServerGroup tsg = oortHelper.getTargetServerGroup(account, serverGroupName, location.value, cloudProvider)
      .orElseThrow({
      new IllegalStateException("no server group found $cloudProvider/$account/$serverGroupName in $location")
    })

    def currentMin = Integer.parseInt(tsg.capacity.min.toString())
    def currentDesired = Integer.parseInt(tsg.capacity.desired.toString())
    def currentMax = Integer.parseInt(tsg.capacity.max.toString())

    return new CapacitySet(
      new Capacity(currentMax, currentDesired, currentMin),
      mergeConfiguredCapacityWithCurrent(stage.mapTo("/capacity", Capacity), currentMin, currentDesired, currentMax)
    )
  }

  static Capacity mergeConfiguredCapacityWithCurrent(Capacity configured, int currentMin, int currentDesired, int currentMax) {
    boolean minConfigured = configured.min != null;
    boolean desiredConfigured = configured.desired != null;
    boolean maxConfigured = configured.max != null;
    Capacity result = new Capacity(
      min: minConfigured ? configured.min : currentMin,
    )
    if (maxConfigured) {
      result.max = configured.max
      result.min = Math.min(result.min, configured.max)
    } else {
      result.max = Math.max(result.min, currentMax)
    }
    if (desiredConfigured) {
      result.desired = configured.desired
    } else {
      result.desired = currentDesired
      if (currentDesired < result.min) {
        result.desired = result.min
      }
      if (currentDesired > result.max) {
        result.desired = result.max
      }
    }

    result
  }
}
