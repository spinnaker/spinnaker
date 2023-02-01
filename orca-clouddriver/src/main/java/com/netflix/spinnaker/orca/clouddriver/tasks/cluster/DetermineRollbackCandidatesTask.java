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

import static com.netflix.spinnaker.orca.clouddriver.pipeline.servergroup.RollbackServerGroupStage.RollbackType.*;
import static com.netflix.spinnaker.orca.clouddriver.pipeline.servergroup.rollback.PreviousImageRollbackSupport.*;
import static java.lang.String.format;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableMap;
import com.netflix.spinnaker.kork.core.RetrySupport;
import com.netflix.spinnaker.moniker.Moniker;
import com.netflix.spinnaker.orca.api.pipeline.RetryableTask;
import com.netflix.spinnaker.orca.api.pipeline.TaskResult;
import com.netflix.spinnaker.orca.api.pipeline.models.ExecutionStatus;
import com.netflix.spinnaker.orca.api.pipeline.models.StageExecution;
import com.netflix.spinnaker.orca.clouddriver.CloudDriverService;
import com.netflix.spinnaker.orca.clouddriver.FeaturesService;
import com.netflix.spinnaker.orca.clouddriver.model.Cluster;
import com.netflix.spinnaker.orca.clouddriver.model.ServerGroup;
import com.netflix.spinnaker.orca.clouddriver.model.ServerGroup.Capacity;
import com.netflix.spinnaker.orca.clouddriver.model.ServerGroup.RollbackDetails;
import com.netflix.spinnaker.orca.clouddriver.pipeline.servergroup.rollback.PreviousImageRollbackSupport;
import com.netflix.spinnaker.orca.clouddriver.utils.CloudProviderAware;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * The {@code DetermineRollbackCandidatesTask} task determines how one or more regions of a cluster
 * should be rolled back.
 *
 * <p>If the stage's context contains an `originalServerGroup` key, then this value is used as the
 * server group to roll back to.
 *
 * <p>If the `originalServerGroup` is not specified in the stage context, the determination is based
 * on inspecting the most recently deployed (and enabled!) server group in each region.
 *
 * <p>If this server group has the `spinnaker:metadata` entity tag: - rollback to a previous server
 * group (if exists!) with the `spinnaker:metadata` image id - if no such server group exists, clone
 * forward with the `spinnaker:metadata` image id
 *
 * <p>If this server group does _not_ have a `spinnaker:metadata` entity tag: - rollback to the
 * previous server group (if exists!)
 */
@Component
public class DetermineRollbackCandidatesTask implements CloudProviderAware, RetryableTask {
  private static final Logger logger =
      LoggerFactory.getLogger(DetermineRollbackCandidatesTask.class);

  private static final TypeReference<List<ServerGroup>> listOfServerGroupsTypeReference =
      new TypeReference<>() {};

  private final boolean dynamicRollbackTimeoutEnabled;

  private final RetrySupport retrySupport;
  private final CloudDriverService cloudDriverService;
  private final PreviousImageRollbackSupport previousImageRollbackSupport;

  @Autowired
  public DetermineRollbackCandidatesTask(
      ObjectMapper objectMapper,
      RetrySupport retrySupport,
      CloudDriverService cloudDriverService,
      FeaturesService featuresService,
      @Value("${rollback.timeout.enabled:false}") boolean dynamicRollbackTimeoutEnabled) {
    this.retrySupport = retrySupport;
    this.cloudDriverService = cloudDriverService;
    this.previousImageRollbackSupport =
        new PreviousImageRollbackSupport(
            objectMapper, cloudDriverService, featuresService, retrySupport);
    this.dynamicRollbackTimeoutEnabled = dynamicRollbackTimeoutEnabled;
  }

  @Override
  public long getBackoffPeriod() {
    return TimeUnit.SECONDS.toMillis(15);
  }

  @Override
  public long getTimeout() {
    return TimeUnit.MINUTES.toMillis(5);
  }

  public long getDynamicTimeout(StageExecution stage) {
    boolean shouldUseDynamicRollbackTimeout =
        dynamicRollbackTimeoutEnabled && stage.getContext().containsKey("rollbackTimeout");
    if (shouldUseDynamicRollbackTimeout) {
      return TimeUnit.MINUTES.toMillis((int) stage.getContext().get("rollbackTimeout"));
    }

    return getTimeout();
  }

  @Nonnull
  @Override
  public TaskResult execute(@Nonnull StageExecution stage) {
    StageData stageData = stage.mapTo(StageData.class);
    Moniker moniker =
        populateMonikerWithServerGroupInfo(
            stageData.moniker,
            stageData.credentials,
            stageData.regions.get(0),
            stageData.serverGroup);
    if (moniker == null) {
      return TaskResult.RUNNING;
    }

    List<ServerGroup> serverGroups =
        getServerGroups(moniker, stageData.credentials, stageData.cloudProvider);
    if (serverGroups == null) {
      return TaskResult.RUNNING;
    }

    return determineRollbackCandidates(stage, stageData, moniker.getCluster(), serverGroups);
  }

