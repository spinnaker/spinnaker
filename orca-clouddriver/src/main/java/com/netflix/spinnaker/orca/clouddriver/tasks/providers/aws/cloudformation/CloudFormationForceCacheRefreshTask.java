/*
 * Copyright (c) 2019 Schibsted Media Group.
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

package com.netflix.spinnaker.orca.clouddriver.tasks.providers.aws.cloudformation;

import static com.netflix.spinnaker.orca.api.pipeline.models.ExecutionStatus.RUNNING;
import static com.netflix.spinnaker.orca.api.pipeline.models.ExecutionStatus.SUCCEEDED;
import static java.net.HttpURLConnection.HTTP_OK;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spectator.api.Id;
import com.netflix.spectator.api.Registry;
import com.netflix.spinnaker.orca.api.pipeline.OverridableTimeoutRetryableTask;
import com.netflix.spinnaker.orca.api.pipeline.TaskResult;
import com.netflix.spinnaker.orca.api.pipeline.models.StageExecution;
import com.netflix.spinnaker.orca.clouddriver.CloudDriverCacheService;
import com.netflix.spinnaker.orca.clouddriver.CloudDriverCacheStatusService;
import com.netflix.spinnaker.orca.clouddriver.utils.CloudProviderAware;
import java.io.IOException;
import java.time.Clock;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import javax.annotation.Nonnull;
import lombok.Data;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import retrofit.client.Response;

@Slf4j
@Component
public class CloudFormationForceCacheRefreshTask
    implements CloudProviderAware, OverridableTimeoutRetryableTask {
  static final String REFRESH_TYPE = "CloudFormation";

  Clock clock = Clock.systemUTC();
  private final ObjectMapper objectMapper;
  private final Registry registry;
  private final Id durationTimerId;

  private final long backoffPeriod = TimeUnit.SECONDS.toMillis(10);
  private final long timeout = TimeUnit.MINUTES.toMillis(20);
  private final long autoSucceedAfterMs = TimeUnit.MINUTES.toMillis(12);

  private final CloudDriverCacheService cacheService;
  private final CloudDriverCacheStatusService cacheStatusService;

  public CloudFormationForceCacheRefreshTask(
      Registry registry,
      CloudDriverCacheService cacheService,
      CloudDriverCacheStatusService cacheStatusService,
      ObjectMapper objectMapper) {

    this.registry = registry;
    this.cacheService = cacheService;
    this.cacheStatusService = cacheStatusService;
    this.objectMapper = objectMapper;
    this.durationTimerId = registry.createId("cloudformationStackForceCacheRefreshTask.duration");
  }

  @Override
  public TaskResult execute(@Nonnull StageExecution stage) {
    Long startTime = stage.getStartTime();
    if (startTime == null) {
      throw new IllegalStateException("Stage has no start time, cannot be executed.");
    }
    long duration = clock.millis() - startTime;
    if (duration > autoSucceedAfterMs) {
      log.info(
          "{}: Force cache refresh never finished processing... assuming the cache is in sync and continuing...",
          stage.getExecution().getId());
      registry
          .timer(durationTimerId.withTags("success", "true", "outcome", "autoSucceed"))
          .record(duration, TimeUnit.MILLISECONDS);
      return TaskResult.SUCCEEDED;
    }

    String cloudProvider = getCloudProvider(stage);

    CloudFormationForceCacheRefreshTask.StageData stageData = fromStage(stage);

    if (stageData.deployedStacks.isEmpty()) {
      stageData.deployedStacks = getDeployedStacks(stage);
    }

    checkPendingRefreshes(cloudProvider, stageData, startTime);
    refreshStacks(cloudProvider, stageData);

    if (stageData.processedStacks.equals(stageData.deployedStacks)) {
      stageData.reset();
      return TaskResult.builder(SUCCEEDED).context(toContext(stageData)).build();
    }

    return TaskResult.builder(RUNNING).context(toContext(stageData)).build();
  }

  private void checkPendingRefreshes(
      String provider, CloudFormationForceCacheRefreshTask.StageData stageData, long startTime) {
    Set<ScopedStack> toCheck = new HashSet<ScopedStack>(stageData.refreshedStacks);
    toCheck.removeAll(stageData.processedStacks);

    List<ScopedStack> stacksToCheck =
        stageData.refreshedStacks.stream()
            .filter(m -> !stageData.processedStacks.contains(m))
            .collect(Collectors.toList());

    if (stacksToCheck.isEmpty()) {
      return;
    }

    List<PendingScopedStack> pendingRefreshes =
        objectMapper.convertValue(
            cacheStatusService.pendingForceCacheUpdates(provider, REFRESH_TYPE),
            new TypeReference<List<PendingScopedStack>>() {});

    for (PendingScopedStack stack : pendingRefreshes) {
      ScopedStack matchingStack =
          stageData.getRefreshedStacks().stream()
              .filter(t -> t.getFullStackName().contains(stack.getScopedStack().getStackName()))
              .findFirst()
              .orElse(null);

      if (matchingStack == null) {
        continue;
      }

      if (stack.getProcessedCount() > 0 && stack.getCacheTime() > startTime) {
        log.info("Refresh for {} has been processed {}", stack);
        stageData.processedStacks.add(matchingStack);
      }
    }
  }

  private void refreshStacks(
      String provider, CloudFormationForceCacheRefreshTask.StageData stageData) {
    Set<ScopedStack> toRefresh = new HashSet<ScopedStack>(stageData.deployedStacks);
    toRefresh.removeAll(stageData.refreshedStacks);

    for (ScopedStack stack : toRefresh) {
      CloudDriverScopedStack cloudDriverStack = new CloudDriverScopedStack(stack);
      Map<String, Object> request =
          objectMapper.convertValue(cloudDriverStack, new TypeReference<Map<String, Object>>() {});

      try {
        Response response = cacheService.forceCacheUpdate(provider, REFRESH_TYPE, request);
        if (response.getStatus() == HTTP_OK) {
          log.info("Refresh of {} succeeded immediately", stack);
          stageData.processedStacks.add(stack);
        } else {
          stack.fullStackName = extractFullStackName(response).get();
        }

        stageData.refreshedStacks.add(stack);
      } catch (Exception e) {
        log.warn("Failed to refresh {}: ", stack, e);
        stageData.errors.add(e.getMessage());
      }
    }
  }

  private Set<ScopedStack> getDeployedStacks(StageExecution stage) {
    String credentials = getCredentials(stage);
    List<String> regions = (List<String>) stage.getContext().get("regions");
    String stackName = (String) stage.getContext().get("stackName");

    if (credentials.isEmpty() || regions.isEmpty() || stackName.isEmpty()) {
      throw new IllegalStateException("Missing stage context in " + stage);
    }

    return regions.stream()
        .map(
            r -> {
              ScopedStack scopedStack = new ScopedStack(credentials, r, stackName);
              return scopedStack;
            })
        .collect(Collectors.toSet());
  }

  @Override
  public long getBackoffPeriod() {
    return backoffPeriod;
  }

  @Override
  public long getTimeout() {
    return timeout;
  }

  private CloudFormationForceCacheRefreshTask.StageData fromStage(StageExecution stage) {
    try {
      return objectMapper.readValue(
          objectMapper.writeValueAsString(stage.getContext()),
          CloudFormationForceCacheRefreshTask.StageData.class);
    } catch (IOException e) {
      throw new IllegalStateException(
          "Malformed stage context in " + stage + ": " + e.getMessage(), e);
    }
  }

  private Map<String, Object> toContext(CloudFormationForceCacheRefreshTask.StageData stageData) {
    return objectMapper.convertValue(stageData, new TypeReference<Map<String, Object>>() {});
  }

  private Optional<String> extractFullStackName(Response response) {
    try {
      CachedResponse cachedResponse =
          objectMapper.readValue(
              response.getBody().in(), CloudFormationForceCacheRefreshTask.CachedResponse.class);
      return cachedResponse.getCachedResponseStacks().getStacks().stream().findFirst();
    } catch (IOException e) {
      throw new IllegalStateException("Malformed response from clouddriver" + e.getMessage(), e);
    }
  }

  @Data
  @JsonIgnoreProperties(ignoreUnknown = true)
  private static class StageData {
    Set<CloudFormationForceCacheRefreshTask.ScopedStack> deployedStacks = new HashSet<>();

    @JsonProperty("refreshed.scopedStacks")
    Set<CloudFormationForceCacheRefreshTask.ScopedStack> refreshedStacks = new HashSet<>();

    @JsonProperty("processed.scopedStacks")
    Set<CloudFormationForceCacheRefreshTask.ScopedStack> processedStacks = new HashSet<>();

    Set<String> errors = new HashSet<>();

    void reset() {
      deployedStacks = Collections.emptySet();
      refreshedStacks = Collections.emptySet();
      processedStacks = Collections.emptySet();
    }
  }

  @Value
  @JsonIgnoreProperties(ignoreUnknown = true)
  private static class PendingScopedStack {
    @JsonProperty("details")
    ScopedStack scopedStack;

    Long processedTime;
    Long cacheTime;
    Long processedCount;

    PendingScopedStack(
        @JsonProperty("processedTime") Long processedTime,
        @JsonProperty("cacheTime") Long cacheTime,
        @JsonProperty("processedCount") Long processedCount,
        @JsonProperty("details") ScopedStack scopedStack) {
      this.cacheTime = cacheTime;
      this.processedCount = processedCount;
      this.scopedStack = scopedStack;
      this.processedTime = processedTime;
    }
  }

  @Data
  private static class CloudDriverScopedStack {
    final String credentials;
    final String stackName;
    final List<String> region;

    CloudDriverScopedStack(ScopedStack scopedStack) {
      this.credentials = scopedStack.credentials;
      this.region = Arrays.asList(scopedStack.region);
      this.stackName = scopedStack.stackName;
    }
  }

  @Data
  @JsonIgnoreProperties(ignoreUnknown = true)
  private static class ScopedStack {
    final String credentials;
    final String stackName;
    final String region;
    String fullStackName;

    ScopedStack(
        @JsonProperty("account") String credentials,
        @JsonProperty("region") String region,
        @JsonProperty("id") String stackName) {
      this.credentials = credentials;
      this.region = region;
      this.stackName = stackName;
      this.fullStackName = new String();
    }
  }

  @Value
  private static class CachedResponse {
    final CachedResponseStacks cachedResponseStacks;

    CachedResponse(
        @JsonProperty("cachedIdentifiersByType") CachedResponseStacks cachedReponseStacks) {
      this.cachedResponseStacks = cachedReponseStacks;
    }
  }

  @Value
  private static class CachedResponseStacks {
    List<String> stacks;

    CachedResponseStacks(@JsonProperty("stacks") List<String> stacks) {
      this.stacks = stacks;
    }
  }
}
