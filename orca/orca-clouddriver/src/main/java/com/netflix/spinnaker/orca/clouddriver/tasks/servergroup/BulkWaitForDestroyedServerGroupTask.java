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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.kork.retrofit.exceptions.SpinnakerHttpException;
import com.netflix.spinnaker.orca.api.pipeline.RetryableTask;
import com.netflix.spinnaker.orca.api.pipeline.TaskResult;
import com.netflix.spinnaker.orca.api.pipeline.models.ExecutionStatus;
import com.netflix.spinnaker.orca.api.pipeline.models.StageExecution;
import com.netflix.spinnaker.orca.clouddriver.OortService;
import com.netflix.spinnaker.orca.clouddriver.model.Cluster;
import com.netflix.spinnaker.orca.clouddriver.model.Instance;
import com.netflix.spinnaker.orca.clouddriver.model.ServerGroup;
import com.netflix.spinnaker.orca.clouddriver.utils.CloudProviderAware;
import com.netflix.spinnaker.orca.clouddriver.utils.MonikerHelper;
import com.netflix.spinnaker.orca.retrofit.exceptions.SpinnakerServerExceptionHandler;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import retrofit.client.Response;

@Component
public class BulkWaitForDestroyedServerGroupTask implements CloudProviderAware, RetryableTask {
  private static final Logger LOGGER =
      LoggerFactory.getLogger(BulkWaitForDestroyedServerGroupTask.class);

  @Autowired private OortService oortService;

  @Autowired private ObjectMapper objectMapper;

  @Autowired private MonikerHelper monikerHelper;

  @Override
  public TaskResult execute(StageExecution stage) {
    String region = (String) stage.getContext().get("region");
    Map<String, List<String>> regionToServerGroups =
        (Map<String, List<String>>) stage.getContext().get("deploy.server.groups");
    List<String> serverGroupNames = regionToServerGroups.get(region);
    try {
      Response response =
          oortService.getCluster(
              monikerHelper.getAppNameFromStage(stage, serverGroupNames.get(0)),
              getCredentials(stage),
              monikerHelper.getClusterNameFromStage(stage, serverGroupNames.get(0)),
              getCloudProvider(stage));

      // TODO: get rid of explicit status code handling
      if (response.getStatus() != 200) {
        return TaskResult.RUNNING;
      }

      Cluster cluster = objectMapper.readValue(response.getBody().in(), Cluster.class);
      Map<String, Object> output = new HashMap<>();
      output.put("remainingInstances", Collections.emptyList());
      if (cluster == null || cluster.getServerGroups() == null) {
        return TaskResult.builder(ExecutionStatus.SUCCEEDED).context(output).build();
      }

      List<ServerGroup> serverGroups = getServerGroups(region, cluster, serverGroupNames);
      if (serverGroups.isEmpty()) {
        return TaskResult.builder(ExecutionStatus.SUCCEEDED).context(output).build();
      }

      List<Instance> instances =
          serverGroups.stream()
              .map(ServerGroup::getInstances)
              .filter(Objects::nonNull)
              .flatMap(List::stream)
              .collect(Collectors.toList());

      LOGGER.info("{} not destroyed, found instances {}", serverGroupNames, instances);
      output.put("remainingInstances", instances);
      return TaskResult.builder(ExecutionStatus.RUNNING).context(output).build();
    } catch (SpinnakerHttpException e) {
      return handleSpinnakerHttpException(stage, e);
    } catch (IOException e) {
      throw new IllegalArgumentException("Failed to fetch cluster details", e);
    }
  }

  private TaskResult handleSpinnakerHttpException(StageExecution stage, SpinnakerHttpException e) {
    switch (e.getResponseCode()) {
      case 404:
        return TaskResult.SUCCEEDED;
      case 500:
        Map<String, Object> error = new HashMap<>();
        error.put(
            "lastSpinnakerException",
            new SpinnakerServerExceptionHandler().handle(stage.getName(), e));
        LOGGER.error("Unexpected http error {}", error.get("lastSpinnakerException"), e);
        return TaskResult.builder(ExecutionStatus.RUNNING).context(error).build();
      default:
        throw e;
    }
  }

  private List<ServerGroup> getServerGroups(
      String region, Cluster cluster, List<String> serverGroupNames) {
    return cluster.getServerGroups().stream()
        .filter(sg -> serverGroupNames.contains(sg.getName()) && sg.getRegion().equals(region))
        .collect(Collectors.toList());
  }

  @Override
  public long getBackoffPeriod() {
    return 5000;
  }

  @Override
  public long getTimeout() {
    return 10000;
  }
}
