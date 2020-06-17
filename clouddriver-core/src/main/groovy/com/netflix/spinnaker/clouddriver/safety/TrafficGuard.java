/*
 * Copyright 2020 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
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
 *
 */
package com.netflix.spinnaker.clouddriver.safety;

import static java.lang.String.format;

import com.google.common.collect.ImmutableMap;
import com.netflix.spectator.api.Id;
import com.netflix.spectator.api.Registry;
import com.netflix.spectator.impl.Preconditions;
import com.netflix.spinnaker.clouddriver.core.services.Front50Service;
import com.netflix.spinnaker.clouddriver.exceptions.TrafficGuardException;
import com.netflix.spinnaker.clouddriver.model.Cluster;
import com.netflix.spinnaker.clouddriver.model.ClusterProvider;
import com.netflix.spinnaker.clouddriver.model.HealthState;
import com.netflix.spinnaker.clouddriver.model.ServerGroup;
import com.netflix.spinnaker.clouddriver.names.NamerRegistry;
import com.netflix.spinnaker.kork.dynamicconfig.DynamicConfigService;
import com.netflix.spinnaker.moniker.Moniker;
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

  private final List<ClusterProvider<?>> clusterProviders;
  private final Front50Service front50Service;
  private final Registry registry;
  private final DynamicConfigService dynamicConfigService;

  private final Id savesId;

  @Autowired
  public TrafficGuard(
      List<ClusterProvider<?>> clusterProviders,
      Optional<Front50Service> front50Service,
      Registry registry,
      DynamicConfigService dynamicConfigService) {
    this.clusterProviders = clusterProviders;
    this.front50Service = front50Service.orElse(null);
    this.registry = registry;
    this.dynamicConfigService = dynamicConfigService;
    this.savesId = registry.createId("trafficGuard.saves");
  }

  public void verifyInstanceTermination(
      String serverGroupName,
      List<String> instanceIds,
      String account,
      String location,
      String cloudProvider,
      String operationDescriptor) {
    // TODO(rz): I opted out of migrating this method because it isn't used in
    //  my current refactors. This method uses clouddriver search endpoint,
    //  which would be a much larger refactor to bring over in this commit. I
    //  would like to postpone such a refactor until it's actually needed.
    throw new UnsupportedOperationException(
        "verifyInstanceTermination method has not been migrated from Orca yet");
  }

  public void verifyTrafficRemoval(
      String serverGroupName,
      String account,
      String location,
      String cloudProvider,
      String operationDescriptor) {

    Moniker serverGroupMoniker = NamerRegistry.getDefaultNamer().deriveMoniker(serverGroupName);

    ClusterProvider<?> clusterProvider =
        getClusterProvider(cloudProvider)
            .orElseThrow(
                () ->
                    new TrafficGuardException(
                        format(
                            "Could not find ClusterProvider for cloud provider '%s'",
                            cloudProvider)));

    Cluster cluster =
        clusterProvider.getCluster(
            serverGroupMoniker.getApp(), account, serverGroupMoniker.getCluster(), false);

    if (cluster == null) {
      throw new TrafficGuardException(
          format(
              "Could not find cluster '%s' in '%s/%s'",
              serverGroupMoniker.getCluster(), account, location));
    }

    List<ServerGroup> targetServerGroups =
        cluster.getServerGroups().stream()
            .filter(it -> it.getRegion().equals(location))
            .collect(Collectors.toList());

    ServerGroup serverGroupGoingAway =
        targetServerGroups.stream()
            .filter(it -> serverGroupMoniker.equals(it.getMoniker()))
            .findFirst()
            .orElseThrow(
                () -> {
                  String message =
                      format(
                          "Could not find server group '%s' in '%s/%s', found [%s]",
                          serverGroupName,
                          account,
                          location,
                          targetServerGroups.stream()
                              .map(it -> it.getMoniker().toString())
                              .collect(Collectors.joining(", ")));
                  log.error("{}\nContext: {}", message, generateContext(targetServerGroups));
                  return new TrafficGuardException(message);
                });

    verifyTrafficRemoval(serverGroupGoingAway, targetServerGroups, account, operationDescriptor);
  }

  public void verifyTrafficRemoval(
      ServerGroup serverGroupGoingAway,
      Collection<ServerGroup> currentServerGroups,
      String account,
      String operationDescriptor) {
    verifyTrafficRemoval(
        Collections.singletonList(serverGroupGoingAway),
        currentServerGroups,
        account,
        operationDescriptor);
  }

  /**
   * If you disable serverGroup, are there other enabled server groups in the same cluster and
   * location?
   *
   * @param serverGroupsGoingAway
   * @param currentServerGroups
   * @param account
   * @param operationDescriptor
   */
  public void verifyTrafficRemoval(
      Collection<ServerGroup> serverGroupsGoingAway,
      Collection<ServerGroup> currentServerGroups,
      String account,
      String operationDescriptor) {
    if (serverGroupsGoingAway == null || serverGroupsGoingAway.isEmpty()) {
      return;
    }

    Preconditions.checkArg(!currentServerGroups.isEmpty(), "currentServerGroups must not be empty");

    // make sure all server groups are in the same location
    ServerGroup someServerGroup = serverGroupsGoingAway.stream().findAny().get();
    String location = someServerGroup.getRegion();
    Preconditions.checkArg(
        Stream.concat(serverGroupsGoingAway.stream(), currentServerGroups.stream())
            .allMatch(sg -> location.equals(sg.getRegion())),
        "server groups must all be in the same location but some not in " + location);

    // make sure all server groups are in the same cluster
    String cluster = someServerGroup.getMoniker().getCluster();
    Preconditions.checkArg(
        Stream.concat(serverGroupsGoingAway.stream(), currentServerGroups.stream())
            .allMatch(sg -> cluster.equals(sg.getMoniker().getCluster())),
        "server groups must all be in the same cluster but some not in " + cluster);

    if (!hasDisableLock(someServerGroup.getMoniker(), account, location)) {
      log.debug("No traffic guard configured for '{}' in {}/{}", cluster, account, location);
      return;
    }

    // let the work begin
    Map<String, Integer> capacityByServerGroupName =
        currentServerGroups.stream()
            .collect(Collectors.toMap(ServerGroup::getName, this::getServerGroupCapacity));

    Set<String> namesOfServerGroupsGoingAway =
        serverGroupsGoingAway.stream().map(ServerGroup::getName).collect(Collectors.toSet());

    int currentCapacity = capacityByServerGroupName.values().stream().reduce(0, Integer::sum);

    if (currentCapacity == 0) {
      log.debug(
          "Bypassing traffic guard check for '{}' in {}/{} with no instances Up. Context: {}",
          cluster,
          account,
          location,
          generateContext(currentServerGroups));
      return;
    }

    int capacityGoingAway =
        capacityByServerGroupName.entrySet().stream()
            .filter(entry -> namesOfServerGroupsGoingAway.contains(entry.getKey()))
            .map(Map.Entry::getValue)
            .reduce(0, Integer::sum);

    int futureCapacity = currentCapacity - capacityGoingAway;

    int someDesiredSize = someServerGroup.getCapacity().getDesired();
    if (futureCapacity > 0
        && serverGroupsGoingAway.size() > 1
        && serverGroupsGoingAway.stream().allMatch(sg -> sg.getCapacity().isPinned())
        && serverGroupsGoingAway.stream()
            .allMatch(sg -> sg.getCapacity().getDesired() == someDesiredSize)) {
      log.debug(
          "Bypassing traffic guard check for '{}' in {}/{} with pinned server groups of size {}. Context: {}",
          cluster,
          account,
          location,
          someDesiredSize,
          generateContext(currentServerGroups));
      return;
    }

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
      String location,
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
            location,
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
            "Expecting a double value in range [0, 0.5] for {} but got {}",
            MIN_CAPACITY_RATIO,
            minCapacityRatio);
        return 0;
      }
      return minCapacityRatio;
    } catch (NumberFormatException e) {
      log.error("Expecting a double value in range [0, 0.5] for {}", MIN_CAPACITY_RATIO, e);
      return defaultMinCapacityRatio;
    }
  }

  private List<Map> generateContext(Collection<ServerGroup> targetServerGroups) {
    return targetServerGroups.stream()
        .map(
            tsg ->
                ImmutableMap.builder()
                    .put("name", tsg.getName())
                    .put("disabled", tsg.isDisabled())
                    .put("instances", tsg.getInstances())
                    .put("capacity", tsg.getCapacity())
                    .build())
        .collect(Collectors.toList());
  }

  private int getServerGroupCapacity(ServerGroup serverGroup) {
    return (int)
        serverGroup.getInstances().stream()
            .filter(it -> HealthState.Up.equals(it.getHealthState()))
            .count();
  }

  public boolean hasDisableLock(Moniker clusterMoniker, String account, String location) {
    if (front50Service == null) {
      log.warn(
          "Front50 has not been configured, no way to check disable lock. Fix this by setting front50.enabled: true");
      return false;
    }
    Map application;
    try {
      application = front50Service.getApplication(clusterMoniker.getApp());
    } catch (RetrofitError e) {
      // ignore an unknown (404) or unauthorized (403) application
      if (e.getResponse() != null
          && Arrays.asList(404, 403).contains(e.getResponse().getStatus())) {
        application = null;
      } else {
        throw e;
      }
    }
    if (application == null || !application.containsKey("trafficGuards")) {
      return false;
    }
    List<Map<String, Object>> trafficGuards =
        (List<Map<String, Object>>) application.get("trafficGuards");
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
    return ClusterMatcher.getMatchingRule(account, location, clusterMoniker, rules) != null;
  }

  private Optional<ClusterProvider<?>> getClusterProvider(String cloudProvider) {
    return clusterProviders.stream()
        .filter(it -> it.getCloudProviderId().equals(cloudProvider))
        .findFirst();
  }
}
