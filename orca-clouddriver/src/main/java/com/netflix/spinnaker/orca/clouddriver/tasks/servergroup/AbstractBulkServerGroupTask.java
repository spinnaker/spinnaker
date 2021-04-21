/*
 * Copyright 2017 Netflix, Inc.
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

package com.netflix.spinnaker.orca.clouddriver.tasks.servergroup;

import com.netflix.spinnaker.moniker.Moniker;
import com.netflix.spinnaker.orca.api.operations.OperationsContext;
import com.netflix.spinnaker.orca.api.operations.OperationsInput;
import com.netflix.spinnaker.orca.api.operations.OperationsRunner;
import com.netflix.spinnaker.orca.api.pipeline.RetryableTask;
import com.netflix.spinnaker.orca.api.pipeline.TaskResult;
import com.netflix.spinnaker.orca.api.pipeline.models.ExecutionStatus;
import com.netflix.spinnaker.orca.api.pipeline.models.StageExecution;
import com.netflix.spinnaker.orca.clouddriver.CloudDriverService;
import com.netflix.spinnaker.orca.clouddriver.model.Cluster;
import com.netflix.spinnaker.orca.clouddriver.model.ServerGroup;
import com.netflix.spinnaker.orca.clouddriver.pipeline.servergroup.support.Location;
import com.netflix.spinnaker.orca.clouddriver.pipeline.servergroup.support.TargetServerGroup;
import com.netflix.spinnaker.orca.clouddriver.utils.CloudProviderAware;
import com.netflix.spinnaker.orca.clouddriver.utils.MonikerHelper;
import java.util.*;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

public abstract class AbstractBulkServerGroupTask implements CloudProviderAware, RetryableTask {
  private static final Logger LOGGER = LoggerFactory.getLogger(AbstractBulkServerGroupTask.class);

  @Autowired protected CloudDriverService cloudDriverService;

  @Autowired protected MonikerHelper monikerHelper;

  @Autowired protected OperationsRunner operationsRunner;

  abstract void validateClusterStatus(Map<String, Object> operation, Moniker moniker);

  abstract String getClouddriverOperation();

  @Override
  public long getBackoffPeriod() {
    return 10000;
  }

  @Override
  public long getTimeout() {
    return 10000;
  }

  @Override
  public TaskResult execute(StageExecution stage) {
    ServerGroupRequest request = stage.mapTo(ServerGroupRequest.class);
    if (request.getServerGroupNames() == null || request.getServerGroupNames().isEmpty()) {
      throw new IllegalArgumentException("Server group names must be provided");
    }
    if (request.getRegion() == null) {
      throw new IllegalArgumentException("A region is required for this operation");
    }
    String fallbackFriggaName = request.getServerGroupNames().get(0);

    String clusterName = monikerHelper.getClusterNameFromStage(stage, fallbackFriggaName);
    String appName = monikerHelper.getAppNameFromStage(stage, fallbackFriggaName);
    Cluster cluster =
        cloudDriverService
            .maybeCluster(
                appName, request.getCredentials(), clusterName, request.getCloudProvider())
            .orElseThrow(
                () ->
                    new IllegalArgumentException(
                        String.format("No Cluster details found for %s", clusterName)));

    List<ServerGroup> serverGroups = cluster.getServerGroups();
    if (serverGroups == null) {
      throw new IllegalArgumentException(
          String.format("No server groups found for cluster %s", clusterName));
    }

    Location location = Location.region(request.getRegion());

    List<TargetServerGroup> targetServerGroups = new ArrayList<>();
    serverGroups.forEach(
        sg -> {
          TargetServerGroup tg = new TargetServerGroup(sg);
          if (tg.getLocation(location.getType()).equals(location)
              && request.getServerGroupNames().contains(tg.getName())) {
            targetServerGroups.add(tg);
          }
        });

    LOGGER.info("Found target server groups {}", targetServerGroups);
    if (targetServerGroups.isEmpty()) {
      throw new TargetServerGroup.NotFoundException();
    }

    List<Map<String, Map>> operations = new ArrayList<>();
    targetServerGroups.forEach(
        targetServerGroup -> {
          Map<String, Map> tmp = new HashMap<>();
          Map<String, Object> operation =
              targetServerGroup.toClouddriverOperationPayload(request.getCredentials());
          Moniker moniker = targetServerGroup.getMoniker();
          if (moniker == null || moniker.getCluster() == null) {
            moniker = MonikerHelper.friggaToMoniker(targetServerGroup.getName());
          }
          validateClusterStatus(operation, moniker);
          tmp.put(getClouddriverOperation(), operation);
          operations.add(tmp);
        });

    OperationsInput operationsInput = OperationsInput.of(request.cloudProvider, operations, stage);
    OperationsContext operationsContext = operationsRunner.run(operationsInput);

    Map<String, Object> result = new HashMap<>();
    result.put("deploy.account.name", request.getCredentials());
    result.put(operationsContext.contextKey(), operationsContext.contextValue());
    Map<String, List<String>> regionToServerGroupNames = new HashMap<>();
    regionToServerGroupNames.put(
        request.getRegion(),
        targetServerGroups.stream().map(TargetServerGroup::getName).collect(Collectors.toList()));

    result.put("deploy.server.groups", regionToServerGroupNames);
    return TaskResult.builder(ExecutionStatus.SUCCEEDED).context(result).build();
  }

  protected Location getLocation(Map<String, Object> operation) {
    return Location.region((String) operation.get("region"));
  }

  private static class ServerGroupRequest {
    private String credentials;
    private String cloudProvider;
    private String region;
    private List<String> serverGroupNames;

    public String getCredentials() {
      return credentials;
    }

    public void setCredentials(String credentials) {
      this.credentials = credentials;
    }

    public String getCloudProvider() {
      return cloudProvider;
    }

    public void setCloudProvider(String cloudProvider) {
      this.cloudProvider = cloudProvider;
    }

    public String getRegion() {
      return region;
    }

    public void setRegion(String region) {
      this.region = region;
    }

    public List<String> getServerGroupNames() {
      return this.serverGroupNames;
    }

    public void setServerGroupNames(List<String> serverGroupNames) {
      this.serverGroupNames = serverGroupNames;
    }
  }
}
