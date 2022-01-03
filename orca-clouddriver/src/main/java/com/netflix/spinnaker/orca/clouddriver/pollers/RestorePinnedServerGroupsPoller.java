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

package com.netflix.spinnaker.orca.clouddriver.pollers;

import static com.netflix.spinnaker.orca.clouddriver.tasks.servergroup.PinnedServerGroupTagGenerator.PINNED_CAPACITY_TAG;
import static java.lang.String.format;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableMap;
import com.netflix.spectator.api.Counter;
import com.netflix.spectator.api.Registry;
import com.netflix.spinnaker.kork.core.RetrySupport;
import com.netflix.spinnaker.orca.api.pipeline.models.ExecutionType;
import com.netflix.spinnaker.orca.api.pipeline.models.PipelineExecution;
import com.netflix.spinnaker.orca.clouddriver.CloudDriverService;
import com.netflix.spinnaker.orca.clouddriver.model.EntityTags;
import com.netflix.spinnaker.orca.clouddriver.model.ServerGroup;
import com.netflix.spinnaker.orca.notifications.AbstractPollingNotificationAgent;
import com.netflix.spinnaker.orca.notifications.NotificationClusterLock;
import com.netflix.spinnaker.orca.pipeline.ExecutionLauncher;
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionNotFoundException;
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository;
import com.netflix.spinnaker.security.AuthenticatedRequest;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnExpression(value = "${pollers.restore-pinned-server-groups.enabled:false}")
public class RestorePinnedServerGroupsPoller extends AbstractPollingNotificationAgent {
  private static final Logger log = LoggerFactory.getLogger(RestorePinnedServerGroupsPoller.class);

  private final ObjectMapper objectMapper;
  private final CloudDriverService cloudDriverService;
  private final RetrySupport retrySupport;
  private final ExecutionLauncher executionLauncher;
  private final ExecutionRepository executionRepository;

  private final String username;

  private final PollerSupport pollerSupport;

  private final Counter errorsCounter;
  private final Counter triggeredCounter;

  @Autowired
  public RestorePinnedServerGroupsPoller(
      NotificationClusterLock notificationClusterLock,
      ObjectMapper objectMapper,
      CloudDriverService cloudDriverService,
      RetrySupport retrySupport,
      Registry registry,
      ExecutionLauncher executionLauncher,
      ExecutionRepository executionRepository,
      @Value("${pollers.restore-pinned-server-groups.username:spinnaker}") String username) {
    this(
        notificationClusterLock,
        objectMapper,
        cloudDriverService,
        retrySupport,
        registry,
        executionLauncher,
        executionRepository,
        username,
        new PollerSupport(retrySupport, cloudDriverService));
  }

  @VisibleForTesting
  public RestorePinnedServerGroupsPoller(
      NotificationClusterLock notificationClusterLock,
      ObjectMapper objectMapper,
      CloudDriverService cloudDriverService,
      RetrySupport retrySupport,
      Registry registry,
      ExecutionLauncher executionLauncher,
      ExecutionRepository executionRepository,
      String username,
      PollerSupport pollerSupport) {
    super(notificationClusterLock);

    this.objectMapper = objectMapper;
    this.cloudDriverService = cloudDriverService;
    this.retrySupport = retrySupport;
    this.executionLauncher = executionLauncher;
    this.executionRepository = executionRepository;
    this.username = username;
    this.pollerSupport = pollerSupport;

    this.errorsCounter = registry.counter("poller.restorePinnedServerGroups.errors");
    this.triggeredCounter = registry.counter("poller.restorePinnedServerGroups.triggered");
  }

  @Override
  protected long getPollingInterval() {
    return TimeUnit.MINUTES.toSeconds(10);
  }

  @Override
  protected TimeUnit getPollingIntervalUnit() {
    return TimeUnit.SECONDS;
  }

  @Override
  protected String getNotificationType() {
    return "restorePinnedServerGroups";
  }

  @Override
  protected void tick() {
    log.info("Checking for pinned server groups");

    List<PinnedServerGroupTag> pinnedServerGroupTags = fetchPinnedServerGroupTags();
    log.info("Found {} pinned server groups", pinnedServerGroupTags.size());

    if (pinnedServerGroupTags.isEmpty()) {
      return;
    }

    pinnedServerGroupTags =
        pinnedServerGroupTags.stream()
            .filter(this::hasCompletedExecution)
            .collect(Collectors.toList());

    log.info(
        "Found {} pinned server groups with completed executions", pinnedServerGroupTags.size());

    for (PinnedServerGroupTag pinnedServerGroupTag : pinnedServerGroupTags) {
      try {
        List<Map<String, Object>> jobs = new ArrayList<>();
        jobs.add(buildDeleteEntityTagsOperation(pinnedServerGroupTag));
        var allowedAccount = Collections.singletonList(pinnedServerGroupTag.account);

        Optional<ServerGroup> serverGroup =
            AuthenticatedRequest.runAs(
                    username,
                    allowedAccount,
                    () ->
                        pollerSupport.fetchServerGroup(
                            pinnedServerGroupTag.account,
                            pinnedServerGroupTag.location,
                            pinnedServerGroupTag.serverGroup))
                .call();

        serverGroup.ifPresent(
            s -> {
              if (s.capacity.min.equals(pinnedServerGroupTag.pinnedCapacity.min)) {
                jobs.add(0, buildResizeOperation(pinnedServerGroupTag, s));

                // ensure that the tag cleanup comes after the resize operation has completed
                jobs.get(1)
                    .put(
                        "requisiteStageRefIds",
                        Collections.singletonList(jobs.get(0).get("refId")));
              }
            });

        Map<String, Object> cleanupOperation =
            buildCleanupOperation(pinnedServerGroupTag, serverGroup, jobs);
        log.info((String) cleanupOperation.get("name"));

        AuthenticatedRequest.runAs(
                username,
                allowedAccount,
                () -> executionLauncher.start(ExecutionType.ORCHESTRATION, cleanupOperation))
            .call();

        triggeredCounter.increment();
      } catch (Exception e) {
        log.error(
            "Failed to unpin server group (serverGroup: {})", pinnedServerGroupTag.serverGroup, e);
        errorsCounter.increment();
      }
    }
  }

