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

package com.netflix.spinnaker.orca.clouddriver.tasks.cluster;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableMap;
import com.netflix.spinnaker.kork.core.RetrySupport;
import com.netflix.spinnaker.moniker.Moniker;
import com.netflix.spinnaker.orca.ExecutionStatus;
import com.netflix.spinnaker.orca.RetryableTask;
import com.netflix.spinnaker.orca.TaskResult;
import com.netflix.spinnaker.orca.clouddriver.OortService;
import com.netflix.spinnaker.orca.clouddriver.tasks.AbstractCloudProviderAwareTask;
import com.netflix.spinnaker.orca.pipeline.model.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import retrofit.client.Response;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Component
public class DetermineRollbackCandidatesTask extends AbstractCloudProviderAwareTask implements RetryableTask {
  private static final Logger logger = LoggerFactory.getLogger(DetermineRollbackCandidatesTask.class);

  private final ObjectMapper objectMapper;
  private final RetrySupport retrySupport;
  private final OortService oortService;

  @Autowired
  public DetermineRollbackCandidatesTask(ObjectMapper objectMapper,
                                         RetrySupport retrySupport,
                                         OortService oortService) {
    this.objectMapper = objectMapper;
    this.retrySupport = retrySupport;
    this.oortService = oortService;
  }

  @Override
  public long getBackoffPeriod() {
    return TimeUnit.SECONDS.toMillis(15);
  }

  @Override
  public long getTimeout() {
    return TimeUnit.MINUTES.toMillis(5);
  }

  @Nonnull
  @Override
  public TaskResult execute(@Nonnull Stage stage) {
    Map<String, String> rollbackTypes = new HashMap<>();
    Map<String, Map> rollbackContexts = new HashMap<>();

    StageData stageData = stage.mapTo(StageData.class);
    Map<String, Object> cluster;

    try {
      cluster = retrySupport.retry(() -> fetchCluster(
        stageData.moniker.getApp(),
        stageData.account,
        stageData.moniker.getCluster(),
        stageData.cloudProvider
      ), 5, 1000, false);
    } catch(Exception e) {
      logger.warn(
        "Failed to fetch cluster, retrying! (application: {}, account: {}, cluster: {}, cloudProvider: {})",
        stageData.moniker.getApp(),
        stageData.account,
        stageData.moniker.getCluster(),
        stageData.cloudProvider,
        e
      );
      return new TaskResult(ExecutionStatus.RUNNING);
    }

    List<Map<String, Object>> serverGroups = (List<Map<String, Object>>) cluster.get("serverGroups");
    serverGroups.sort(Comparator.comparing((Map o) -> ((Long) o.get("createdTime"))).reversed());

    for (String region : stageData.regions) {
      Map<String, Object> newestServerGroupInRegion = serverGroups
        .stream()
        .filter(s -> region.equalsIgnoreCase((String) s.get("region")))
        .findFirst()
        .orElse(null);

      if (newestServerGroupInRegion != null) {
        rollbackTypes.put(region, "PREVIOUS_IMAGE");
        rollbackContexts.put(
          region,
          ImmutableMap.builder()
            .put("rollbackServerGroupName", newestServerGroupInRegion.get("name"))
            .put("targetHealthyRollbackPercentage", 100)
            .build()
        );
      }
    }

    return new TaskResult(
      ExecutionStatus.SUCCEEDED,
      Collections.emptyMap(),
      ImmutableMap.<String, Object>builder()
        .put("rollbackTypes", rollbackTypes)
        .put("rollbackContexts", rollbackContexts)
        .build()
    );
  }

  private Map<String, Object> fetchCluster(String application,
                                           String account,
                                           String cluster,
                                           String cloudProvider) {
    try {
      Response response = oortService.getCluster(application, account, cluster, cloudProvider);
      return (Map<String, Object>) objectMapper.readValue(response.getBody().in(), Map.class);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  static class StageData {
    public String account;
    public String cloudProvider;

    public Moniker moniker;
    public List<String> regions;
  }
}
