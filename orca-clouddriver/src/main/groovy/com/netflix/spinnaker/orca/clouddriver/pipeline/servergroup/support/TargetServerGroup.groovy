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

package com.netflix.spinnaker.orca.clouddriver.pipeline.servergroup.support

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.frigga.Names
import com.netflix.spinnaker.moniker.Moniker
import com.netflix.spinnaker.orca.kato.pipeline.support.StageData
import com.netflix.spinnaker.orca.pipeline.model.Stage
import groovy.transform.InheritConstructors
import groovy.transform.ToString
import groovy.util.logging.Slf4j

/**
 * A TargetServerGroup is a ServerGroup that is dynamically resolved using a target like "current" or "oldest".
 */
class TargetServerGroup {

  final static ObjectMapper objectMapper = new ObjectMapper()

  // Delegates all Map interface calls to this object.
  @Delegate
  private final Map<String, Object> serverGroup

  TargetServerGroup(Map<String, Object> serverGroupData) {
    if (serverGroupData.instances && serverGroupData.instances instanceof Collection) {
      serverGroupData.instances = serverGroupData.instances.collect {
        [name: it.name, launchTime: it.launchTime, health: it.health, healthState: it.healthState, zone: it.zone]
      }
    }
    if (serverGroupData.containsKey("asg") && serverGroupData.asg instanceof Map) {
      ((Map) serverGroupData.get("asg")).remove("instances")
    }
    serverGroup = new HashMap(serverGroupData).asImmutable()
  }

  /**
   * All invocations of this method should use the full 'getLocation()' signature, instead of the shorthand dot way
   * (i.e. "serverGroup.location"). Otherwise, the property 'location' is looked for in the serverGroup map, which is
   * very likely not there.
   */
  Location getLocation(Location.Type exactLocationType = null) {
    return Support.locationFromServerGroup(serverGroup, exactLocationType)
  }

  /**
   * Used in TrafficGuard, which is Java, which doesn't play nice with @Delegate
   */
  String getName() {
    return serverGroup.name
  }

  /**
   * Used in TrafficGuard, which is Java, which doesn't play nice with @Delegate
   */
  Boolean isDisabled() {
    return serverGroup.isDisabled == null ? serverGroup.disabled : serverGroup.isDisabled
  }

  /**
   * Used in TrafficGuard, which is Java, which doesn't play nice with @Delegate
   */
  List<Map> getInstances() {
    return (serverGroup.instances ?: []) as List<Map>
  }

  /**
   * Used in TrafficGuard, which is Java, which doesn't play nice with @Delegate
   */
  Moniker getMoniker() {
    return serverGroup?.moniker ? objectMapper.convertValue(serverGroup?.moniker, Moniker) : null
  }

  String getCloudProvider() {
    serverGroup.cloudProvider ?: serverGroup.type
  }

  /**
   * Used in UpsertGceAutoscalingPolicy, which is Java, which doesn't play nice with @Delegate
   * @return
   */
  Map<String, Object> getAutoscalingPolicy() {
    return (Map<String, Object>) serverGroup.autoscalingPolicy
  }

  Map toClouddriverOperationPayload(String account) {
    //TODO(cfieber) - add an endpoint on Clouddriver to do provider appropriate conversion of a TargetServerGroup
    def op = [
      credentials    : account,
      accountName    : account,
      serverGroupName: serverGroup.name,
      asgName        : serverGroup.name,
      cloudProvider  : serverGroup.cloudProvider ?: serverGroup.type
    ]

    def loc = getLocation()
    switch (loc.type) {
      case (Location.Type.NAMESPACE):
        op.namespace = loc.value
        break
      case (Location.Type.REGION):
        op.region = loc.value
        break
      case (Location.Type.ZONE):
        op.zone = loc.value
        break
      default:
        throw new IllegalStateException("unsupported location type $loc.type")
    }
    return op
  }

  @Override
  public String toString() {
    "TargetServerGroup$serverGroup"
  }

  public static class Support {
    static Location resolveLocation(String namespace, String region, String zone) {
      if (namespace) {
        return Location.namespace(namespace)
      } else if (region) {
        return Location.region(region)
      } else if (zone) {
        return Location.zone(zone)
      } else {
        throw new IllegalArgumentException("No known location type provided. Must be `namespace`, `region` or `zone`.")
      }
    }

    static Location locationFromServerGroup(Map<String, Object> serverGroup, Location.Type exactLocationType) {
      switch (exactLocationType) {
        case (Location.Type.NAMESPACE):
          return Location.namespace(serverGroup.namespace)
        case (Location.Type.REGION):
          return Location.region(serverGroup.region)
        case (Location.Type.ZONE):
          return Location.zone(serverGroup.zone)
      }

      try {
        return resolveLocation(serverGroup.namespace, serverGroup.region, serverGroup.zone)
      } catch (e) {
        throw new IllegalArgumentException("Incorrect location specified for ${serverGroup.serverGroupName ?: serverGroup.name}: ${e.message}")
      }
    }

    static Location locationFromOperation(Map<String, Object> operation) {
      if (!operation.targetLocation) {
        return null
      }
      new Location(type: Location.Type.valueOf(operation.targetLocation.type), value: operation.targetLocation.value)
    }

    static Location locationFromStageData(StageData stageData) {
      try {
        List zones = stageData.availabilityZones?.values()?.flatten()?.toArray()
        return resolveLocation(stageData.namespace, stageData.region, zones?.getAt(0))
      } catch (e) {
        throw new IllegalArgumentException("Incorrect location specified for ${stageData}: ${e.message}")
      }
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
      /**
       * "Newest Server Group"
       */
      current_asg_dynamic,
      /**
       * "Previous Server Group"
       */
        ancestor_asg_dynamic,
      /**
       * "Oldest Server Group"
       */
        oldest_asg_dynamic,
      /**
       * "(Deprecated) Current Server Group"
       */
        @Deprecated
        current_asg,
      /**
       * "(Deprecated) Last Server Group"
       */
        @Deprecated
        ancestor_asg,

      boolean isDynamic() {
        return this.name().endsWith("dynamic")
      }
    }

    // serverGroupName used when specifically targeting a server group
    // TODO(ttomsu): This feels dirty - consider structuring to enable an 'exact' Target that just specifies the exact
    // server group name to fetch?
    String serverGroupName
    Moniker moniker

    // Alternatively to asgName, the combination of target and cluster can be used.
    Target target
    String cluster

    String credentials
    List<Location> locations
    String cloudProvider = "aws"

    String getApp() {
      moniker?.app ?: Names.parseName(serverGroupName ?: cluster)?.app
    }

    String getCluster() {
      moniker?.cluster ?: cluster ?: Names.parseName(serverGroupName)?.cluster
    }

    static Params fromStage(Stage stage) {
      Params p = stage.mapTo(Params)

      if (stage.context.region) {
        p.locations = [Location.region(stage.context.region)]
      } else if (stage.context.regions) {
        p.locations = stage.context.regions.collect { String r -> Location.region(r) }
      } else if (stage.context.namespace) {
        p.locations = [Location.namespace(stage.context.namespace)]
      } else if (stage.context.namespaces) {
        p.locations = stage.context.namespaces.collect { String n -> Location.namespace(n) }
      } else if (stage.context.cloudProvider == "gce" && stage.context.zones) {
        p.locations = stage.context.zones.collect { String z -> Location.zone(z) }
      } else {
        p.locations = []
      }
      p
    }
  }

  @InheritConstructors
  static class NotFoundException extends RuntimeException {}
}
