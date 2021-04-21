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

package com.netflix.spinnaker.orca.clouddriver.pipeline.servergroup.support;

import com.netflix.frigga.Names;
import com.netflix.spinnaker.moniker.Moniker;
import com.netflix.spinnaker.orca.api.pipeline.models.StageExecution;
import com.netflix.spinnaker.orca.clouddriver.model.Instance;
import com.netflix.spinnaker.orca.clouddriver.model.ServerGroup;
import com.netflix.spinnaker.orca.clouddriver.model.ServerGroup.Asg;
import com.netflix.spinnaker.orca.clouddriver.pipeline.servergroup.support.Location.Type;
import com.netflix.spinnaker.orca.jackson.OrcaObjectMapper;
import com.netflix.spinnaker.orca.kato.pipeline.support.StageData;
import java.util.*;
import java.util.stream.Collectors;
import lombok.Data;

/**
 * A TargetServerGroup is a ServerGroup that is dynamically resolved using a target like "current"
 * or "oldest".
 */
public class TargetServerGroup {

  private final ServerGroup serverGroup;

  // Overloaded constructor should be fine for groovy since the old constructor would have thrown a
  // null pointer
  public TargetServerGroup(ServerGroup serverGroup) {
    this.serverGroup = serverGroup;
  }

  public TargetServerGroup(Map<String, Object> serverGroupData) {
    serverGroup = OrcaObjectMapper.getInstance().convertValue(serverGroupData, ServerGroup.class);
  }

  public Collection<String> getSuspendedProcesses() {
    return Optional.ofNullable(serverGroup.getAsg())
        .map(Asg::getSuspendedProcesses)
        .map(
            processes ->
                processes.stream()
                    .map(ServerGroup.Process::getProcessName)
                    .collect(Collectors.toList()))
        .orElse(null);
  }

  /**
   * All invocations of this method should use the full 'getLocation()' signature, instead of the
   * shorthand dot way (i.e. "serverGroup.location"). Otherwise, the property 'location' is looked
   * for in the serverGroup map, which is very likely not there.
   */
  public Location getLocation() {
    return getLocation(null);
  }

  public Location getLocation(Type exactLocationType) {
    return Support.locationFromServerGroup(serverGroup, exactLocationType);
  }

  public String getName() {
    return serverGroup.getName();
  }

  public String getRegion() {
    return serverGroup.getRegion();
  }

  public Capacity getCapacity() {
    return Capacity.builder()
        .min(toInt(serverGroup.capacity.min))
        .max(toInt(serverGroup.capacity.max))
        .desired(toInt(serverGroup.capacity.desired))
        .build();
  }

  public Asg getAsg() {
    return serverGroup.getAsg();
  }

  public String getCredentials() { // TODO: is type String?
    return serverGroup.getCredentials();
  }

  public Long getCreatedTime() {
    return serverGroup.getCreatedTime();
  }

  private static int toInt(Object field) {
    return Integer.parseInt(field.toString());
  }

  public Boolean isDisabled() {
    return serverGroup.getDisabled();
  }

  public List<Instance> getInstances() {
    List<Instance> result = serverGroup.getInstances();
    return result != null ? result : List.of();
  }

  public Moniker getMoniker() {
    return serverGroup.getMoniker();
  }

  public String getCloudProvider() {
    String result = serverGroup.getCloudProvider();
    return result != null ? result : serverGroup.getType();
  }

  public Map<String, Object> getAutoscalingPolicy() {
    return serverGroup.getAutoscalingPolicy();
  }

  public Map<String, Object> toClouddriverOperationPayload(String account) {
    // TODO(cfieber) - add an endpoint on Clouddriver to do provider appropriate conversion of a
    // TargetServerGroup
    Map<String, Object> op = new HashMap<>();
    op.put("credentials", account);
    op.put("accountName", account);
    op.put("serverGroupName", getName());
    op.put("asgName", getName());
    op.put("cloudProvider", getCloudProvider());

    Location loc = getLocation();
    switch (loc.getType()) {
      case NAMESPACE:
        op.put("namespace", loc.getValue());
        break;
      case REGION:
        op.put("region", loc.getValue());
        break;
      case ZONE:
        op.put("zone", loc.getValue());
        break;
      default:
        throw new IllegalStateException("unsupported location type " + loc.getType());
    }
    return op;
  }

  @Override
  public String toString() {
    return "TargetServerGroup" + serverGroup.toString();
  }