  private TaskResult determineRollbackCandidates(
      StageExecution stage, StageData stageData, String cluster, List<ServerGroup> serverGroups) {

    List<Map> imagesToRestore = new ArrayList<>();
    Map<String, String> rollbackTypes = new HashMap<>();
    Map<String, Map> rollbackContexts = new HashMap<>();

    for (String region : stageData.regions) {
      List<ServerGroup> allServerGroupsInRegion =
          serverGroups.stream()
              .filter(s -> region.equalsIgnoreCase(s.region))
              .collect(Collectors.toList());

      if (!isRollbackPossible(allServerGroupsInRegion, cluster, region)) {
        continue;
      }

      List<ServerGroup> enabledServerGroupsInRegion =
          allServerGroupsInRegion.stream()
              .filter(DetermineRollbackCandidatesTask::isServerGroupEnabled)
              .collect(Collectors.toList());

      ServerGroup serverGroupToRollBack = getServerGroupToRollBack(enabledServerGroupsInRegion);

      RollbackDetails candidateDetails =
          findBestCandidate(
              allServerGroupsInRegion,
              enabledServerGroupsInRegion,
              serverGroupToRollBack,
              stage,
              stageData,
              cluster,
              region);

      logger.info(
          "Found rollback candidate in cluster {}, region {}: {}",
          cluster,
          region,
          candidateDetails.rollbackContext.get("restoreServerGroupName"));

      imagesToRestore.add(getImageToRestore(region, candidateDetails));
      rollbackTypes.put(region, candidateDetails.rollbackType.toString());
      rollbackContexts.put(
          region,
          getRollbackContext(
              stageData.targetHealthyRollbackPercentage, serverGroupToRollBack, candidateDetails));
    }

    return buildResult(imagesToRestore, rollbackTypes, rollbackContexts);
  }

  private RollbackDetails findBestCandidate(
      List<ServerGroup> allServerGroupsInRegion,
      List<ServerGroup> enabledServerGroupsInRegion,
      ServerGroup serverGroupToRollBack,
      StageExecution stage,
      StageData stageData,
      String cluster,
      String region) {

    List<ServerGroup> candidates =
        shouldOnlyConsiderEnabledServerGroups(stageData.additionalRollbackContext)
            ? enabledServerGroupsInRegion
            : allServerGroupsInRegion;

    ImageDetails imageDetails =
        previousImageRollbackSupport.getImageDetailsFromEntityTags(
            stageData.cloudProvider, stageData.credentials, region, serverGroupToRollBack.name);

    return getOriginalServerGroup(stage)
        .map(
            serverGroupToRestore ->
                getRollbackDetails(serverGroupToRollBack.name, serverGroupToRestore, imageDetails))
        .orElseGet(
            () ->
                getBestCandidate(cluster, region, serverGroupToRollBack, candidates, imageDetails));
  }

  private RollbackDetails getRollbackDetails(
      String serverGroupToRollBack, String serverGroupToRestore, ImageDetails imageDetails) {

    Map<String, String> context =
        ImmutableMap.<String, String>builder()
            .put("rollbackServerGroupName", serverGroupToRollBack)
            .put("restoreServerGroupName", serverGroupToRestore)
            .build();

    return new RollbackDetails(
        EXPLICIT, context, imageDetails.getImageName(), imageDetails.getBuildNumber());
  }

  /** Return the name of the original server group we should roll back to */
  private Optional<String> getOriginalServerGroup(StageExecution stage) {
    Object originalServerGroup = stage.getContext().get("originalServerGroup");

    return originalServerGroup instanceof String
        ? Optional.of(originalServerGroup.toString())
        : Optional.empty();
  }

  private ServerGroup getServerGroupToRollBack(
      @Nonnull List<ServerGroup> enabledServerGroupsInRegion) {
    return enabledServerGroupsInRegion.get(0);
  }

  /** Retrieve the details for the best rollback candidate */
  @Nonnull
  private RollbackDetails getBestCandidate(
      String cluster,
      String region,
      ServerGroup serverGroupToRollBack,
      List<ServerGroup> candidates,
      ImageDetails imageDetails) {
    return Optional.ofNullable(imageDetails)
        .map(
            imgDetails ->
                getDetailsUsingEntityTags(
                    candidates, serverGroupToRollBack, imgDetails, cluster, region))
        .orElseGet(
            () ->
                getDetailsUsingPreviousServerGroups(
                    candidates, serverGroupToRollBack, cluster, region));
  }

