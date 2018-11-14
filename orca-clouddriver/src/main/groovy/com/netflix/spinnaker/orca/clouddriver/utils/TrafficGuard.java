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

import com.google.common.collect.ImmutableMap;
import com.netflix.spectator.api.Id;
import com.netflix.spectator.api.Registry;
import com.netflix.spinnaker.kork.dynamicconfig.DynamicConfigService;
import com.netflix.spinnaker.moniker.Moniker;
import com.netflix.spinnaker.orca.clouddriver.pipeline.servergroup.support.Location;
import com.netflix.spinnaker.orca.clouddriver.pipeline.servergroup.support.TargetServerGroup;
import com.netflix.spinnaker.orca.front50.Front50Service;
import com.netflix.spinnaker.orca.front50.model.Application;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import retrofit.RetrofitError;

import java.util.*;
import java.util.stream.Collectors;

import static java.lang.String.format;

@Component
public class TrafficGuard {
  private final static String MIN_CAPACITY_RATIO = "trafficGuards.minCapacityRatio";
  private final Logger log = LoggerFactory.getLogger(getClass());

  private final OortHelper oortHelper;
  private final Front50Service front50Service;
  private final Registry registry;
  private final DynamicConfigService dynamicConfigService;

  private final Id savesId;

  @Autowired
  public TrafficGuard(OortHelper oortHelper,
                      Optional<Front50Service> front50Service,
                      Registry registry,
                      DynamicConfigService dynamicConfigService) {
    this.oortHelper = oortHelper;
    this.front50Service = front50Service.orElse(null);
    this.registry = registry;
    this.dynamicConfigService = dynamicConfigService;
    this.savesId = registry.createId("trafficGuard.saves");
  }

  public void verifyInstanceTermination(String serverGroupNameFromStage,
                                        Moniker serverGroupMonikerFromStage,
                                        List<String> instanceIds,
                                        String account,
                                        Location location,
                                        String cloudProvider,
                                        String operationDescriptor) {
    // TODO rz - Expose a single `instance server groups` endpoint in clouddriver; returns a map<instanceid, servergroup>.
    Map<String, List<String>> instancesPerServerGroup = new HashMap<>();
    for (String instanceId : instanceIds) {
      String serverGroupName = serverGroupNameFromStage;
      if (serverGroupName == null) {
        Optional<String> resolvedServerGroupName = resolveServerGroupNameForInstance(instanceId, account, location.getValue(), cloudProvider);
        serverGroupName = resolvedServerGroupName.orElse(null);
      }

      if (serverGroupName != null) {
        instancesPerServerGroup.computeIfAbsent(serverGroupName, serverGroup -> new ArrayList<>()).add(instanceId);
      }
    }

    instancesPerServerGroup.entrySet().forEach(entry -> {
      String serverGroupName = entry.getKey();
      Moniker moniker = serverGroupMonikerFromStage;
      if (moniker.getApp() == null) {
        // handle scenarios where the stage moniker is invalid (ie. stage had no server group details provided)
        moniker = MonikerHelper.friggaToMoniker(serverGroupName);
      }

      // TODO rz - `hasDisableLock` should only be called once for each `app` value, not every instance.
      if (hasDisableLock(moniker, account, location)) {
        // TODO rz - Remove: No longer needed since all data is retrieved in above clouddriver call @L64
        Optional<TargetServerGroup> targetServerGroup = oortHelper.getTargetServerGroup(account, serverGroupName, location.getValue(), cloudProvider);

        targetServerGroup.ifPresent(serverGroup -> {
          Optional<Map> thisInstance = serverGroup.getInstances().stream().filter(i -> "Up".equals(i.get("healthState"))).findFirst();
          if (thisInstance.isPresent() && "Up".equals(thisInstance.get().get("healthState"))) {
            long otherActiveInstances = serverGroup.getInstances().stream().filter(i -> "Up".equals(i.get("healthState")) && !entry.getValue().contains(i.get("name"))).count();
            if (otherActiveInstances == 0) {
              verifyOtherServerGroupsAreTakingTraffic(serverGroupName, serverGroup.getMoniker(), location, account, cloudProvider, operationDescriptor);
            }
          }
        });
      }
    });
  }

  private Optional<String> resolveServerGroupNameForInstance(String instanceId, String account, String region, String cloudProvider) {
    List<Map> searchResults = (List<Map>) oortHelper.getSearchResults(instanceId, "instances", cloudProvider).get(0).getOrDefault("results", new ArrayList<>());
    Optional<Map> instance = searchResults.stream().filter(r -> account.equals(r.get("account")) && region.equals(r.get("region"))).findFirst();
    // instance not found, assume it's already terminated, what could go wrong
    return Optional.ofNullable((String) instance.orElse(new HashMap<>()).get("serverGroup"));
  }