  public List<PinnedServerGroupTag> fetchPinnedServerGroupTags() {
    List<EntityTags> allEntityTags =
        AuthenticatedRequest.allowAnonymous(
            () ->
                retrySupport.retry(
                    () ->
                        cloudDriverService.getEntityTags(
                            Map.of("tag:" + PINNED_CAPACITY_TAG, "*", "entityType", "servergroup")),
                    15,
                    2000,
                    false));

    return allEntityTags.stream()
        .map(
            e ->
                e.tags.stream()
                    .filter(t -> PINNED_CAPACITY_TAG.equalsIgnoreCase(t.name))
                    .map(
                        t -> {
                          PinnedServerGroupTag pinnedServerGroupTag =
                              objectMapper.convertValue(t.value, PinnedServerGroupTag.class);
                          pinnedServerGroupTag.id = e.id;
                          pinnedServerGroupTag.application = e.entityRef.application;
                          return pinnedServerGroupTag;
                        })
                    .findFirst()
                    .orElse(null))
        .filter(Objects::nonNull)
        .collect(Collectors.toList());
  }

  public boolean hasCompletedExecution(PinnedServerGroupTag pinnedServerGroupTag) {
    try {
      PipelineExecution execution =
          executionRepository.retrieve(
              pinnedServerGroupTag.executionType, pinnedServerGroupTag.executionId);

      return execution.getStatus().isComplete();
    } catch (ExecutionNotFoundException e) {
      return true;
    } catch (Exception e) {
      log.warn(
          "Unable to determine status of execution (executionId: {})",
          pinnedServerGroupTag.executionId,
          e);
      errorsCounter.increment();
      return false;
    }
  }

  private Map<String, Object> buildCleanupOperation(
      PinnedServerGroupTag pinnedServerGroupTag,
      Optional<ServerGroup> serverGroup,
      List<Map<String, Object>> jobs) {
    String name =
        serverGroup
            .map(
                s ->
                    format(
                        "Unpin Server Group: %s (min: %s)",
                        pinnedServerGroupTag.serverGroup,
                        pinnedServerGroupTag.unpinnedCapacity.min))
            .orElseGet(() -> "Deleting tags on '" + pinnedServerGroupTag.id + "'");

    return ImmutableMap.<String, Object>builder()
        .put("application", pinnedServerGroupTag.application)
        .put("name", name)
        .put("user", "spinnaker")
        .put("stages", jobs)
        .build();
  }

  private Map<String, Object> buildDeleteEntityTagsOperation(
      PinnedServerGroupTag pinnedServerGroupTag) {
    Map<String, Object> operation = new HashMap<>();

    operation.put("application", pinnedServerGroupTag.application);
    operation.put("type", "deleteEntityTags");
    operation.put("id", pinnedServerGroupTag.id);
    operation.put("tags", Collections.singletonList(PINNED_CAPACITY_TAG));

    return operation;
  }

  private Map<String, Object> buildResizeOperation(
      PinnedServerGroupTag pinnedServerGroupTag, ServerGroup serverGroup) {
    return ImmutableMap.<String, Object>builder()
        .put("refId", "1")
        .put("asgName", pinnedServerGroupTag.serverGroup)
        .put("serverGroupName", pinnedServerGroupTag.serverGroup)
        .put("type", "resizeServerGroup")
        .put("region", pinnedServerGroupTag.location)
        .put("credentials", pinnedServerGroupTag.account)
        .put("cloudProvider", pinnedServerGroupTag.cloudProvider)
        .put(
            "interestingHealthProviderNames",
            Collections.emptyList()) // no need to wait on health when only adjusting min capacity
        .put(
            "capacity",
            ImmutableMap.<String, Integer>builder()
                .put("min", pinnedServerGroupTag.unpinnedCapacity.min)
                .build())
        .put(
            "constraints",
            Collections.singletonMap(
                "capacity",
                ImmutableMap.<String, Integer>builder()
                    .put(
                        "min",
                        serverGroup
                            .capacity
                            .min) // ensure that the current min capacity has not been already
                    // changed
                    .build()))
        .build();
  }

  public static class PinnedServerGroupTag {
    public String id;

    public String cloudProvider;
    public String application;
    public String account;
    public String location;
    public String serverGroup;

    public ExecutionType executionType;
    public String executionId;
    public String stageId;

    public ServerGroup.Capacity pinnedCapacity;
    public ServerGroup.Capacity unpinnedCapacity;
  }
}
