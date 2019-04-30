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

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableMap;
import com.netflix.spinnaker.kork.core.RetrySupport;
import com.netflix.spinnaker.moniker.Moniker;
import com.netflix.spinnaker.orca.ExecutionStatus;
import com.netflix.spinnaker.orca.RetryableTask;
import com.netflix.spinnaker.orca.TaskResult;
import com.netflix.spinnaker.orca.clouddriver.FeaturesService;
import com.netflix.spinnaker.orca.clouddriver.OortService;
import com.netflix.spinnaker.orca.clouddriver.pipeline.servergroup.RollbackServerGroupStage;
import com.netflix.spinnaker.orca.clouddriver.pipeline.servergroup.rollback.PreviousImageRollbackSupport;
import com.netflix.spinnaker.orca.clouddriver.tasks.AbstractCloudProviderAwareTask;
import com.netflix.spinnaker.orca.pipeline.model.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import retrofit.client.Response;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import static com.netflix.spinnaker.orca.clouddriver.pipeline.servergroup.rollback.PreviousImageRollbackSupport.*;
import static java.lang.String.format;
import static com.netflix.spinnaker.orca.clouddriver.pipeline.servergroup.RollbackServerGroupStage.RollbackType.*;

/**
 * The {@code DetermineRollbackCandidatesTask} task determines how one or more regions of a cluster should be
 * rolled back.
 *
 * The determination is based on inspecting the most recently deployed (and enabled!) server group in each region.
 *
 * If this server group has the `spinnaker:metadata` entity tag:
 * - rollback to a previous server group (if exists!) with the `spinnaker:metadata` image id
 * - if no such server group exists, clone forward with the `spinnaker:metadata` image id
 *
 * If this server group does _not_ have a `spinnaker:metadata` entity tag:
 * - rollback to the previous server group (if exists!)
 */
@Component
public class DetermineRollbackCandidatesTask extends AbstractCloudProviderAwareTask implements RetryableTask {
  private static final Logger logger = LoggerFactory.getLogger(DetermineRollbackCandidatesTask.class);

  private final ObjectMapper objectMapper;
  private final RetrySupport retrySupport;
  private final OortService oortService;
  private final FeaturesService featuresService;
  private final PreviousImageRollbackSupport previousImageRollbackSupport;