  public void verifyTrafficRemoval(String serverGroupName, Moniker serverGroupMoniker, String account, Location location, String cloudProvider, String operationDescriptor) {
    if (!hasDisableLock(serverGroupMoniker, account, location)) {
      return;
    }

    verifyOtherServerGroupsAreTakingTraffic(serverGroupName, serverGroupMoniker, location, account, cloudProvider, operationDescriptor);
  }

  private void verifyOtherServerGroupsAreTakingTraffic(String serverGroupName, Moniker serverGroupMoniker,
                                                       Location location, String account, String cloudProvider,
                                                       String operationDescriptor) {
    // TODO rz - Expose traffic guards endpoint in clouddriver
    Optional<Map> cluster = oortHelper.getCluster(serverGroupMoniker.getApp(), account,
      serverGroupMoniker.getCluster(), cloudProvider);
    if (!cluster.isPresent()) {
      throw new TrafficGuardException(format("Could not find cluster '%s' in %s/%s with traffic guard configured.",
        serverGroupMoniker.getCluster(), account, location.getValue()));
    }

    List<TargetServerGroup> targetServerGroups = ((List<Map<String, Object>>) cluster.get().get("serverGroups"))
      .stream()
      .map(TargetServerGroup::new)
      .filter(tsg -> location.equals(tsg.getLocation()))
      .collect(Collectors.toList());

    Map<String, Integer> capacityByServerGroupName = targetServerGroups
      .stream()
      .collect(Collectors.toMap(TargetServerGroup::getName, this::getServerGroupCapacity));

    int currentCapacity = capacityByServerGroupName.values().stream().reduce(0, Integer::sum);
    int serverGroupCapacity = capacityByServerGroupName.get(serverGroupName);
    int futureCapacity = currentCapacity - serverGroupCapacity;

    if (currentCapacity == 0) {
      log.debug("Bypassing traffic guard check for '{}' in {}/{} with no instances Up. Context: {}",
        serverGroupMoniker.getCluster(), account, location.getValue(), generateContext(targetServerGroups));
      return;
    }

    double futureCapacityRatio = ((double) futureCapacity) / currentCapacity;
    if (futureCapacityRatio <= getMinCapacityRatio()) {
      String message = format("This cluster ('%s' in %s/%s) has traffic guards enabled. %s %s would leave the cluster ",
        serverGroupMoniker.getCluster(), account, location.getValue(), operationDescriptor, serverGroupName);

      message += (futureCapacity == 0)
        ? "with no instances taking traffic."
        : format("with %d instances up (%.2f%% of %d instances currently up)",
            futureCapacity, futureCapacityRatio * 100, currentCapacity);

      log.debug("{} Context: {}", message, generateContext(targetServerGroups));

      registry.counter(savesId.withTags(
        "application", serverGroupMoniker.getApp(),
        "account", account
      )).increment();

      throw new TrafficGuardException(message);
    }
  }

  private double getMinCapacityRatio() {
    Double minCapacityRatio = dynamicConfigService.getConfig(Double.class, MIN_CAPACITY_RATIO, 0d);
    if (minCapacityRatio == null || minCapacityRatio < 0 || 0.5 <= minCapacityRatio) {
      log.error("Expecting a double value in range [0, 0.5) for {} but got {}", MIN_CAPACITY_RATIO, minCapacityRatio);
      return 0;
    }

    return minCapacityRatio;
  }

  private List<Map> generateContext(List<TargetServerGroup> targetServerGroups) {
    return targetServerGroups.stream()
      .map(tsg -> ImmutableMap.builder()
        .put("name", tsg.getName())
        .put("disabled", tsg.isDisabled())
        .put("instances", tsg.getInstances())
        .build())
      .collect(Collectors.toList());
  }

  private int getServerGroupCapacity(TargetServerGroup serverGroup) {
    return (int) serverGroup.getInstances().stream()
      .filter(instance -> "Up".equals(instance.get("healthState")))
      .count();
  }

  public boolean hasDisableLock(Moniker clusterMoniker, String account, Location location) {
    if (front50Service == null) {
      log.warn("Front50 has not been configured, no way to check disable lock. Fix this by setting front50.enabled: true");
      return false;
    }
    Application application;
    try {
      application = front50Service.get(clusterMoniker.getApp());
    } catch (RetrofitError e) {
      //ignore an unknown (404) or unauthorized (403) application
      if (e.getResponse() != null && Arrays.asList(404, 403).contains(e.getResponse().getStatus())) {
        application = null;
      } else {
        throw e;
      }
    }
    if (application == null || !application.details().containsKey("trafficGuards")) {
      return false;
    }
    List<Map<String, String>> trafficGuards = (List<Map<String, String>>) application.details().get("trafficGuards");
    List<ClusterMatchRule> rules = trafficGuards.stream().map(guard ->
      new ClusterMatchRule(guard.get("account"), guard.get("location"), guard.get("stack"), guard.get("detail"), 1)
    ).collect(Collectors.toList());
    return ClusterMatcher.getMatchingRule(account, location.getValue(), clusterMoniker, rules) != null;
  }
}
