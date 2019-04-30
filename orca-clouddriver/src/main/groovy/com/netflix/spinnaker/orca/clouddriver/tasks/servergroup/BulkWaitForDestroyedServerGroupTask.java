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

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.frigga.Names;
import com.netflix.spinnaker.moniker.Moniker;
import com.netflix.spinnaker.orca.ExecutionStatus;
import com.netflix.spinnaker.orca.RetryableTask;
import com.netflix.spinnaker.orca.TaskResult;
import com.netflix.spinnaker.orca.clouddriver.OortService;
import com.netflix.spinnaker.orca.clouddriver.tasks.AbstractCloudProviderAwareTask;
import com.netflix.spinnaker.orca.clouddriver.utils.MonikerHelper;
import com.netflix.spinnaker.orca.pipeline.model.Stage;
import com.netflix.spinnaker.orca.retrofit.exceptions.RetrofitExceptionHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import retrofit.RetrofitError;
import retrofit.client.Response;

@Component
public class BulkWaitForDestroyedServerGroupTask extends AbstractCloudProviderAwareTask implements RetryableTask {
  private static final Logger LOGGER = LoggerFactory.getLogger(BulkWaitForDestroyedServerGroupTask.class);

  @Autowired
  private OortService oortService;

  @Autowired
  private ObjectMapper objectMapper;

  @Autowired
  private MonikerHelper monikerHelper;

  @Override
  public TaskResult execute(Stage stage) {
    String region = (String) stage.getContext().get("region");
    Map<String, List<String>> regionToServerGroups = (Map<String, List<String>>) stage.getContext().get("deploy.server.groups");
    List<String> serverGroupNames = regionToServerGroups.get(region);
    try {
      Response response = oortService.getCluster(
        monikerHelper.getAppNameFromStage(stage, serverGroupNames.get(0)),
        getCredentials(stage),
        monikerHelper.getClusterNameFromStage(stage, serverGroupNames.get(0)),
        getCloudProvider(stage)
      );

      if (response.getStatus() != 200) {
        return TaskResult.RUNNING;
      }

      Map cluster = objectMapper.readValue(response.getBody().in(), Map.class);
      Map<String, Object> output = new HashMap<>();
      output.put("remainingInstances", Collections.emptyList());
      if (cluster == null || cluster.get("serverGroups") == null) {
        return TaskResult.builder(ExecutionStatus.SUCCEEDED).context(output).build();
      }

      List<Map<String, Object>> serverGroups = getServerGroups(region, cluster, serverGroupNames);
      if (serverGroups.isEmpty()) {
        return TaskResult.builder(ExecutionStatus.SUCCEEDED).context(output).build();
      }

      List<Map<String, Object>> instances = getInstances(serverGroups);
      LOGGER.info("{} not destroyed, found instances {}", serverGroupNames, instances);
      output.put("remainingInstances", instances);
      return TaskResult.builder(ExecutionStatus.RUNNING).context(output).build();
    } catch (RetrofitError e) {
      return handleRetrofitError(stage, e);
    } catch (IOException e) {
      throw new IllegalArgumentException("Failed to fetch cluster details", e);
    }
  }

  private TaskResult handleRetrofitError(Stage stage, RetrofitError e) {
    if (e.getResponse() == null) {
      throw e;
    }
    switch (e.getResponse().getStatus()) {
      case 404:
        return TaskResult.SUCCEEDED;
      case 500:
        Map<String, Object> error = new HashMap<>();
        error.put("lastRetrofitException", new RetrofitExceptionHandler().handle(stage.getName(), e));
        LOGGER.error("Unexpected retrofit error {}", error.get("lastRetrofitException"), e);
        return TaskResult.builder(ExecutionStatus.RUNNING).context(error).build();
      default:
        throw e;
    }
  }

  private List<Map<String, Object>> getServerGroups(String region, Map cluster, List<String> serverGroupNames) {
    return ((List<Map<String, Object>>) cluster.get("serverGroups"))
      .stream()
      .filter(sg  -> serverGroupNames.contains(sg.get("name")) && sg.get("region").equals(region))
      .collect(Collectors.toList());
  }

  private List<Map<String, Object>> getInstances(List<Map<String, Object>> serverGroups) {
    List<Map<String, Object>> instances = new ArrayList<>();
    serverGroups.forEach(serverGroup -> {
      if (serverGroup.get("instances") != null) {
        instances.addAll((List<Map<String, Object>>) serverGroup.get("instances"));
      }
    });

    return instances;
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