  @Autowired
  public DetermineRollbackCandidatesTask(ObjectMapper objectMapper,
                                         RetrySupport retrySupport,
                                         OortService oortService,
                                         FeaturesService featuresService) {
    this.objectMapper = objectMapper;
    this.retrySupport = retrySupport;
    this.oortService = oortService;
    this.featuresService = featuresService;

    this.previousImageRollbackSupport = new PreviousImageRollbackSupport(
      objectMapper, oortService, featuresService, retrySupport
    );
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

    AtomicReference<Moniker> moniker = new AtomicReference<>(stageData.moniker);
    if (moniker.get() == null && stageData.serverGroup != null) {
      try {
        Map<String, Object> serverGroup = retrySupport.retry(() -> fetchServerGroup(
          stageData.credentials,
          stageData.regions.get(0),
          stageData.serverGroup
        ), 5, 1000, false);

        moniker.set(objectMapper.convertValue(serverGroup.get("moniker"), Moniker.class));
      } catch (Exception e) {
        logger.warn(
          "Failed to fetch server group, retrying! (account: {}, region: {}, serverGroup: {})",
          stageData.credentials,
          stageData.regions.get(0),
          stageData.serverGroup,
          e
        );
        return TaskResult.RUNNING;
      }
    }

    try {
      cluster = retrySupport.retry(() -> fetchCluster(
        moniker.get().getApp(),
        stageData.credentials,
        moniker.get().getCluster(),
        stageData.cloudProvider
      ), 5, 1000, false);
    } catch (Exception e) {
      logger.warn(
        "Failed to fetch cluster, retrying! (application: {}, account: {}, cluster: {}, cloudProvider: {})",
        moniker.get().getApp(),
        stageData.credentials,
        moniker.get().getCluster(),
        stageData.cloudProvider,
        e
      );
      return TaskResult.RUNNING;
    }

    List<ServerGroup> serverGroups = objectMapper.convertValue(
      cluster.get("serverGroups"),
      new TypeReference<List<ServerGroup>>() {
      }
    );
    serverGroups.sort(Comparator.comparing((ServerGroup o) -> o.createdTime).reversed());

    List<Map> imagesToRestore = new ArrayList<>();
    for (String region : stageData.regions) {
      List<ServerGroup> serverGroupsInRegion = serverGroups
        .stream()
        .filter(s -> region.equalsIgnoreCase(s.region))
        .collect(Collectors.toList());

      if (serverGroupsInRegion.isEmpty()) {
        // no server groups in region, nothing to rollback!
        continue;
      }

      ServerGroup newestEnabledServerGroupInRegion = serverGroupsInRegion
        .stream()
        .filter(s -> s.disabled == null || !s.disabled)
        .findFirst()
        .orElse(null);

      if (newestEnabledServerGroupInRegion == null) {
        // no enabled server groups in this region, nothing to rollback!
        continue;
      }

      ImageDetails imageDetails = previousImageRollbackSupport.getImageDetailsFromEntityTags(
        stageData.cloudProvider,
        stageData.credentials,
        region,
        newestEnabledServerGroupInRegion.name
      );

      RollbackDetails rollbackDetails = null;
      if (imageDetails != null) {
        // check for rollback candidates based on entity tags
        rollbackDetails = fetchRollbackDetails(
          imageDetails,
          newestEnabledServerGroupInRegion,
          serverGroupsInRegion
        );
      }

      if (rollbackDetails == null) {
        // check for rollback candidates based on previous server groups
        rollbackDetails = fetchRollbackDetails(newestEnabledServerGroupInRegion, serverGroupsInRegion);
      }

      if (rollbackDetails != null) {
        Map<String, Object> rollbackContext = new HashMap<>(rollbackDetails.rollbackContext);
        rollbackContext.put(
          "targetHealthyRollbackPercentage",
          determineTargetHealthyRollbackPercentage(
            newestEnabledServerGroupInRegion.capacity,
            stageData.targetHealthyRollbackPercentage
          )
        );

        rollbackTypes.put(region, rollbackDetails.rollbackType.toString());
        rollbackContexts.put(region, rollbackContext);

        ImmutableMap.Builder<Object, Object> imageToRestore = ImmutableMap.builder()
          .put("region", region)
          .put("image", rollbackDetails.imageName)
          .put("rollbackMethod", rollbackDetails.rollbackType.toString());

        if (rollbackDetails.buildNumber != null) {
          imageToRestore.put("buildNumber", rollbackDetails.buildNumber);
        }

        imagesToRestore.add(imageToRestore.build());
      }
    }

    return TaskResult.builder(ExecutionStatus.SUCCEEDED).context(Collections.singletonMap("imagesToRestore", imagesToRestore)).outputs(ImmutableMap.<String, Object>builder()
        .put("rollbackTypes", rollbackTypes)
        .put("rollbackContexts", rollbackContexts)
        .build()).build();
  }

