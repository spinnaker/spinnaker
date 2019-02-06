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
import com.netflix.spinnaker.orca.pipeline.model.Stage
import groovy.transform.Canonical

interface ResizeStrategy {
  static enum ResizeAction {
    scale_exact, scale_up, scale_down, scale_to_cluster, scale_to_server_group
  }

  static class OptionalConfiguration {
    ResizeAction action
    Integer scalePct
    Integer scaleNum
    String resizeType

    //Temporary shim to support old style configuration where scale_exact was not an action
    //TODO(cfieber) - remove this after we've rolled this in and don't need to roll back
    ResizeAction getActualAction() {
      if (resizeType == 'exact') {
        return ResizeAction.scale_exact
      }
      // resize Orchestration doesn't provide action currently
      if (action == null) {
        return ResizeAction.scale_exact
      }
      return action
    }
  }

  @Canonical
  static class CapacitySet {
    Capacity original
    Capacity target
  }

  @Canonical
  static class Capacity {
    Integer max
    Integer desired
    Integer min
  }

  @Canonical
  static class Source implements Serializable {
    Collection<String> zones
    Collection<String> regions
    String region
    String zone

    String serverGroupName
    String credentials
    String cloudProvider

    public String getLocation() {
      def location = region ?: (regions ? regions[0] : null)

      if (!location) {
        location = zone ?: (zones ? zones[0] : null)
      }

      return location
    }
  }

  static class StageData {
    ResizeStrategy.Source source

    // whether or not `min` capacity should be set to `desired` capacity
    boolean pinMinimumCapacity
    boolean unpinMinimumCapacity = false
    boolean pinCapacity
  }

  boolean handles(ResizeAction resizeAction)
  CapacitySet capacityForOperation(Stage stage, String account, String serverGroupName, String cloudProvider, Location location, OptionalConfiguration resizeConfig)
}
