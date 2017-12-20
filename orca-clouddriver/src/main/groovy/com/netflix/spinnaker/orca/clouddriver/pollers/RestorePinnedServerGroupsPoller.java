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

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableMap;
import com.netflix.spectator.api.Counter;
import com.netflix.spectator.api.Registry;
import com.netflix.spinnaker.kork.core.RetrySupport;
import com.netflix.spinnaker.orca.clouddriver.OortService;
import com.netflix.spinnaker.orca.notifications.AbstractPollingNotificationAgent;
import com.netflix.spinnaker.orca.pipeline.ExecutionLauncher;
import com.netflix.spinnaker.orca.pipeline.model.Execution;
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionNotFoundException;
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository;
import groovy.util.logging.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Component;
import redis.clients.jedis.Jedis;
import redis.clients.util.Pool;
import retrofit.client.Response;
import rx.Observable;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static java.lang.String.format;
import static com.netflix.spinnaker.orca.clouddriver.tasks.servergroup.PinnedServerGroupTagGenerator.PINNED_CAPACITY_TAG;

@Slf4j
@Component
@ConditionalOnExpression(value = "${pollers.restorePinnedServerGroups.enabled:false}")
class RestorePinnedServerGroupsPoller extends AbstractPollingNotificationAgent {
  private static final Logger log = LoggerFactory.getLogger(RestorePinnedServerGroupsPoller.class);

  private final ObjectMapper objectMapper;
  private final OortService oortService;
  private final RetrySupport retrySupport;
  private final ExecutionLauncher executionLauncher;
  private final ExecutionRepository executionRepository;

  private final Counter errorsCounter;
  private final Counter triggeredCounter;

  @Autowired
  public RestorePinnedServerGroupsPoller(@Qualifier("jedisPool") Pool<Jedis> jedisPool,
                                         ObjectMapper objectMapper,
                                         OortService oortService,
                                         RetrySupport retrySupport,
                                         Registry registry,
                                         ExecutionLauncher executionLauncher,
                                         ExecutionRepository executionRepository) {
    super(jedisPool);

    this.objectMapper = objectMapper;
    this.oortService = oortService;
    this.retrySupport = retrySupport;
    this.executionLauncher = executionLauncher;
    this.executionRepository = executionRepository;

    this.errorsCounter = registry.counter("poller.restorePinnedServerGroups.errors");
    this.triggeredCounter = registry.counter("poller.restorePinnedServerGroups.triggered");
  }

  @Override
  protected long getPollingInterval() {
    return TimeUnit.MINUTES.toSeconds(10);
  }

  @Override
  protected String getNotificationType() {
    return "restorePinnedServerGroups";
  }

  @Override
  protected void startPolling() {
    subscription = Observable
      .timer(getPollingInterval(), TimeUnit.SECONDS, scheduler)
      .repeat()
      .filter(interval -> tryAcquireLock())
      .subscribe(interval -> {
        try {
          poll();
        } catch (Exception e) {
          log.error("Error checking for pinned server groups", e);
        }
      });
  }

  void poll() {
    log.info("Checking for pinned server groups");

    List<PinnedServerGroupTag> pinnedServerGroupTags = fetchPinnedServerGroupTags();
    log.info("Found {} pinned server groups", pinnedServerGroupTags.size());

    if (pinnedServerGroupTags.isEmpty()) {
      return;
    }

    pinnedServerGroupTags = pinnedServerGroupTags.stream()
      .filter(this::hasCompletedExecution)
      .collect(Collectors.toList());

    log.info("Found {} pinned server groups with completed executions", pinnedServerGroupTags.size());

    for (PinnedServerGroupTag pinnedServerGroupTag : pinnedServerGroupTags) {
      try {
        ServerGroup serverGroup = fetchServerGroup(
          pinnedServerGroupTag.account,
          pinnedServerGroupTag.location,
          pinnedServerGroupTag.serverGroup
        );

        List<Map<String, Object>> jobs = new ArrayList<>();
        jobs.add(buildDeleteEntityTagsOperation(pinnedServerGroupTag, serverGroup));

        if (serverGroup.capacity.min.equals(pinnedServerGroupTag.pinnedCapacity.min)) {
          jobs.add(0, buildResizeOperation(pinnedServerGroupTag, serverGroup));

          // ensure that the tag cleanup comes after the resize operation has completed
          jobs.get(1).put("requisiteStageRefIds", Collections.singletonList(jobs.get(0).get("refId")));
        }

        Map<String, Object> cleanupOperation = buildCleanupOperation(pinnedServerGroupTag, serverGroup, jobs);
        log.info((String) cleanupOperation.get("name"));

        // TODO-AJ Need to provide an authentication context such that resize operations in privileged accounts work!
        executionLauncher.start(
          Execution.ExecutionType.ORCHESTRATION,
          objectMapper.writeValueAsString(cleanupOperation)
        );

        triggeredCounter.increment();
      } catch (Exception e) {
        log.error("Failed to unpin server group (serverGroup: {})", pinnedServerGroupTag.serverGroup, e);
        errorsCounter.increment();
      }
    }
  }

