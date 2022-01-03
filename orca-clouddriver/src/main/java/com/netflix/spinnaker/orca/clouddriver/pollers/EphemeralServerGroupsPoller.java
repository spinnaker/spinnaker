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

import static com.netflix.spinnaker.orca.clouddriver.tasks.servergroup.EphemeralServerGroupEntityTagGenerator.TTL_TAG;
import static java.lang.String.format;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableMap;
import com.netflix.spectator.api.Counter;
import com.netflix.spectator.api.Id;
import com.netflix.spectator.api.Registry;
import com.netflix.spinnaker.kork.core.RetrySupport;
import com.netflix.spinnaker.kork.exceptions.SystemException;
import com.netflix.spinnaker.orca.api.pipeline.models.ExecutionType;
import com.netflix.spinnaker.orca.clouddriver.CloudDriverService;
import com.netflix.spinnaker.orca.clouddriver.config.PollerConfigurationProperties;
import com.netflix.spinnaker.orca.clouddriver.model.EntityTags;
import com.netflix.spinnaker.orca.clouddriver.model.ServerGroup;
import com.netflix.spinnaker.orca.front50.Front50Service;
import com.netflix.spinnaker.orca.front50.model.Application;
import com.netflix.spinnaker.orca.notifications.AbstractPollingNotificationAgent;
import com.netflix.spinnaker.orca.notifications.NotificationClusterLock;
import com.netflix.spinnaker.orca.pipeline.ExecutionLauncher;
import com.netflix.spinnaker.security.AuthenticatedRequest;
import groovy.util.logging.Slf4j;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import retrofit.RetrofitError;

@Slf4j
@Component
@ConditionalOnExpression(value = "${pollers.ephemeral-server-groups.enabled:false}")
public class EphemeralServerGroupsPoller extends AbstractPollingNotificationAgent {
  private static final Logger log = LoggerFactory.getLogger(EphemeralServerGroupsPoller.class);

  private final ObjectMapper objectMapper;
  private final CloudDriverService cloudDriverService;
  private final RetrySupport retrySupport;
  private final Registry registry;
  private final ExecutionLauncher executionLauncher;
  private final Front50Service front50Service;
  private final PollerConfigurationProperties pollerConfigurationProperties;

  private final PollerSupport pollerSupport;

  private final Counter errorsCounter;
  private final Id triggeredCounterId;

  @Autowired
  public EphemeralServerGroupsPoller(
      NotificationClusterLock notificationClusterLock,
      ObjectMapper objectMapper,
      CloudDriverService cloudDriverService,
      RetrySupport retrySupport,
      Registry registry,
      ExecutionLauncher executionLauncher,
      Front50Service front50Service,
      PollerConfigurationProperties pollerConfigurationProperties) {
    super(notificationClusterLock);

    this.objectMapper = objectMapper;
    this.cloudDriverService = cloudDriverService;
    this.retrySupport = retrySupport;
    this.registry = registry;
    this.executionLauncher = executionLauncher;
    this.front50Service = front50Service;
    this.pollerConfigurationProperties = pollerConfigurationProperties;

    this.pollerSupport = new PollerSupport(retrySupport, cloudDriverService);

    this.triggeredCounterId = registry.createId("poller.ephemeralServerGroups.triggered");
    this.errorsCounter = registry.counter("poller.ephemeralServerGroups.errors");
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

    List<EphemeralServerGroupTag> ephemeralServerGroupTags = new ArrayList<>();

    try {
      ephemeralServerGroupTags.addAll(fetchEphemeralServerGroupTags());
      log.info(
          "Found {} ephemeral server groups: {}",
          ephemeralServerGroupTags.size(),
          ephemeralServerGroupTags);
    } catch (Exception e) {
      log.error("Unable to fetch ephemeral server groups", e);
      errorsCounter.increment();
    }

    if (ephemeralServerGroupTags.isEmpty()) {
      return;
    }

    for (EphemeralServerGroupTag ephemeralServerGroupTag : ephemeralServerGroupTags) {
      try {
        List<Map<String, Object>> jobs = new ArrayList<>();

        Optional<ServerGroup> serverGroup =
            pollerSupport.fetchServerGroup(
                ephemeralServerGroupTag.account,
                ephemeralServerGroupTag.location,
                ephemeralServerGroupTag.serverGroup);

        if (serverGroup.isPresent()) {
          // server group exists -- destroy the server group (will also remove all associated tags)
          jobs.add(buildDestroyServerGroupOperation(ephemeralServerGroupTag));
        } else {
          // server group no longer exists -- remove the tags
          jobs.add(buildDeleteEntityTagsOperation(ephemeralServerGroupTag));
        }

        Map<String, Object> cleanupOperation = buildCleanupOperation(ephemeralServerGroupTag, jobs);
        log.info((String) cleanupOperation.get("name"));

        String taskUser =
            Optional.ofNullable(
                    pollerConfigurationProperties.getEphemeralServerGroupsPoller().getUsername())
                .orElseGet(
                    () ->
                        getApplication(ephemeralServerGroupTag.application)
                            .map(it -> it.email)
                            .orElseGet(
                                () -> {
                                  log.warn(
                                      "Failed to find application owner for server group '{}', "
                                          + "will use static 'spinnaker' user as fallback.",
                                      ephemeralServerGroupTag.serverGroup);
                                  return "spinnaker";
                                }));

        AuthenticatedRequest.runAs(
                taskUser,
                Collections.singletonList(ephemeralServerGroupTag.account),
                () -> executionLauncher.start(ExecutionType.ORCHESTRATION, cleanupOperation))
            .call();

        // if a server group still exists >= 30 minutes past it's TTL, flag it as stale.
        boolean isStale =
            ephemeralServerGroupTag.expiry.isBefore(
                ZonedDateTime.now(ZoneOffset.UTC).minusMinutes(30));
        if (isStale) {
          log.warn(
              "Ephemeral server group appears stale (id: {}, expiry: {})",
              ephemeralServerGroupTag.id,
              ephemeralServerGroupTag.expiry);
        }

        registry.counter(triggeredCounterId.withTag("stale", isStale)).increment();
      } catch (Exception e) {
        log.error(
            "Failed to destroy ephemeral server group (id: {})", ephemeralServerGroupTag.id, e);
        errorsCounter.increment();
      }
    }
  }