  @Nonnull
  private TaskResult buildResult(
      List<Map> imagesToRestore,
      Map<String, String> rollbackTypes,
      Map<String, Map> rollbackContexts) {
    return TaskResult.builder(ExecutionStatus.SUCCEEDED)
        .context(Collections.singletonMap("imagesToRestore", imagesToRestore))
        .outputs(
            ImmutableMap.<String, Object>builder()
                .put("rollbackTypes", rollbackTypes)
                .put("rollbackContexts", rollbackContexts)
                .build())
        .build();
  }

  @Nullable
  private List<ServerGroup> getServerGroups(
      Moniker moniker, String credentials, String cloudProvider) {
    return Optional.ofNullable(fetchClusterInfoWithRetry(moniker, credentials, cloudProvider))
        .map(Cluster::getServerGroups)
        .map(
            serverGroups ->
                // The list is sorted by creation time, newest first
                serverGroups.stream()
                    .sorted(Comparator.comparing((ServerGroup o) -> o.createdTime).reversed())
                    .collect(Collectors.toList()))
        .orElse(null);
  }

  /** Verify that a rollback is actually possible */
  private boolean isRollbackPossible(
      List<ServerGroup> allServerGroupsInRegion, String cluster, String region) {

    // need at least one server group to rollback from, and one to rollback to!
    if (allServerGroupsInRegion.size() < 2) {
      logger.warn(
          "Not enough server groups in cluster {} and region {} to perform a rollback. Skipping this region.",
          cluster,
          region);
      return false;
    }

    // Check if there's least one enabled
    boolean atLeastOneEnabled =
        allServerGroupsInRegion.stream()
            .anyMatch(DetermineRollbackCandidatesTask::isServerGroupEnabled);
    if (!atLeastOneEnabled) {
      logger.warn(
          "No enabled server groups in cluster {} and region {} to rollback from. Skipping this region.",
          cluster,
          region);
    }

    return atLeastOneEnabled;
  }

  private static boolean isServerGroupEnabled(ServerGroup serverGroup) {
    return serverGroup.disabled == null || !serverGroup.disabled;
  }

  private ImmutableMap<Object, Object> getImageToRestore(
      String region, RollbackDetails rollbackDetails) {
    ImmutableMap.Builder<Object, Object> imageToRestore =
        ImmutableMap.builder()
            .put("region", region)
            .put("image", rollbackDetails.imageName)
            .put("rollbackMethod", rollbackDetails.rollbackType.toString());

    if (rollbackDetails.buildNumber != null) {
      imageToRestore.put("buildNumber", rollbackDetails.buildNumber);
    }

    return imageToRestore.build();
  }

  @Nonnull
  private Map<String, Object> getRollbackContext(
      @Nullable Integer targetHealthyRollbackPercentage,
      ServerGroup serverGroupToRollBack,
      RollbackDetails rollbackDetails) {
    Map<String, Object> rollbackContext = new HashMap<>(rollbackDetails.rollbackContext);
    rollbackContext.put(
        "targetHealthyRollbackPercentage",
        determineTargetHealthyRollbackPercentage(
            serverGroupToRollBack.capacity, targetHealthyRollbackPercentage));
    return rollbackContext;
  }

  @Nonnull
  private RollbackDetails getDetailsUsingPreviousServerGroups(
      List<ServerGroup> candidateServerGroupsInRegion,
      ServerGroup serverGroupToRollBack,
      String cluster,
      String region) {

    logger.info(
        "Looking for rollback candidates in cluster {}, region {} based on previous server groups. ",
        cluster,
        region);
    return fetchRollbackDetails(serverGroupToRollBack, candidateServerGroupsInRegion);
  }

  /** Check for rollback candidates based on entity tags */
  @Nonnull
  private RollbackDetails getDetailsUsingEntityTags(
      List<ServerGroup> candidateServerGroupsInRegion,
      ServerGroup serverGroupToRollBack,
      ImageDetails imageDetails,
      String cluster,
      String region) {

    logger.info(
        "Looking for rollback candidates in cluster {}, region {} based on entity tags. ",
        cluster,
        region);

    return fetchRollbackDetails(imageDetails, serverGroupToRollBack, candidateServerGroupsInRegion);
  }

  private boolean shouldOnlyConsiderEnabledServerGroups(
      Map<String, Object> additionalRollbackContext) {
    return Optional.ofNullable(additionalRollbackContext)
        .map(a -> (Boolean) a.get("onlyEnabledServerGroups"))
        .orElse(false);
  }