  List<PinnedServerGroupTag> fetchPinnedServerGroupTags() {
    List<EntityTags> allEntityTags = retrySupport.retry(() -> objectMapper.convertValue(
      oortService.getEntityTags(ImmutableMap.<String, String>builder()
        .put("tag:" + PINNED_CAPACITY_TAG, "*")
        .put("entityType", "servergroup")
        .build()
      ),
      new TypeReference<List<EntityTags>>() {
      }
    ), 15, 2000, false);

    return allEntityTags.stream()
      .map(e -> e.tags.stream()
        .filter(t -> PINNED_CAPACITY_TAG.equalsIgnoreCase(t.name))
        .map(t -> {
          PinnedServerGroupTag pinnedServerGroupTag = objectMapper.convertValue(t.value, PinnedServerGroupTag.class);
          pinnedServerGroupTag.entityTagsId = e.id;
          return pinnedServerGroupTag;
        })
        .findFirst()
        .orElse(null)
      )
      .filter(Objects::nonNull)
      .collect(Collectors.toList());
  }

  boolean hasCompletedExecution(PinnedServerGroupTag pinnedServerGroupTag) {
    try {
      Execution execution = executionRepository.retrieve(
        pinnedServerGroupTag.executionType, pinnedServerGroupTag.executionId
      );

      return execution.getStatus().isComplete();
    } catch (ExecutionNotFoundException e) {
      return true;
    } catch (Exception e) {
      log.warn("Unable to determine status of execution (executionId: {})", pinnedServerGroupTag.executionId, e);
      errorsCounter.increment();
      return false;
    }
  }

  ServerGroup fetchServerGroup(String account, String region, String serverGroup) {
    return retrySupport.retry(() -> {
      try {
        Response response = oortService.getServerGroup(account, region, serverGroup);
        return objectMapper.readValue(response.getBody().in(), ServerGroup.class);
      } catch (IOException e) {
        throw new RuntimeException(
          format(
            "Unable to fetch server group (account: %s, region: %s, serverGroup: %s)", account, region, serverGroup
          ),
          e
        );
      }
    }, 5, 2000, false);
  }


  private Map<String, Object> buildCleanupOperation(PinnedServerGroupTag pinnedServerGroupTag,
                                                    ServerGroup serverGroup,
                                                    List<Map<String, Object>> jobs) {
    return ImmutableMap.<String, Object>builder()
      .put("application", serverGroup.moniker.app)
      .put(
        "name",
        format(
          "Unpin Server Group: %s to %s/%s/%s",
          pinnedServerGroupTag.serverGroup,
          pinnedServerGroupTag.unpinnedCapacity.min,
          serverGroup.capacity.desired,
          serverGroup.capacity.max
        )
      )
      .put("trigger", Collections.emptyMap())
      .put("user", "Spinnaker")
      .put("stages", jobs)
      .build();
  }

  private Map<String, Object> buildDeleteEntityTagsOperation(PinnedServerGroupTag pinnedServerGroupTag, ServerGroup serverGroup) {
    Map<String, Object> operation = new HashMap<>();

    operation.put("application", serverGroup.moniker.app);
    operation.put("type", "deleteEntityTags");
    operation.put("id", pinnedServerGroupTag.entityTagsId);
    operation.put("tags", Collections.singletonList(PINNED_CAPACITY_TAG));

    return operation;
  }

  private Map<String, Object> buildResizeOperation(PinnedServerGroupTag pinnedServerGroupTag, ServerGroup serverGroup) {
    return ImmutableMap.<String, Object>builder()
      .put("refId", "1")
      .put("asgName", pinnedServerGroupTag.serverGroup)
      .put("serverGroupName", pinnedServerGroupTag.serverGroup)
      .put("type", "resizeServerGroup")
      .put("region", pinnedServerGroupTag.location)
      .put("credentials", pinnedServerGroupTag.account)
      .put("cloudProvider", pinnedServerGroupTag.cloudProvider)
      .put("interestingHealthProviderNames", Collections.emptyList()) // no need to wait on health when only
      .put("capacity", ImmutableMap.<String, Integer>builder()        // adjusting min capacity
        .put("min", pinnedServerGroupTag.unpinnedCapacity.min)
        .put("desired", serverGroup.capacity.desired)
        .put("max", serverGroup.capacity.max)
        .build()
      )
      .put("constraints", Collections.singletonMap("capacity", ImmutableMap.<String, Integer>builder()
        .put("min", serverGroup.capacity.min)                         // ensure that the current capacity matches
        .put("desired", serverGroup.capacity.desired)                 // expectations and has not changed since the
        .put("max", serverGroup.capacity.max)                         // last caching cycle
        .build())
      )
      .build();
  }

  private static class EntityTags {
    public String id;
    public List<Tag> tags;

    private static class Tag {
      public String name;
      public Object value;
    }
  }

  private static class PinnedServerGroupTag {
    public String entityTagsId;
    public String serverGroup;
    public String account;
    public String location;
    public String cloudProvider;

    public Execution.ExecutionType executionType;
    public String executionId;
    public String stageId;

    public Capacity pinnedCapacity;
    public Capacity unpinnedCapacity;
  }

  private static class ServerGroup {
    public Capacity capacity;
    public Moniker moniker;

    private static class Moniker {
      public String app;
    }
  }

  private static class Capacity {
    public Integer min;
    public Integer desired;
    public Integer max;
  }
}
