/*
 * Copyright 2017 Google, Inc.
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
 *
 */

package com.netflix.spinnaker.orca.clouddriver.tasks.manifest;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spectator.api.Id;
import com.netflix.spectator.api.Registry;
import com.netflix.spinnaker.orca.RetryableTask;
import com.netflix.spinnaker.orca.Task;
import com.netflix.spinnaker.orca.TaskResult;
import com.netflix.spinnaker.orca.clouddriver.CloudDriverCacheService;
import com.netflix.spinnaker.orca.clouddriver.CloudDriverCacheStatusService;
import com.netflix.spinnaker.orca.clouddriver.tasks.AbstractCloudProviderAwareTask;
import com.netflix.spinnaker.orca.pipeline.model.Stage;
import lombok.Data;
import lombok.Getter;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import retrofit.client.Response;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.time.Clock;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static com.netflix.spinnaker.orca.ExecutionStatus.RUNNING;
import static com.netflix.spinnaker.orca.ExecutionStatus.SUCCEEDED;
import static java.net.HttpURLConnection.HTTP_OK;

@Component
@Slf4j
public class ManifestForceCacheRefreshTask extends AbstractCloudProviderAwareTask implements Task, ManifestAware, RetryableTask {
  private final static String REFRESH_TYPE = "manifest";
  public final static String TASK_NAME = "forceCacheRefresh";

  @Getter
  private final long backoffPeriod = TimeUnit.SECONDS.toMillis(10);
  @Getter
  private final long timeout = TimeUnit.MINUTES.toMillis(15);

  private final long autoSucceedAfterMs = TimeUnit.MINUTES.toMillis(12);
  private final Clock clock;
  private final Registry registry;
  private final CloudDriverCacheService cacheService;
  private final CloudDriverCacheStatusService cacheStatusService;
  private final ObjectMapper objectMapper;
  private final Id durationTimerId;

  @Autowired
  public ManifestForceCacheRefreshTask(Registry registry,
                                       CloudDriverCacheService cacheService,
                                       CloudDriverCacheStatusService cacheStatusService,
                                       ObjectMapper objectMapper) {
    this(registry, cacheService, cacheStatusService, objectMapper, Clock.systemUTC());
  }

  ManifestForceCacheRefreshTask(Registry registry,
                                CloudDriverCacheService cacheService,
                                CloudDriverCacheStatusService cacheStatusService,
                                ObjectMapper objectMapper, Clock clock) {
    this.registry = registry;
    this.cacheService = cacheService;
    this.cacheStatusService = cacheStatusService;
    this.objectMapper = objectMapper;
    this.clock = clock;
    this.durationTimerId = registry.createId("manifestForceCacheRefreshTask.duration");
  }

  @Override
  @Nonnull
  public TaskResult execute(@Nonnull Stage stage) {
    Long startTime = stage.getStartTime();
    if (startTime == null) {
      throw new IllegalStateException("Stage has no start time, cannot be executing.");
    }
    long duration = clock.millis() - startTime;
    if (duration > autoSucceedAfterMs) {
      log.info("{}: Force cache refresh never finished processing... assuming the cache is in sync and continuing...", stage.getExecution().getId());
      registry.timer(durationTimerId.withTags("success", "true", "outcome", "autoSucceed"))
        .record(duration, TimeUnit.MILLISECONDS);
      return TaskResult.SUCCEEDED;
    }

    String cloudProvider = getCloudProvider(stage);
    StageData stageData = fromStage(stage);
    stageData.deployedManifests = getDeployedManifests(stage);

    checkPendingRefreshes(cloudProvider, stageData, startTime);

    refreshManifests(cloudProvider, stageData);

    if (allManifestsProcessed(stageData)) {
      registry.timer(durationTimerId.withTags("success", "true", "outcome", "complete"))
        .record(duration, TimeUnit.MILLISECONDS);
      return TaskResult.builder(SUCCEEDED).context(toContext(stageData)).build();
    }

    return TaskResult.builder(RUNNING).context(toContext(stageData)).build();
  }

  /**
   * Checks whether all manifests deployed in the stage have been processed by the cache
   * @return true if all manifests have been processed
   */
  private boolean allManifestsProcessed(StageData stageData) {
    return stageData.getProcessedManifests().containsAll(stageData.getDeployedManifests());
  }

  /**
   * Checks on the status of any pending on-demand cache refreshes. If a pending refresh has been processed, adds the
   * corresponding manifest to processedManifests; if a pending refresh is not found in clouddriver or is invalid,
   * removes the corresponding manifest from refreshedManifests
   */
  private void checkPendingRefreshes(String provider, StageData stageData, long startTime) {
    Set<ScopedManifest> refreshedManifests = stageData.getRefreshedManifests();
    Set<ScopedManifest> processedManifests = stageData.getProcessedManifests();

    List<ScopedManifest> manifestsToCheck = refreshedManifests.stream()
      .filter(m -> !processedManifests.contains(m))
      .collect(Collectors.toList());

    if (manifestsToCheck.isEmpty()) {
      return;
    }

    Collection<PendingRefresh> pendingRefreshes = objectMapper.convertValue(
      cacheStatusService.pendingForceCacheUpdates(provider, REFRESH_TYPE),
      new TypeReference<Collection<PendingRefresh>>() { }
    );

    for (ScopedManifest manifest : manifestsToCheck) {
      RefreshStatus refreshStatus = pendingRefreshes.stream()
        .filter(pr -> pr.getScopedManifest() != null)
        .filter(pr -> refreshMatches(pr.getScopedManifest(), manifest))
        .map(pr -> getRefreshStatus(pr, startTime))
        .sorted()
        .findFirst()
        .orElse(RefreshStatus.INVALID);

      if (refreshStatus == RefreshStatus.PROCESSED) {
        log.debug("Pending manifest refresh of {} completed", manifest);
        processedManifests.add(manifest);
      } else if (refreshStatus == RefreshStatus.PENDING) {
        log.debug("Pending manifest refresh of {} still pending", manifest);
      } else {
        log.warn("No valid pending refresh of {}", manifest);
        refreshedManifests.remove(manifest);
      }
    }
  }

