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

import com.netflix.spinnaker.orca.pipeline.model.Stage
import groovy.transform.CompileDynamic
import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

@Component
@Slf4j
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
        def capacity = stage.mapTo("/capacity", Capacity)
        description.capacity = mergeConfiguredCapacityWithCurrent(capacity, currentMin, currentDesired, currentMax)
      }

      descriptions[asg.name as String] = description
    }
    descriptions.values().flatten()
  }

  @CompileDynamic
  static public List<Map<String, Object>> createResizeDescriptors(Stage stage, List<TargetServerGroup> targetServerGroups) {
    def optionalConfig = stage.mapTo(OptionalConfiguration)
    Map<String, Map<String, Object>> operations = [:]

    for (TargetServerGroup tsg : targetServerGroups) {
      def location = tsg.location
      def serverGroup = tsg.serverGroup

      def operation = new HashMap(stage.context)
      if (operations.containsKey(serverGroup.name)) {
        // TODO(ttomsu): Multi-zone resizing for GCE, which means this 'regions' attribute will have to change, too.
        operations[serverGroup.name as String].regions.add(location)
        continue
      }
      operation.asgName = serverGroup.name
      operation.regions = [serverGroup.region]

      def currentMin = Integer.parseInt(serverGroup.capacity.min.toString())
      def currentDesired = Integer.parseInt(serverGroup.capacity.desired.toString())
      def currentMax = Integer.parseInt(serverGroup.capacity.max.toString())

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
        operation.capacity = [min: newMin, desired: newDesired, max: newMax]
      } else {
        def capacity = stage.mapTo("/capacity", Capacity)
        operation.capacity = mergeConfiguredCapacityWithCurrent(capacity, currentMin, currentDesired, currentMax)
      }

      // TODO(ttomsu): Remove cloud provider-specific operation.
      if (operation.provider == "gce" || operation.cloudProvider == "gce") {
        augmentDescriptionForGCE(operation, tsg)
      }
      operations[serverGroup.name as String] = operation
    }
    operations.values().flatten()
  }

  private static Map mergeConfiguredCapacityWithCurrent(Capacity configured, int currentMin, int currentDesired, int currentMax) {
    boolean minConfigured = configured.min != null;
    boolean desiredConfigured = configured.desired != null;
    boolean maxConfigured = configured.max != null;
    Map result = [
      min: minConfigured ? configured.min : currentMin,
    ]
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

  private static augmentDescriptionForGCE(Map description, TargetServerGroup tsg) {
    // TODO(ttomsu): Make clouddriver op support specifying multiple zones.
    description.zone = tsg.location
    description.targetSize = description.capacity.desired
    description.serverGroupName = description.asgName
  }

  static enum ResizeAction {
    scale_up, scale_down
  }

  static class OptionalConfiguration {
    ResizeAction action
    Integer scalePct
    Integer scaleNum
  }

  static class Capacity {
    Integer max
    Integer desired
    Integer min
  }
}
