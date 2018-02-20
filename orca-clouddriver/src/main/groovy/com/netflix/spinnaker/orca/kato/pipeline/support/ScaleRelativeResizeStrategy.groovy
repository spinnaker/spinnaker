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
class ScaleRelativeResizeStrategy implements ResizeStrategy {

  @Autowired
  OortHelper oortHelper

  @Override
  boolean handles(ResizeAction resizeAction) {
    resizeAction == ResizeAction.scale_down || resizeAction == ResizeAction.scale_up
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
    def originalCapacity = new Capacity(currentMax, currentDesired, currentMin)

    int sign = resizeConfig.action == ResizeAction.scale_up ? 1 : -1

    if (resizeConfig.scalePct) {
      double factor = resizeConfig.scalePct / 100.0d
      def minDiff = sign * Math.ceil(currentMin * factor)
      def desiredDiff = sign * Math.ceil(currentDesired * factor)
      def maxDiff = sign * Math.ceil(currentMax * factor)
      return new CapacitySet(
        originalCapacity,
        new Capacity(
          min: Math.max(currentMin + minDiff, 0),
          desired: Math.max(currentDesired + desiredDiff, 0),
          max: Math.max(currentMax + maxDiff, 0)
        )
      )
    } else if (resizeConfig.scaleNum) {
      int delta = sign * resizeConfig.scaleNum
      return new CapacitySet(
        originalCapacity,
        new Capacity(
          min: Math.max(currentMin + delta, 0),
          desired: Math.max(currentDesired + delta, 0),
          max: Math.max(currentMax + delta, 0)
        )
      )
    } else {
      return new CapacitySet(
        originalCapacity,
        new Capacity(
          min: currentMin,
          desired: currentDesired,
          max: currentMax
        )
      )
    }
  }
}