  private boolean refreshMatches(ScopedManifest refresh, ScopedManifest manifest) {
    return manifest.account.equals(refresh.account)
      && (manifest.location.equals(refresh.location) || StringUtils.isEmpty(refresh.location))
      && manifest.name.equals(refresh.name);
  }

  private RefreshStatus getRefreshStatus(PendingRefresh pendingRefresh, long startTime) {
    ScopedManifest scopedManifest = pendingRefresh.getScopedManifest();
    if (pendingRefresh.cacheTime == null || pendingRefresh.processedTime == null || scopedManifest == null) {
      log.warn("Pending refresh of {} is missing cache metadata", pendingRefresh);
      return RefreshStatus.INVALID;
    } else if (pendingRefresh.cacheTime < startTime) {
      log.warn("Pending refresh of {} is stale", pendingRefresh);
      return RefreshStatus.INVALID;
    } else if (pendingRefresh.processedTime < startTime) {
      log.info("Pending refresh of {} was cached as a part of this request, but not processed", pendingRefresh);
      return RefreshStatus.PENDING;
    } else {
      return RefreshStatus.PROCESSED;
    }
  }

  private List<ScopedManifest> manifestsNeedingRefresh(StageData stageData) {
    List<ScopedManifest> deployedManifests = stageData.getDeployedManifests();
    Set<ScopedManifest> refreshedManifests = stageData.getRefreshedManifests();
    if (deployedManifests.isEmpty()) {
      log.warn("No manifests were deployed, nothing to refresh...");
    }

    return deployedManifests.stream()
      .filter(m -> !refreshedManifests.contains(m))
      .collect(Collectors.toList());
  }

  private List<ScopedManifest> getDeployedManifests(Stage stage) {
    String account = getCredentials(stage);
    Map<String, List<String>> deployedManifests = manifestNamesByNamespace(stage);
    return deployedManifests.entrySet().stream()
      .flatMap(e -> e.getValue().stream().map(v -> new ScopedManifest(account, e.getKey(), v)))
      .collect(Collectors.toList());
  }

  /**
   * Requests an on-demand cache refresh for any manifest without a refresh requests that is either pending or
   * processed. Adds each manifest to refreshedManifests; if the request to clouddriver was immediately processed,
   * also adds the manifest to processedManifests.
   */
  private void refreshManifests(String provider, StageData stageData) {
    List<ScopedManifest> manifests = manifestsNeedingRefresh(stageData);

    for (ScopedManifest manifest : manifests) {
      Map<String, String> request = objectMapper.convertValue(manifest, new TypeReference<Map<String, String>>() {});
      try {
        Response response = cacheService.forceCacheUpdate(provider, REFRESH_TYPE, request);
        if (response.getStatus() == HTTP_OK) {
          log.info("Refresh of {} succeeded immediately", manifest);
          stageData.getProcessedManifests().add(manifest);
        }

        stageData.getRefreshedManifests().add(manifest);
      } catch (Exception e) {
        log.warn("Failed to refresh {}: ", manifest, e);
        stageData.errors.add(e.getMessage());
      }
    }
  }

  private StageData fromStage(Stage stage) {
    try {
      return objectMapper.readValue(objectMapper.writeValueAsString(stage.getContext()), StageData.class);
    } catch (IOException e) {
      throw new IllegalStateException("Malformed stage context in " + stage + ": " + e.getMessage(), e);
    }
  }

  private Map<String, Object> toContext(StageData stageData) {
    return objectMapper.convertValue(stageData, new TypeReference<Map<String, Object>>() { });
  }

  @Data
  static private class PendingRefresh {
    @JsonProperty("details")
    ScopedManifest scopedManifest;
    Long processedTime;
    Long cacheTime;
    Long processedCount;
  }

  @Data
  @JsonIgnoreProperties(ignoreUnknown = true)
  static private class StageData {
    List<ScopedManifest> deployedManifests = Collections.emptyList();

    @JsonProperty("refreshed.scopedManifests")
    Set<ScopedManifest> refreshedManifests = new HashSet<>();

    @JsonProperty("processed.scopedManifests")
    Set<ScopedManifest> processedManifests = new HashSet<>();

    Set<String> errors = new HashSet<>();
  }

  @Value
  private static class ScopedManifest {
    final String account;
    final String location;
    final String name;

    ScopedManifest(
      @JsonProperty("account") String account,
      @JsonProperty("location") String location,
      @JsonProperty("name") String name
    ) {
      this.account = account;
      this.location = location;
      this.name = name;
    }
  }

  private enum RefreshStatus {
    PROCESSED,
    PENDING,
    INVALID
  }
}