  /** Retrieve info about the server group and use it to populate a Moniker object */
  @Nullable
  private Moniker populateMonikerWithServerGroupInfo(
      Moniker moniker, String credentials, String region, String serverGroupName) {
    if (moniker == null && serverGroupName != null) {
      try {
        ServerGroup serverGroup =
            retrySupport.retry(
                () -> cloudDriverService.getServerGroup(credentials, region, serverGroupName),
                5,
                1000,
                false);

        return serverGroup.getMoniker();
      } catch (Exception e) {
        logger.warn(
            "Failed to fetch server group, retrying! (account: {}, region: {}, serverGroup: {})",
            credentials,
            region,
            serverGroupName,
            e);
        return null;
      }
    }
    return moniker;
  }

  /** Get info about cluster */
  @Nullable
  private Cluster fetchClusterInfoWithRetry(
      Moniker moniker, String credentials, String cloudProvider) {
    try {
      return retrySupport.retry(
          () ->
              cloudDriverService.getCluster(
                  moniker.getApp(), credentials, moniker.getCluster(), cloudProvider),
          5,
          1000,
          false);
    } catch (Exception e) {
      logger.warn(
          "Failed to fetch cluster, retrying! (application: {}, account: {}, cluster: {}, cloudProvider: {})",
          moniker.getApp(),
          credentials,
          moniker.getCluster(),
          cloudProvider,
          e);
      return null;
    }
  }

  private static RollbackDetails fetchRollbackDetails(
      ImageDetails imageDetails,
      ServerGroup serverGroupToRollBack,
      List<ServerGroup> serverGroupsInRegion) {
    ServerGroup previousServerGroupWithImage =
        serverGroupsInRegion.stream()
            .filter(exclude(serverGroupToRollBack))
            .filter(s -> s.image != null && s.image.imageId != null)
            .filter(s -> imageDetails.getImageId().equalsIgnoreCase(s.image.imageId))
            .findFirst()
            .orElse(null);

    RollbackDetails rollbackDetails =
        new RollbackDetails(imageDetails.getImageName(), imageDetails.getBuildNumber());

    if (previousServerGroupWithImage != null) {
      // we already have a server group with the desired image
      rollbackDetails.rollbackType = EXPLICIT;
      rollbackDetails.rollbackContext =
          ImmutableMap.<String, String>builder()
              .put("rollbackServerGroupName", serverGroupToRollBack.name)
              .put("restoreServerGroupName", previousServerGroupWithImage.name)
              .build();
      return rollbackDetails;
    }

    rollbackDetails.rollbackType = PREVIOUS_IMAGE;
    rollbackDetails.rollbackContext =
        ImmutableMap.<String, String>builder()
            .put("rollbackServerGroupName", serverGroupToRollBack.name)
            .put("imageId", imageDetails.getImageId())
            .put("imageName", imageDetails.getImageName())
            .build();
    return rollbackDetails;
  }

  private static RollbackDetails fetchRollbackDetails(
      ServerGroup serverGroupToRollBack, List<ServerGroup> serverGroupsInRegion) {

    ServerGroup previousServerGroupInRegion =
        serverGroupsInRegion.stream()
            .filter(exclude(serverGroupToRollBack))
            .findFirst()
            .orElse(null);

    if (previousServerGroupInRegion == null) {
      // this should never happen in reality!
      throw new IllegalStateException(
          format(
              "Could not find a server group to roll back to! (serverGroupName: %s)",
              serverGroupToRollBack.name));
    }

    return new RollbackDetails(
        EXPLICIT,
        ImmutableMap.<String, String>builder()
            .put("rollbackServerGroupName", serverGroupToRollBack.name)
            .put("restoreServerGroupName", previousServerGroupInRegion.name)
            .build(),
        previousServerGroupInRegion.getImageName(),
        previousServerGroupInRegion.getBuildNumber());
  }

  private static Integer determineTargetHealthyRollbackPercentage(
      Capacity currentCapacity, Integer targetHealthyRollbackPercentageOverride) {
    if (targetHealthyRollbackPercentageOverride != null) {
      return targetHealthyRollbackPercentageOverride;
    }

    if (currentCapacity == null || currentCapacity.desired == null) {
      return 100;
    }

    /*
     * This logic is equivalent to what `deck` has implemented around manual rollbacks.
     *
     * https://github.com/spinnaker/deck/blob/master/app/scripts/modules/amazon/src/serverGroup/details/rollback/rollbackServerGroup.controller.js#L44
     */
    if (currentCapacity.desired < 10) {
      return 100;
    } else if (currentCapacity.desired < 20) {
      // accept 1 instance in an unknown state during rollback
      return 90;
    }

    return 95;
  }

  /** Helper function useful for filtering streams */
  private static Predicate<ServerGroup> exclude(ServerGroup group) {
    return s -> !s.getName().equalsIgnoreCase(group.getName());
  }

  private static class StageData {
    public String credentials;
    public String cloudProvider;
    public String serverGroup;

    public Integer targetHealthyRollbackPercentage;

    public Moniker moniker;
    public List<String> regions;
    public Map<String, Object> additionalRollbackContext;
  }
}
