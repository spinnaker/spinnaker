/*
 * Copyright 2018 Netflix, Inc.
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

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableMap;
import com.netflix.spectator.api.Counter;
import com.netflix.spectator.api.Registry;
import com.netflix.spinnaker.kork.core.RetrySupport;
import com.netflix.spinnaker.orca.clouddriver.OortService;
import com.netflix.spinnaker.orca.notifications.AbstractPollingNotificationAgent;
import com.netflix.spinnaker.orca.notifications.NotificationClusterLock;
import com.netflix.spinnaker.orca.pipeline.ExecutionLauncher;
import com.netflix.spinnaker.orca.pipeline.model.Execution;
import com.netflix.spinnaker.security.AuthenticatedRequest;
import com.netflix.spinnaker.security.User;
import groovy.util.logging.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Component;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static com.netflix.spinnaker.orca.clouddriver.tasks.servergroup.EphemeralServerGroupEntityTagGenerator.TTL_TAG;
import static java.lang.String.format;

@Slf4j
@Component
@ConditionalOnExpression(value = "${pollers.ephemeralServerGroups.enabled:false}")
public class EphemeralServerGroupsPoller extends AbstractPollingNotificationAgent {
  private static final Logger log = LoggerFactory.getLogger(EphemeralServerGroupsPoller.class);

  private final ObjectMapper objectMapper;
  private final OortService oortService;
  private final RetrySupport retrySupport;
  private final ExecutionLauncher executionLauncher;

  private final PollerSupport pollerSupport;

  private final Counter errorsCounter;
  private final Counter triggeredCounter;

  @Autowired
  public EphemeralServerGroupsPoller(NotificationClusterLock notificationClusterLock,
                                     ObjectMapper objectMapper,
                                     OortService oortService,
                                     RetrySupport retrySupport,
                                     Registry registry,
                                     ExecutionLauncher executionLauncher) {
    super(notificationClusterLock);

    this.objectMapper = objectMapper;
    this.oortService = oortService;
    this.retrySupport = retrySupport;
    this.executionLauncher = executionLauncher;

    this.pollerSupport = new PollerSupport(objectMapper, retrySupport, oortService);

    this.errorsCounter = registry.counter("poller.ephemeralServerGroups.errors");
    this.triggeredCounter = registry.counter("poller.ephemeralServerGroups.triggered");
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
    return "ephemeralServerGroups";
  }

  @Override
  protected void tick() {
    log.info("Checking for ephemeral server groups");

    List<EphemeralServerGroupTag> ephemeralServerGroupTags = fetchEphemeralServerGroupTags();
    log.info("Found {} ephemeral server groups", ephemeralServerGroupTags.size());

    if (ephemeralServerGroupTags.isEmpty()) {
      return;
    }

    for (EphemeralServerGroupTag ephemeralServerGroupTag : ephemeralServerGroupTags) {
      try {
        List<Map<String, Object>> jobs = new ArrayList<>();

        Optional<ServerGroup> serverGroup = pollerSupport.fetchServerGroup(
          ephemeralServerGroupTag.account,
          ephemeralServerGroupTag.location,
          ephemeralServerGroupTag.serverGroup
        );

        if (serverGroup.isPresent()) {
          // server group exists -- destroy the server group (will also remove all associated tags)
          jobs.add(buildDestroyServerGroupOperation(ephemeralServerGroupTag));
        } else {
          // server group no longer exists -- remove the tags
          jobs.add(buildDeleteEntityTagsOperation(ephemeralServerGroupTag));
        }

        Map<String, Object> cleanupOperation = buildCleanupOperation(ephemeralServerGroupTag, jobs);
        log.info((String) cleanupOperation.get("name"));

        User systemUser = new User();
        systemUser.setUsername("spinnaker");
        systemUser.setAllowedAccounts(Collections.singletonList(ephemeralServerGroupTag.account));

        AuthenticatedRequest.propagate(() -> executionLauncher.start(
          Execution.ExecutionType.ORCHESTRATION,
          objectMapper.writeValueAsString(cleanupOperation)
        ), systemUser).call();

        triggeredCounter.increment();
      } catch (Exception e) {
        log.error("Failed to destroy ephemeral server group (id: {})", ephemeralServerGroupTag.id, e);
        errorsCounter.increment();
      }
    }
  }

  private List<EphemeralServerGroupTag> fetchEphemeralServerGroupTags() {
    List<EntityTags> allEntityTags = retrySupport.retry(() -> objectMapper.convertValue(
      oortService.getEntityTags(ImmutableMap.<String, String>builder()
        .put("tag:" + TTL_TAG, "*")
        .put("entityType", "servergroup")
        .build()
      ),
      new TypeReference<List<EntityTags>>() {
      }
    ), 15, 2000, false);

    return allEntityTags.stream()
      .map(e -> e.tags.stream()
        .filter(t -> TTL_TAG.equalsIgnoreCase(t.name))
        .map(t -> {
          EphemeralServerGroupTag ephemeralServerGroupTag = objectMapper.convertValue(t.value, EphemeralServerGroupTag.class);
          ephemeralServerGroupTag.id = e.id;
          ephemeralServerGroupTag.account = e.entityRef.account;
          ephemeralServerGroupTag.location = e.entityRef.region;
          ephemeralServerGroupTag.application = e.entityRef.application;
          ephemeralServerGroupTag.serverGroup = e.entityRef.entityId;
          ephemeralServerGroupTag.cloudProvider = e.entityRef.cloudProvider;

          return ephemeralServerGroupTag;
        })
        .findFirst()
        .orElse(null)
      )
      .filter(Objects::nonNull)
      .filter(t -> t.expiry.isBefore(ZonedDateTime.now(ZoneOffset.UTC)))
      .collect(Collectors.toList());
  }

  private Map<String, Object> buildCleanupOperation(EphemeralServerGroupTag ephemeralServerGroupTag,
                                                    List<Map<String, Object>> jobs) {
    return ImmutableMap.<String, Object>builder()
      .put("application", ephemeralServerGroupTag.application)
      .put(
        "name",
        format(
          "Destroy Ephemeral Server Group: %s",
          ephemeralServerGroupTag.serverGroup
        )
      )
      .put("user", "spinnaker")
      .put("stages", jobs)
      .build();
  }

  private Map<String, Object> buildDeleteEntityTagsOperation(EphemeralServerGroupTag ephemeralServerGroupTag) {
    Map<String, Object> operation = new HashMap<>();

    operation.put("application", ephemeralServerGroupTag.application);
    operation.put("type", "deleteEntityTags");
    operation.put("id", ephemeralServerGroupTag.id);
    operation.put("tags", Collections.singletonList(TTL_TAG));

    return operation;
  }

  private Map<String, Object> buildDestroyServerGroupOperation(EphemeralServerGroupTag ephemeralServerGroupTag) {
    Map<String, Object> operation = new HashMap<>();

    operation.put("type", "destroyServerGroup");
    operation.put("asgName", ephemeralServerGroupTag.serverGroup);
    operation.put("serverGroupName", ephemeralServerGroupTag.serverGroup);
    operation.put("region", ephemeralServerGroupTag.location);
    operation.put("credentials", ephemeralServerGroupTag.account);
    operation.put("cloudProvider", ephemeralServerGroupTag.cloudProvider);

    return operation;
  }

  private static class EphemeralServerGroupTag {
    public String id;

    public String cloudProvider;
    public String application;
    public String account;
    public String location;
    public String serverGroup;

    public ZonedDateTime expiry;
  }
}