  public static class Support {
    static Location resolveLocation(String namespace, String region, String zone) {
      if (namespace != null) {
        return Location.namespace(namespace);
      } else if (region != null) {
        return Location.region(region);
      } else if (zone != null) {
        return Location.zone(zone);
      } else {
        throw new IllegalArgumentException(
            "No known location type provided. Must be `namespace`, `region` or `zone`.");
      }
    }

    static Location locationFromServerGroup(ServerGroup serverGroup, Type exactLocationType) {
      if (exactLocationType != null) {
        switch (exactLocationType) {
          case NAMESPACE:
            return Location.namespace(serverGroup.getNamespace());
          case REGION:
            return Location.region(serverGroup.getRegion());
          case ZONE:
            return Location.zone(serverGroup.getZone());
        }
      }

      try {
        return resolveLocation(
            serverGroup.getNamespace(), serverGroup.getRegion(), serverGroup.getZone());
      } catch (Exception e) {
        String sg = serverGroup.getServerGroupName();
        if (sg == null) {
          sg = serverGroup.getName();
        }
        throw new IllegalArgumentException(
            String.format("Incorrect location specified for %s: %s", sg, e.getMessage()));
      }
    }

    public static Location locationFromStageData(StageData stageData) {
      try {
        String zone =
            Optional.ofNullable(stageData.getAvailabilityZones())
                .flatMap(
                    azs ->
                        azs.values().stream()
                            .filter(Objects::nonNull)
                            .flatMap(List::stream)
                            .findFirst())
                .orElse(null);
        return resolveLocation(stageData.getNamespace(), stageData.getRegion(), zone);
      } catch (Exception e) {
        throw new IllegalArgumentException(
            String.format("Incorrect location specified for %s: %s", stageData, e.getMessage()));
      }
    }
  }

  public static boolean isDynamicallyBound(StageExecution stage) {
    var target = Params.fromStage(stage).target;
    return target != null && target.isDynamic();
  }

  /** A Params object is used to define the required parameters to resolve a TargetServerGroup. */
  @Data
  public static class Params {
    /**
     * These are all lower case because we expect them to be defined in the pipeline as lowercase.
     */
    public enum Target {
      /** "Newest Server Group" */
      current_asg_dynamic,
      /** "Previous Server Group" */
      ancestor_asg_dynamic,
      /** "Oldest Server Group" */
      oldest_asg_dynamic,
      /** "(Deprecated) Current Server Group" */
      @Deprecated
      current_asg,
      /** "(Deprecated) Last Server Group" */
      @Deprecated
      ancestor_asg;

      public boolean isDynamic() {
        return name().endsWith("dynamic");
      }
    }

    // serverGroupName used when specifically targeting a server group
    // TODO(ttomsu): This feels dirty - consider structuring to enable an 'exact' Target that just
    // specifies the exact
    // server group name to fetch?
    String serverGroupName;
    Moniker moniker;

    // Alternatively to asgName, the combination of target and cluster can be used.
    Target target;
    private String cluster;

    String credentials;
    List<Location> locations;
    String cloudProvider = "aws";

    public String getApp() {
      if (moniker != null && moniker.getApp() != null) {
        return moniker.getApp();
      }
      return Names.parseName(serverGroupName != null ? serverGroupName : getCluster()).getApp();
    }

    public String getCluster() {
      if (moniker != null && moniker.getCluster() != null) {
        return moniker.getCluster();
      }
      if (cluster != null) {
        return cluster;
      }
      return Names.parseName(serverGroupName).getCluster();
    }

    public static Params fromStage(StageExecution stage) {
      Params p = stage.mapTo(Params.class);

      var context = stage.getContext();

      if (context.get("region") != null) {
        p.locations = List.of(Location.region((String) context.get("region")));
      } else if (context.get("regions") instanceof Collection) {
        p.locations =
            ((Collection<String>) context.get("regions"))
                .stream().map(Location::region).collect(Collectors.toList());
      } else if (context.get("namespace") != null) {
        p.locations = List.of(Location.namespace((String) context.get("namespace")));
      } else if (context.get("namespaces") instanceof Collection) {
        p.locations =
            ((Collection<String>) context.get("namespaces"))
                .stream().map(Location::namespace).collect(Collectors.toList());
      } else if ("gce".equals(context.get("cloudProvider"))
          && context.get("zones") instanceof Collection) {
        p.locations =
            ((Collection<String>) context.get("zones"))
                .stream().map(Location::zone).collect(Collectors.toList());
      } else {
        p.locations = List.of();
      }
      return p;
    }
  }

  public ServerGroup toServerGroup() {
    return serverGroup;
  }

  public static class NotFoundException extends RuntimeException {
    public NotFoundException() {}

    public NotFoundException(String message) {
      super(message);
    }
  }
}
