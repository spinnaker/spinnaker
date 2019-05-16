/*
 * Copyright 2017 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.orca.clouddriver.utils;

import static java.lang.String.format;

import com.google.common.collect.ImmutableMap;
import com.netflix.spectator.api.Id;
import com.netflix.spectator.api.Registry;
import com.netflix.spectator.impl.Preconditions;
import com.netflix.spinnaker.kork.dynamicconfig.DynamicConfigService;
import com.netflix.spinnaker.moniker.Moniker;
import com.netflix.spinnaker.orca.clouddriver.pipeline.servergroup.support.Location;
import com.netflix.spinnaker.orca.clouddriver.pipeline.servergroup.support.TargetServerGroup;
import com.netflix.spinnaker.orca.front50.Front50Service;
import com.netflix.spinnaker.orca.front50.model.Application;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import retrofit.RetrofitError;

@Component
public class TrafficGuard {
  private static final String MIN_CAPACITY_RATIO = "traffic-guards.min-capacity-ratio";
  private final Logger log = LoggerFactory.getLogger(getClass());

  private final OortHelper oortHelper;
  private final Front50Service front50Service;
  private final Registry registry;
  private final DynamicConfigService dynamicConfigService;

  private final Id savesId;

  @Autowired
  public TrafficGuard(
      OortHelper oortHelper,
      Optional<Front50Service> front50Service,
      Registry registry,
      DynamicConfigService dynamicConfigService) {
    this.oortHelper = oortHelper;
    this.front50Service = front50Service.orElse(null);
    this.registry = registry;
    this.dynamicConfigService = dynamicConfigService;
    this.savesId = registry.createId("trafficGuard.saves");
  }

  public void verifyInstanceTermination(
      String serverGroupNameFromStage,
      Moniker serverGroupMonikerFromStage,
      List<String> instanceIds,
      String account,
      Location location,
      String cloudProvider,
      String operationDescriptor) {
    // TODO rz - Expose a single `instance server groups` endpoint in clouddriver; returns a
    // map<instanceid, servergroup>.
    Map<String, List<String>> instancesPerServerGroup = new HashMap<>();
    for (String instanceId : instanceIds) {
      String serverGroupName = serverGroupNameFromStage;
      if (serverGroupName == null) {
        Optional<String> resolvedServerGroupName =
            resolveServerGroupNameForInstance(
                instanceId, account, location.getValue(), cloudProvider);
        serverGroupName = resolvedServerGroupName.orElse(null);
      }

      if (serverGroupName != null) {
        instancesPerServerGroup
            .computeIfAbsent(serverGroupName, serverGroup -> new ArrayList<>())
            .add(instanceId);
      }
    }

    instancesPerServerGroup
        .entrySet()
        .forEach(
            entry -> {
              String serverGroupName = entry.getKey();

              // handle scenarios where the stage moniker is invalid (ie. stage had no server group
              // details provided)
              Moniker moniker =
                  serverGroupMonikerFromStage.getApp() == null
                      ? MonikerHelper.friggaToMoniker(serverGroupName)
                      : serverGroupMonikerFromStage;

              // TODO rz - Remove: No longer needed since all data is retrieved in above clouddriver
              // calls
              TargetServerGroup targetServerGroup =
                  oortHelper
                      .getTargetServerGroup(
                          account, serverGroupName, location.getValue(), cloudProvider)
                      .orElseThrow(
                          () ->
                              new TrafficGuardException(
                                  format(
                                      "failed to look up server group named %s in %s/%s",
                                      serverGroupName, account, location)));

              Optional<Map> thisInstance =
                  targetServerGroup.getInstances().stream()
                      .filter(i -> "Up".equals(i.get("healthState")))
                      .findFirst();
              if (thisInstance.isPresent()) {
                long otherActiveInstances =
                    targetServerGroup.getInstances().stream()
                        .filter(
                            i ->
                                "Up".equals(i.get("healthState"))
                                    && !entry.getValue().contains(i.get("name")))
                        .count();
                if (otherActiveInstances == 0) {
                  verifyTrafficRemoval(
                      serverGroupName,
                      moniker,
                      account,
                      location,
                      cloudProvider,
                      operationDescriptor);
                }
              }
            });
  }

  private Optional<String> resolveServerGroupNameForInstance(
      String instanceId, String account, String region, String cloudProvider) {
    List<Map> searchResults =
        (List<Map>)
            oortHelper
                .getSearchResults(instanceId, "instances", cloudProvider)
                .get(0)
                .getOrDefault("results", new ArrayList<>());
    Optional<Map> instance =
        searchResults.stream()
            .filter(r -> account.equals(r.get("account")) && region.equals(r.get("region")))
            .findFirst();
    // instance not found, assume it's already terminated, what could go wrong
    return Optional.ofNullable((String) instance.orElse(new HashMap<>()).get("serverGroup"));
  }

  public void verifyTrafficRemoval(
      String serverGroupName,
      Moniker serverGroupMoniker,
      String account,
      Location location,
      String cloudProvider,
      String operationDescriptor) {
    Optional<Map> cluster =
        oortHelper.getCluster(
            serverGroupMoniker.getApp(), account, serverGroupMoniker.getCluster(), cloudProvider);

    if (!cluster.isPresent()) {
      throw new TrafficGuardException(
          format(
              "Could not find cluster '%s' in %s/%s",
              serverGroupMoniker.getCluster(), account, location.getValue()));
    }

    List<TargetServerGroup> targetServerGroups =
        ((List<Map<String, Object>>) cluster.get().get("serverGroups"))
            .stream()
                .map(TargetServerGroup::new)
                .filter(tsg -> location.equals(tsg.getLocation(location.getType())))
                .collect(Collectors.toList());

    TargetServerGroup serverGroupGoingAway =
        targetServerGroups.stream()
            .filter(sg -> serverGroupName.equals(sg.getName()))
            .findFirst()
            .orElseThrow(
                () -> {
                  String message =
                      format(
                          "Could not find server group '%s' in %s/%s, found [%s]",
                          serverGroupName,
                          account,
                          location,
                          String.join(
                              ", ",
                              targetServerGroups.stream()
                                  .map(TargetServerGroup::getName)
                                  .collect(Collectors.toSet())));
                  log.error("{}\nContext: {}", message, generateContext(targetServerGroups));
                  return new TrafficGuardException(message);
                });

    verifyTrafficRemoval(serverGroupGoingAway, targetServerGroups, account, operationDescriptor);
  }

  public void verifyTrafficRemoval(
      TargetServerGroup serverGroupGoingAway,
      Collection<TargetServerGroup> currentServerGroups,
      String account,
      String operationDescriptor) {
    verifyTrafficRemoval(
        Collections.singletonList(serverGroupGoingAway),
        currentServerGroups,
        account,
        operationDescriptor);
  }

  // if you disable serverGroup, are there other enabled server groups in the same cluster and
  // location?
  // TODO rz - Expose traffic guards endpoint in clouddriver
  public void verifyTrafficRemoval(
      Collection<TargetServerGroup> serverGroupsGoingAway,
      Collection<TargetServerGroup> currentServerGroups,
      String account,
      String operationDescriptor) {
    if (serverGroupsGoingAway == null || serverGroupsGoingAway.isEmpty()) {
      return;
    }

    Preconditions.checkArg(!currentServerGroups.isEmpty(), "currentServerGroups must not be empty");

    // make sure all server groups are in the same location
    TargetServerGroup someServerGroup = serverGroupsGoingAway.stream().findAny().get();
    Location location = someServerGroup.getLocation();
    Preconditions.checkArg(
        Stream.concat(serverGroupsGoingAway.stream(), currentServerGroups.stream())
            .allMatch(sg -> location.equals(sg.getLocation())),
        "server groups must all be in the same location but some not in " + location);

    // make sure all server groups are in the same cluster
    String cluster = someServerGroup.getMoniker().getCluster();
    Preconditions.checkArg(
        Stream.concat(serverGroupsGoingAway.stream(), currentServerGroups.stream())
            .allMatch(sg -> cluster.equals(sg.getMoniker().getCluster())),
        "server groups must all be in the same cluster but some not in " + cluster);

    if (!hasDisableLock(someServerGroup.getMoniker(), account, location)) {
      log.debug(
          "No traffic guard configured for '{}' in {}/{}", cluster, account, location.getValue());
      return;
    }

    // let the work begin

    Map<String, Integer> capacityByServerGroupName =
        currentServerGroups.stream()
            .collect(Collectors.toMap(TargetServerGroup::getName, this::getServerGroupCapacity));

    Set<String> namesOfServerGroupsGoingAway =
        serverGroupsGoingAway.stream().map(TargetServerGroup::getName).collect(Collectors.toSet());

    int currentCapacity = capacityByServerGroupName.values().stream().reduce(0, Integer::sum);

    if (currentCapacity == 0) {
      log.debug(
          "Bypassing traffic guard check for '{}' in {}/{} with no instances Up. Context: {}",
          cluster,
          account,
          location.getValue(),
          generateContext(currentServerGroups));
      return;
    }

    int capacityGoingAway =
        capacityByServerGroupName.entrySet().stream()
            .filter(entry -> namesOfServerGroupsGoingAway.contains(entry.getKey()))
            .map(Map.Entry::getValue)
            .reduce(0, Integer::sum);

    int futureCapacity = currentCapacity - capacityGoingAway;
    double futureCapacityRatio = ((double) futureCapacity) / currentCapacity;
    double minCapacityRatio = getMinCapacityRatio();
    if (futureCapacityRatio <= minCapacityRatio) {
      String message =
          generateUserFacingMessage(
              cluster,
              account,
              location,
              operationDescriptor,
              namesOfServerGroupsGoingAway,
              futureCapacity,
              currentCapacity,
              futureCapacityRatio,
              minCapacityRatio);
      log.debug("{}\nContext: {}", message, generateContext(currentServerGroups));

      registry
          .counter(
              savesId.withTags(
                  "application", someServerGroup.getMoniker().getApp(), "account", account))
          .increment();

      throw new TrafficGuardException(message);
    }
  }

  private String generateUserFacingMessage(
      String cluster,
      String account,
      Location location,
      String operationDescriptor,
      Set<String> namesOfServerGroupsGoingAway,
      int futureCapacity,
      int currentCapacity,
      double futureCapacityRatio,
      double minCapacityRatio) {
    String message =
        format(
            "This cluster ('%s' in %s/%s) has traffic guards enabled. %s [%s] would leave the cluster ",
            cluster,
            account,
            location.getValue(),
            operationDescriptor,
            String.join(",", namesOfServerGroupsGoingAway));

    if (futureCapacity == 0) {
      return message + "with no instances up.";
    }

    String withInstances =
        (futureCapacity == 1)
            ? "with 1 instance up "
            : format("with %d instances up ", futureCapacity);
    return message
        + withInstances
        + format(
            "(%.1f%% of %d instances currently up). The configured minimum is %.1f%%.",
            futureCapacityRatio * 100, currentCapacity, minCapacityRatio * 100);
  }

  private double getMinCapacityRatio() {
    double defaultMinCapacityRatio = 0d;
    try {
      Double minCapacityRatio =
          dynamicConfigService.getConfig(Double.class, MIN_CAPACITY_RATIO, defaultMinCapacityRatio);
      if (minCapacityRatio == null || minCapacityRatio < 0 || 0.5 <= minCapacityRatio) {
        log.error(
            "Expecting a double value in range [0, 0.5) for {} but got {}",
            MIN_CAPACITY_RATIO,
            minCapacityRatio);
        return 0;
      }
      return minCapacityRatio;
    } catch (NumberFormatException e) {
      log.error("Expecting a double value in range [0, 0.5) for {}", MIN_CAPACITY_RATIO, e);
      return defaultMinCapacityRatio;
    }
  }

  private List<Map> generateContext(Collection<TargetServerGroup> targetServerGroups) {
    return targetServerGroups.stream()
        .map(
            tsg ->
                ImmutableMap.builder()
                    .put("name", tsg.getName())
                    .put("disabled", tsg.isDisabled())
                    .put("instances", tsg.getInstances())
                    .build())
        .collect(Collectors.toList());
  }

  private int getServerGroupCapacity(TargetServerGroup serverGroup) {
    return (int)
        serverGroup.getInstances().stream()
            .filter(instance -> "Up".equals(instance.get("healthState")))
            .count();
  }

  public boolean hasDisableLock(Moniker clusterMoniker, String account, Location location) {
    if (front50Service == null) {
      log.warn(
          "Front50 has not been configured, no way to check disable lock. Fix this by setting front50.enabled: true");
      return false;
    }
    Application application;
    try {
      application = front50Service.get(clusterMoniker.getApp());
    } catch (RetrofitError e) {
      // ignore an unknown (404) or unauthorized (403) application
      if (e.getResponse() != null
          && Arrays.asList(404, 403).contains(e.getResponse().getStatus())) {
        application = null;
      } else {
        throw e;
      }
    }
    if (application == null || !application.details().containsKey("trafficGuards")) {
      return false;
    }
    List<Map<String, Object>> trafficGuards =
        (List<Map<String, Object>>) application.details().get("trafficGuards");
    List<ClusterMatchRule> rules =
        trafficGuards.stream()
            .filter(guard -> (boolean) guard.getOrDefault("enabled", true))
            .map(
                guard ->
                    new ClusterMatchRule(
                        (String) guard.get("account"),
                        (String) guard.get("location"),
                        (String) guard.get("stack"),
                        (String) guard.get("detail"),
                        1))
            .collect(Collectors.toList());
    return ClusterMatcher.getMatchingRule(account, location.getValue(), clusterMoniker, rules)
        != null;
  }
}
