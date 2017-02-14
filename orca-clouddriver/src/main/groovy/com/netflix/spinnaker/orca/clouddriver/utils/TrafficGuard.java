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

import com.netflix.frigga.Names;
import com.netflix.spinnaker.orca.clouddriver.FeaturesService;
import com.netflix.spinnaker.orca.clouddriver.pipeline.servergroup.support.Location;
import com.netflix.spinnaker.orca.clouddriver.pipeline.servergroup.support.TargetServerGroup;
import com.netflix.spinnaker.orca.front50.Front50Service;
import com.netflix.spinnaker.orca.front50.model.Application;
import org.apache.commons.lang.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

@Component
public class TrafficGuard {

  private final OortHelper oortHelper;

  private final Front50Service front50Service;

  @Autowired
  public TrafficGuard(OortHelper oortHelper, Front50Service front50Service) {
    this.oortHelper = oortHelper;
    this.front50Service = front50Service;
  }

  public void verifyTrafficRemoval(String serverGroupName, String account, Location location, String cloudProvider, String operationDescriptor) {
    Names names = Names.parseName(serverGroupName);

    if (!hasDisableLock(names.getCluster(), account, location)) {
      return;
    }

    Optional<Map> cluster = oortHelper.getCluster(names.getApp(), account, names.getCluster(), cloudProvider);

    if (!cluster.isPresent()) {
      throw new IllegalStateException("Could not find traffic-protected cluster.");
    }

    List<TargetServerGroup> targetServerGroups = ((List<Map<String, Object>>) cluster.get().get("serverGroups"))
      .stream()
      .map(TargetServerGroup::new)
      .filter(tsg -> location.equals(tsg.getLocation()))
      .collect(Collectors.toList());

    boolean otherEnabledServerGroupFound = targetServerGroups.stream().anyMatch(tsg ->
      !serverGroupName.equals(tsg.getName()) &&
        !Boolean.TRUE.equals(tsg.isDisabled()) &&
        (tsg.getInstances()).size() > 0
    );
    if (!otherEnabledServerGroupFound) {
      throw new IllegalStateException(String.format("This cluster has traffic protection enabled. " +
        "%s %s would leave the cluster with no instances taking traffic.", operationDescriptor, serverGroupName));
    }
  }

  public boolean hasDisableLock(String cluster, String account, Location location) {
    Names names = Names.parseName(cluster);
    Application application = front50Service.get(names.getApp());
    if (application == null || !application.details().containsKey("trafficGuards")) {
      return false;
    }
    List<Map<String, String>> trafficGuards = (List<Map<String, String>>) application.details().get("trafficGuards");
    return trafficGuards.stream().anyMatch(guard ->
      ("*".equals(guard.get("account")) || account.equals(guard.get("account"))) &&
        ("*".equals(guard.get("location")) || location.getValue().equals(guard.get("location"))) &&
          ("*".equals(guard.get("stack")) || StringUtils.equals(names.getStack(), guard.get("stack"))) &&
            ("*".equals(guard.get("detail")) || StringUtils.equals(names.getDetail(), guard.get("detail")))
    );
  }
}