  private Map<String, Object> fetchCluster(String application,
                                           String credentials,
                                           String cluster,
                                           String cloudProvider) {
    try {
      Response response = oortService.getCluster(application, credentials, cluster, cloudProvider);
      return (Map<String, Object>) objectMapper.readValue(response.getBody().in(), Map.class);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  private Map<String, Object> fetchServerGroup(String account,
                                               String region,
                                               String serverGroup) {
    try {
      Response response = oortService.getServerGroup(account, region, serverGroup);
      return (Map<String, Object>) objectMapper.readValue(response.getBody().in(), Map.class);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  private static RollbackDetails fetchRollbackDetails(ImageDetails imageDetails,
                                                      ServerGroup newestEnabledServerGroupInRegion,
                                                      List<ServerGroup> serverGroupsInRegion) {
    ServerGroup previousServerGroupWithImage = serverGroupsInRegion
      .stream()
      .filter(s -> !(s.name.equalsIgnoreCase(newestEnabledServerGroupInRegion.name)))
      .filter(s -> s.image != null && s.image.imageId != null)
      .filter(s -> imageDetails.getImageId().equalsIgnoreCase(s.image.imageId))
      .findFirst()
      .orElse(null);

    RollbackDetails rollbackDetails = new RollbackDetails(imageDetails.getImageName(), imageDetails.getBuildNumber());

    if (previousServerGroupWithImage != null) {
      // we already have a server group with the desired image
      rollbackDetails.rollbackType = EXPLICIT;
      rollbackDetails.rollbackContext = ImmutableMap.<String, String>builder()
        .put("rollbackServerGroupName", newestEnabledServerGroupInRegion.name)
        .put("restoreServerGroupName", previousServerGroupWithImage.name)
        .build();
      return rollbackDetails;
    }

    rollbackDetails.rollbackType = PREVIOUS_IMAGE;
    rollbackDetails.rollbackContext = ImmutableMap.<String, String>builder()
      .put("rollbackServerGroupName", newestEnabledServerGroupInRegion.name)
      .put("imageId", imageDetails.getImageId())
      .put("imageName", imageDetails.getImageName())
      .build();
    return rollbackDetails;
  }

  private static RollbackDetails fetchRollbackDetails(ServerGroup newestEnabledServerGroupInRegion,
                                                      List<ServerGroup> serverGroupsInRegion) {
    if (serverGroupsInRegion.size() < 2 || newestEnabledServerGroupInRegion == null) {
      // less than 2 server groups or no enabled server group, nothing to rollback!
      return null;
    }

    ServerGroup previousServerGroupInRegion = serverGroupsInRegion
      .stream()
      .filter(s -> !(s.name.equalsIgnoreCase(newestEnabledServerGroupInRegion.name)))
      .findFirst()
      .orElse(null);

    if (previousServerGroupInRegion == null) {
      // this should never happen in reality!
      throw new IllegalStateException(
        format(
          "Found more than one server group with the same name! (serverGroupName: %s)",
          newestEnabledServerGroupInRegion.name
        )
      );
    }

    return new RollbackDetails(
      EXPLICIT,
      ImmutableMap.<String, String>builder()
        .put("rollbackServerGroupName", newestEnabledServerGroupInRegion.name)
        .put("restoreServerGroupName", previousServerGroupInRegion.name)
        .build(),
      previousServerGroupInRegion.getImageName(),
      previousServerGroupInRegion.getBuildNumber()
    );
  }

  private static Integer determineTargetHealthyRollbackPercentage(Capacity currentCapacity,
                                                                  Integer targetHealthyRollbackPercentageOverride) {
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

  private static class StageData {
    public String credentials;
    public String cloudProvider;
    public String serverGroup;

    public Integer targetHealthyRollbackPercentage;

    public Moniker moniker;
    public List<String> regions;
  }

  private static class ServerGroup {
    public String name;
    public String region;
    public Long createdTime;
    public Boolean disabled;

    public Capacity capacity;
    public Image image;
    public BuildInfo buildInfo;

    public String getImageName() {
      return (image != null && image.name != null) ? image.name : null;
    }

    public String getBuildNumber() {
      return (buildInfo != null && buildInfo.jenkins != null) ? buildInfo.jenkins.number : null;
    }
  }

  public static class Capacity {
    public Integer min;
    public Integer max;
    public Integer desired;
  }

  private static class Image {
    public String imageId;
    public String name;
  }

  private static class BuildInfo {
    public Jenkins jenkins;

    private static class Jenkins {
      public String number;
    }
  }

  private static class RollbackDetails {
    RollbackServerGroupStage.RollbackType rollbackType;
    Map<String, String> rollbackContext;

    String imageName;
    String buildNumber;

    RollbackDetails(RollbackServerGroupStage.RollbackType rollbackType,
                    Map<String, String> rollbackContext,
                    String imageName,
                    String buildNumber) {
      this.rollbackType = rollbackType;
      this.rollbackContext = rollbackContext;
      this.imageName = imageName;
      this.buildNumber = buildNumber;
    }

    RollbackDetails(String imageName,
                    String buildNumber) {
      this.imageName = imageName;
      this.buildNumber = buildNumber;
    }
  }
}
