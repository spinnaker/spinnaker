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

import java.util.*;
import java.util.stream.Collectors;
import com.netflix.frigga.Names;
import com.netflix.spinnaker.moniker.Moniker;
import com.netflix.spinnaker.orca.ExecutionStatus;
import com.netflix.spinnaker.orca.RetryableTask;
import com.netflix.spinnaker.orca.TaskResult;
import com.netflix.spinnaker.orca.clouddriver.KatoService;
import com.netflix.spinnaker.orca.clouddriver.model.TaskId;
import com.netflix.spinnaker.orca.clouddriver.pipeline.servergroup.support.Location;
import com.netflix.spinnaker.orca.clouddriver.pipeline.servergroup.support.TargetServerGroup;
import com.netflix.spinnaker.orca.clouddriver.tasks.AbstractCloudProviderAwareTask;
import com.netflix.spinnaker.orca.clouddriver.utils.MonikerHelper;
import com.netflix.spinnaker.orca.clouddriver.utils.OortHelper;
import com.netflix.spinnaker.orca.pipeline.model.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

public abstract class AbstractBulkServerGroupTask extends AbstractCloudProviderAwareTask implements RetryableTask {
  private static final Logger LOGGER = LoggerFactory.getLogger(AbstractBulkServerGroupTask.class);

  @Autowired
  protected OortHelper oortHelper;

  @Autowired
  protected MonikerHelper monikerHelper;

  @Autowired
  protected KatoService katoService;

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
  public TaskResult execute(Stage stage) {
    ServerGroupRequest request = (ServerGroupRequest) stage.mapTo(ServerGroupRequest.class);
    if (request.getServerGroupNames() == null || request.getServerGroupNames().isEmpty()) {
      throw new IllegalArgumentException("Server group names must be provided");
    }
    String clusterName = monikerHelper.getClusterNameFromStage(stage, request.getServerGroupNames().get(0));
    Map cluster = oortHelper.getCluster(
      monikerHelper.getAppNameFromStage(stage, request.getServerGroupNames().get(0)),
      request.getCredentials(),
      clusterName,
      request.getCloudProvider()
    ).orElseThrow(
      () -> new IllegalArgumentException(String.format("No Cluster details found for %s", clusterName))
    );

    List<Map> serverGroups = Optional.ofNullable((List<Map>) cluster.get("serverGroups"))
      .orElseThrow(
        () -> new IllegalArgumentException(String.format("No server groups found for cluster %s", clusterName))
      );

    Location location = Optional.ofNullable(Location.region(request.getRegion()))
      .orElseThrow(
        () -> new IllegalArgumentException("A region is required for this operation")
      );

    List<TargetServerGroup> targetServerGroups = new ArrayList<>();
    serverGroups.forEach( sg -> {
      TargetServerGroup tg = new TargetServerGroup(sg);
      if (tg.getLocation(location.getType()).equals(location) && request.getServerGroupNames().contains(tg.getName())) {
        targetServerGroups.add(tg);
      }
    });

    LOGGER.info("Found target server groups {}", targetServerGroups);
    if (targetServerGroups.isEmpty()) {
      throw new TargetServerGroup.NotFoundException();
    }

    List<Map<String, Map>> operations = new ArrayList<>();
    targetServerGroups.forEach( targetServerGroup -> {
      Map<String , Map> tmp = new HashMap<>();
      Map operation = targetServerGroup.toClouddriverOperationPayload(request.getCredentials());
      Moniker moniker = targetServerGroup.getMoniker();
      if (moniker == null || moniker.getCluster() == null) {
        moniker = MonikerHelper.friggaToMoniker(targetServerGroup.getName());
      }
      validateClusterStatus(operation, moniker);
      tmp.put(getClouddriverOperation(), operation);
      operations.add(tmp);
    });

    TaskId taskId = katoService.requestOperations(request.cloudProvider, operations).toBlocking().first();

    Map<String, Object> result = new HashMap<>();
    result.put("deploy.account.name", request.getCredentials());
    result.put("kato.last.task.id", taskId);
    Map<String, List<String>> regionToServerGroupNames = new HashMap<>();
    regionToServerGroupNames.put(request.getRegion(), targetServerGroups
      .stream()
      .map(TargetServerGroup::getName)
      .collect(Collectors.toList()));

    result.put("deploy.server.groups", regionToServerGroupNames);
    return new TaskResult(ExecutionStatus.SUCCEEDED, result);
  }

  protected Location getLocation(Map operation) {
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
