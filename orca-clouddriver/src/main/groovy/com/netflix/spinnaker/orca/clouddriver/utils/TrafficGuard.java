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

import java.util.*;
import java.util.stream.Collectors;
import com.netflix.frigga.Names;
import com.netflix.spinnaker.orca.clouddriver.pipeline.servergroup.support.Location;
import com.netflix.spinnaker.orca.clouddriver.pipeline.servergroup.support.TargetServerGroup;
import com.netflix.spinnaker.orca.front50.Front50Service;
import com.netflix.spinnaker.orca.front50.model.Application;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import retrofit.RetrofitError;
import static java.lang.String.format;

@Component
public class TrafficGuard {

  private final Logger log = LoggerFactory.getLogger(getClass());

  private final OortHelper oortHelper;

  private final Front50Service front50Service;

  @Autowired
  public TrafficGuard(OortHelper oortHelper, Optional<Front50Service> front50Service) {
    this.oortHelper = oortHelper;
    this.front50Service = front50Service.orElse(null);
  }

  public void verifyInstanceTermination(String serverGroupNameFromStage,
                                        List<String> instanceIds,
                                        String account,
                                        Location location,
                                        String cloudProvider,
                                        String operationDescriptor) {
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
      Names names = Names.parseName(serverGroupName);
      if (hasDisableLock(names.getCluster(), account, location)) {
        Optional<TargetServerGroup> targetServerGroup = oortHelper.getTargetServerGroup(account, serverGroupName, location.getValue(), cloudProvider);

        targetServerGroup.ifPresent(serverGroup -> {
          Optional<Map> thisInstance = serverGroup.getInstances().stream().filter(i -> "Up".equals(i.get("healthState"))).findFirst();
          if (thisInstance.isPresent() && "Up".equals(thisInstance.get().get("healthState"))) {
            long otherActiveInstances = serverGroup.getInstances().stream().filter(i -> "Up".equals(i.get("healthState")) && !entry.getValue().contains(i.get("name"))).count();
            if (otherActiveInstances == 0) {
              verifyOtherServerGroupsAreTakingTraffic(serverGroupName, location, account, cloudProvider, operationDescriptor);
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

  public void verifyTrafficRemoval(String serverGroupName, String account, Location location, String cloudProvider, String operationDescriptor) {
    Names names = Names.parseName(serverGroupName);

    if (!hasDisableLock(names.getCluster(), account, location)) {
      return;
    }

    verifyOtherServerGroupsAreTakingTraffic(serverGroupName, location, account, cloudProvider, operationDescriptor);
  }

  private void verifyOtherServerGroupsAreTakingTraffic(String serverGroupName, Location location, String account, String cloudProvider, String operationDescriptor) {
    Names names = Names.parseName(serverGroupName);
    Optional<Map> cluster = oortHelper.getCluster(names.getApp(), account, names.getCluster(), cloudProvider);

    if (!cluster.isPresent()) {
      throw new IllegalStateException(format("Could not find cluster '%s' in %s/%s with traffic guard configured.", names.getCluster(), account, location.getValue()));
    }
    List<TargetServerGroup> targetServerGroups = ((List<Map<String, Object>>) cluster.get().get("serverGroups"))
      .stream()
      .map(TargetServerGroup::new)
      .filter(tsg -> location.equals(tsg.getLocation()))
      .collect(Collectors.toList());

    boolean otherEnabledServerGroupFound = targetServerGroups.stream().anyMatch(tsg ->
      !serverGroupName.equals(tsg.getName()) &&
        (tsg.getInstances().stream().filter(i -> "Up".equals(i.get("healthState"))).count()) > 0
    );
    if (!otherEnabledServerGroupFound) {
      throw new IllegalStateException(format("This cluster ('%s' in %s/%s) has traffic guards enabled. " +
        "%s %s would leave the cluster with no instances taking traffic.", names.getCluster(), account, location.getValue(), operationDescriptor, serverGroupName));
    }
  }

  public boolean hasDisableLock(String cluster, String account, Location location) {
    if (front50Service == null) {
      log.warn("Front50 has not been configured, no way to check disable lock. Fix this by setting front50.enabled: true");
      return false;
    }
    Names names = Names.parseName(cluster);
    Application application;
    try {
      application = front50Service.get(names.getApp());
    } catch (RetrofitError e) {
      if (e.getResponse() != null && e.getResponse().getStatus() == 404) {
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
    return ClusterMatcher.getMatchingRule(account, location.getValue(), cluster, rules) != null;
  }
}
