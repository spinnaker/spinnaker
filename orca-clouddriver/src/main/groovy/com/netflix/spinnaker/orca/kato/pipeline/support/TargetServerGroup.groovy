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

import com.netflix.frigga.Names
import com.netflix.spinnaker.orca.pipeline.model.Stage
import groovy.transform.InheritConstructors
import groovy.transform.ToString

/**
 * A TargetServerGroup is a ServerGroup that is dynamically resolved using a target like "current" or "oldest".
 */
@ToString(includeNames = true)
class TargetServerGroup {
  String cluster
  String location

  /**
   * serverGroup is here to enable any need to reach into the ServerGroup for a specific property. It is strongly
   * recommended to pull properties from this object into a top level field to help with code readability and reduce
   * brittleness.
   */
  Map<String, Object> serverGroup

  static boolean isDynamicallyBound(Stage stage) {
    Params.fromStage(stage).target?.isDynamic()
  }

  /**
   * A Params object is used to define the required parameters to resolve a TargetServerGroup.
   */
  @ToString(includeNames = true)
  static class Params {
    /**
     * These are all lower case because we expect them to be defined in the pipeline as lowercase.
     */
    enum Target {
      current_asg_dynamic,
      ancestor_asg_dynamic,
      oldest_asg_dynamic,
      @Deprecated current_asg,
      @Deprecated ancestor_asg,

      boolean isDynamic() {
        return this.name().endsWith("dynamic")
      }
    }

    // asgName used when specifically targeting a server group
    // TODO(ttomsu): This feels dirty - consider structuring to enable an 'exact' Target that just specifies the exact
    // server group name to fetch?
    String asgName

    // Alternatively to asgName, the combination of target and cluster can be used.
    Target target
    String cluster

    String credentials
    List<String> locations // regions or zones.
    String cloudProvider

    String getApp() {
      Names.parseName(asgName ?: cluster)?.app
    }

    String getCluster() {
      cluster ?: Names.parseName(asgName)?.cluster
    }

    static Params fromStage(Stage stage) {
      Params p = stage.mapTo(Params)
      def loc
      // TODO(ttomsu): Remove this condition when Deck no longer sends both zones and regions for both GCE & AWS
      if (stage.context.regions && stage.context.zones) {
        if (stage.context.cloudProvider == "gce") {
          loc = stage.context.zones
        } else {
          loc = stage.context.regions
        }
      } else {
        loc = (List) (stage.context.zones ?: stage.context.regions ?: [])
      }
      p.locations = loc
      p
    }
  }

  @InheritConstructors
  static class NotFoundException extends RuntimeException{}
}
