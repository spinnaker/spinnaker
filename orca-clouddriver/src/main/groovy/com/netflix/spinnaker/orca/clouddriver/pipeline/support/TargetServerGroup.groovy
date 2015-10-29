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

package com.netflix.spinnaker.orca.clouddriver.pipeline.support

import com.netflix.frigga.Names
import com.netflix.spinnaker.orca.kato.pipeline.support.StageData
import com.netflix.spinnaker.orca.pipeline.model.Stage
import groovy.transform.InheritConstructors
import groovy.transform.ToString
import groovy.util.logging.Slf4j

/**
 * A TargetServerGroup is a ServerGroup that is dynamically resolved using a target like "current" or "oldest".
 */
@ToString(includeNames = true)
class TargetServerGroup {
  // Delegates all Map interface calls to this object.
  @Delegate Map<String, Object> serverGroup = [:]

  /**
   * All invocations of this method should use the full 'getLocation()' signature, instead of the shorthand dot way
   * (i.e. "serverGroup.location"). Otherwise, the property 'location' is looked for in the serverGroup map, which is
   * very likely not there.
   */
  Location getLocation() {
    return Support.locationFromServerGroup(serverGroup)
  }

  Map toClouddriverOperationPayload(String account) {
    //TODO(cfieber) - add an endpoint on Clouddriver to do provider appropriate conversion of a TargetServerGroup
    def op = [
      credentials: account,
      accountName: account,
      serverGroupName: serverGroup.name,
      asgName: serverGroup.name,
      cloudProvider: serverGroup.type,
      providerType: serverGroup.type
    ]

    def loc = getLocation()
    if (loc.type == Location.Type.REGION) {
      op.region = loc.value
      op.regions = [loc.value]
    } else if (loc.type == Location.Type.ZONE) {
      op.zone = loc.value
      op.zones = [loc.value]
    } else {
      throw new IllegalStateException("unsupported location type $loc.type")
    }
    return op
  }

  public static class Support {
    static Location locationFromServerGroup(Map<String, Object> serverGroup) {
      // All Google server group operations currently work with zones, not regions.
      if (serverGroup.type == "gce") {
        return new Location(type: Location.Type.ZONE, value: serverGroup.zones[0])
      }
      return new Location(type: Location.Type.REGION, value: serverGroup.region)
    }

    static Location locationFromStageData(StageData stageData) {
      if (stageData.cloudProvider == "gce") {
        def zones = stageData.availabilityZones.values().flatten().toArray()
        if (!zones) {
          throw new IllegalStateException("Cannot find GCE zones in stage data ${stageData}")
        }
        return new Location(type: Location.Type.ZONE, value: zones[0])
      }
      return new Location(type: Location.Type.REGION, value: stageData.region)
    }

    static Location locationFromCloudProviderValue(String cloudProvider, String value) {
      Location.Type type = cloudProvider == 'gce' ? Location.Type.ZONE : Location.Type.REGION
      return new Location(type: type, value: value)
    }
  }
  static boolean isDynamicallyBound(Stage stage) {
    Params.fromStage(stage).target?.isDynamic()
  }

  /**
   * A Params object is used to define the required parameters to resolve a TargetServerGroup.
   */
  @ToString(includeNames = true)
  @Slf4j
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
    List<Location> locations
    String cloudProvider = "aws"

    String getApp() {
      Names.parseName(asgName ?: cluster)?.app
    }

    String getCluster() {
      cluster ?: Names.parseName(asgName)?.cluster
    }

    static Params fromStage(Stage stage) {
      Params p = stage.mapTo(Params)

      def toZones = { String z ->
        return new Location(type: Location.Type.ZONE, value: z)
      }
      def toRegions = { String r ->
        return new Location(type: Location.Type.REGION, value: r)
      }

      if (stage.context.zones) {
        if (stage.context.regions && stage.context.cloudProvider != "gce") {
          // Prefer regions if both are specified, except for GCE.
          p.locations = stage.context.regions.collect(toRegions)
        } else {
          p.locations = stage.context.zones.collect(toZones)
        }
      } else {
        // Default to regions.
        p.locations = stage.context.regions.collect(toRegions)
      }
      p
    }
  }

  @InheritConstructors
  static class NotFoundException extends RuntimeException {}
}