  private List<EphemeralServerGroupTag> fetchEphemeralServerGroupTags() {
    try {
      List<EntityTags> allEntityTags =
          AuthenticatedRequest.allowAnonymous(
              () ->
                  retrySupport.retry(
                      () ->
                          cloudDriverService.getEntityTags(
                              Map.of("tag:" + TTL_TAG, "*", "entityType", "servergroup")),
                      15,
                      2000,
                      false));

      return allEntityTags.stream()
          .map(
              e ->
                  e.tags.stream()
                      .filter(t -> TTL_TAG.equalsIgnoreCase(t.name))
                      .map(
                          t -> {
                            EphemeralServerGroupTag ephemeralServerGroupTag =
                                objectMapper.convertValue(t.value, EphemeralServerGroupTag.class);
                            ephemeralServerGroupTag.id = e.id;
                            ephemeralServerGroupTag.account = e.entityRef.account;
                            ephemeralServerGroupTag.location = e.entityRef.region;
                            ephemeralServerGroupTag.application = e.entityRef.application;
                            ephemeralServerGroupTag.serverGroup = e.entityRef.entityId;
                            ephemeralServerGroupTag.cloudProvider = e.entityRef.cloudProvider;

                            return ephemeralServerGroupTag;
                          })
                      .findFirst()
                      .orElse(null))
          .filter(Objects::nonNull)
          .filter(t -> t.expiry.isBefore(ZonedDateTime.now(ZoneOffset.UTC)))
          .collect(Collectors.toList());
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  private Map<String, Object> buildCleanupOperation(
      EphemeralServerGroupTag ephemeralServerGroupTag, List<Map<String, Object>> jobs) {
    return ImmutableMap.<String, Object>builder()
        .put("application", ephemeralServerGroupTag.application)
        .put(
            "name",
            format("Destroy Ephemeral Server Group: %s", ephemeralServerGroupTag.serverGroup))
        .put("user", "spinnaker")
        .put("stages", jobs)
        .build();
  }

  private Map<String, Object> buildDeleteEntityTagsOperation(
      EphemeralServerGroupTag ephemeralServerGroupTag) {
    Map<String, Object> operation = new HashMap<>();

    operation.put("application", ephemeralServerGroupTag.application);
    operation.put("type", "deleteEntityTags");
    operation.put("id", ephemeralServerGroupTag.id);
    operation.put("tags", Collections.singletonList(TTL_TAG));

    return operation;
  }

  private Map<String, Object> buildDestroyServerGroupOperation(
      EphemeralServerGroupTag ephemeralServerGroupTag) {
    Map<String, Object> operation = new HashMap<>();

    operation.put("type", "destroyServerGroup");
    operation.put("asgName", ephemeralServerGroupTag.serverGroup);
    operation.put("serverGroupName", ephemeralServerGroupTag.serverGroup);
    operation.put("region", ephemeralServerGroupTag.location);
    operation.put("credentials", ephemeralServerGroupTag.account);
    operation.put("cloudProvider", ephemeralServerGroupTag.cloudProvider);

    return operation;
  }

  private Optional<Application> getApplication(String applicationName) {
    try {
      return Optional.of(front50Service.get(applicationName));
    } catch (RetrofitError e) {
      if (e.getResponse().getStatus() == HttpStatus.NOT_FOUND.value()) {
        return Optional.empty();
      }
      throw new SystemException(format("Failed to retrieve application '%s'", applicationName), e);
    }
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
