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

import com.netflix.spinnaker.orca.kato.pipeline.support.ResizeStrategy.Capacity
import com.netflix.spinnaker.orca.kato.pipeline.support.ResizeStrategy.OptionalConfiguration
import com.netflix.spinnaker.orca.kato.pipeline.support.ResizeStrategy.ResizeAction
import com.netflix.spinnaker.orca.pipeline.model.Stage
import groovy.transform.CompileDynamic
import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

@Component
@Slf4j
@Deprecated
class ResizeSupport {

  @Autowired
  TargetReferenceSupport targetReferenceSupport

  @CompileDynamic
  @Deprecated
  public List<Map<String, Object>> createResizeStageDescriptors(Stage stage, List<TargetReference> targetReferences) {
    def optionalConfig = stage.mapTo(OptionalConfiguration)
    Map<String, Map<String, Object>> descriptions = [:]

    for (TargetReference target : targetReferences) {
      def region = target.region
      def asg = target.asg
      if (!asg && targetReferenceSupport.isDynamicallyBound(stage)) {
        log.info("Skipping target reference because the stage is not dynamic and there is no ASG defined")
        continue
      }

      def description = new HashMap(stage.context)
      if (descriptions.containsKey(asg.name)) {
        descriptions[asg.name as String].regions.add(region)
        continue
      }
      description.asgName = asg.name
      description.regions = [asg.region]

      def currentMin = Integer.parseInt(asg.asg.minSize.toString())
      def currentDesired = Integer.parseInt(asg.asg.desiredCapacity.toString())
      def currentMax = Integer.parseInt(asg.asg.maxSize.toString())

      Integer newMin, newDesired, newMax
      if (optionalConfig.scalePct) {
        def factor = optionalConfig.scalePct / 100
        def minDiff = Math.ceil(currentMin * factor)
        def desiredDiff = Math.ceil(currentDesired * factor)
        def maxDiff = Math.ceil(currentMax * factor)
        newMin = currentMin + minDiff
        newDesired = currentDesired + desiredDiff
        newMax = currentMax + maxDiff

        if (optionalConfig.action == ResizeAction.scale_down) {
          newMin = Math.max(currentMin - minDiff, 0)
          newDesired = Math.max(currentDesired - desiredDiff, 0)
          newMax = Math.max(currentMax - maxDiff, 0)
        }
      } else if (optionalConfig.scaleNum) {
        newMin = currentMin + optionalConfig.scaleNum
        newDesired = currentDesired + optionalConfig.scaleNum
        newMax = currentMax + optionalConfig.scaleNum

        if (optionalConfig.action == ResizeAction.scale_down) {
          newMin = Math.max(currentMin - optionalConfig.scaleNum, 0)
          newDesired = Math.max(currentDesired - optionalConfig.scaleNum, 0)
          newMax = Math.max(currentMax - optionalConfig.scaleNum, 0)
        }
      }

      if (newMin != null && newDesired != null && newMax != null) {
        description.capacity = [min: newMin, desired: newDesired, max: newMax]
      } else {
        def targetCapacity = stage.mapTo("/capacity", Capacity)
        def currentCapacity = new Capacity(currentMax, currentDesired, currentMin)
        def newCapacity = ScaleExactResizeStrategy.mergeConfiguredCapacityWithCurrent(targetCapacity, currentCapacity)
        description.capacity = [min: newCapacity.min, desired: newCapacity.desired, max: newCapacity.max]
      }

      descriptions[asg.name as String] = description
    }
    descriptions.values().flatten()
  }


}
