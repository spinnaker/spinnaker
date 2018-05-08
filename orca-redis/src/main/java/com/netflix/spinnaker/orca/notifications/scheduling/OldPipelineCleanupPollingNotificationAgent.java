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
package com.netflix.spinnaker.orca.notifications.scheduling;

import com.netflix.appinfo.InstanceInfo.InstanceStatus;
import com.netflix.spinnaker.kork.eureka.RemoteStatusChangedEvent;
import com.netflix.spinnaker.kork.jedis.RedisClientDelegate;
import com.netflix.spinnaker.orca.ExecutionStatus;
import com.netflix.spinnaker.orca.pipeline.model.Execution;
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;
import rx.Observable;
import rx.Scheduler;
import rx.Subscription;
import rx.schedulers.Schedulers;

import javax.annotation.PreDestroy;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.netflix.spinnaker.orca.pipeline.model.Execution.ExecutionType.PIPELINE;
import static com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository.IterableUtil.toStream;

// TODO rz - Remove redis know-how and move back into orca-core
@Component
@ConditionalOnExpression("${pollers.oldPipelineCleanup.enabled:false}")
public class OldPipelineCleanupPollingNotificationAgent implements ApplicationListener<RemoteStatusChangedEvent> {

  private static final List<String> COMPLETED_STATUSES = ExecutionStatus.COMPLETED.stream().map(Enum::toString).collect(Collectors.toList());

  private final Logger log = LoggerFactory.getLogger(OldPipelineCleanupPollingNotificationAgent.class);

  private Scheduler scheduler = Schedulers.io();
  private Subscription subscription;

  private Predicate<Execution> filter = new Predicate<Execution>() {
    @Override
    public boolean test(Execution execution) {
      if (!COMPLETED_STATUSES.contains(execution.getStatus().toString())) {
        return false;
      }
      long startTime = execution.getStartTime() == null ? execution.getBuildTime() : execution.getStartTime();
      return startTime <= (Instant.ofEpochMilli(clock.millis()).minus(thresholdDays, ChronoUnit.DAYS).toEpochMilli());
    }
  };

  private Function<Execution, PipelineExecutionDetails> mapper = execution -> new PipelineExecutionDetails(
    execution.getId(),
    execution.getApplication(),
    execution.getPipelineConfigId() == null ? "ungrouped" : execution.getPipelineConfigId(),
    execution.getStatus(),
    execution.getStartTime(),
    execution.getBuildTime()
  );

  private Comparator<PipelineExecutionDetails> sorter = (o1, o2) -> {
    if (o1.getRealStartTime() > o2.getRealStartTime()) {
      return 1;
    }
    if (o1.getRealStartTime() < o2.getRealStartTime()) {
      return -1;
    }
    return 0;
  };

  private Clock clock = Clock.systemDefaultZone();

  @Autowired
  private ExecutionRepository executionRepository;

  @Autowired
  private RedisClientDelegate redisClientDelegate;

  @Value("${pollers.oldPipelineCleanup.intervalMs:3600000}")
  private long pollingIntervalMs;

  @Value("${pollers.oldPipelineCleanup.thresholdDays:30}")
  private int thresholdDays;

  @Value("${pollers.oldPipelineCleanup.minimumPipelineExecutions:5}")
  private int minimumPipelineExecutions;

  @PreDestroy
  private void stopPolling() {
    if (subscription != null) {
      subscription.unsubscribe();
    }
  }

  @Override
  public void onApplicationEvent(RemoteStatusChangedEvent event) {
    if (event.getSource().isUp()) {
      log.info("Instance is " + event.getSource().getStatus() + "... starting old pipeline cleanup");
      startPolling();
    } else if (event.getSource().getPreviousStatus() == InstanceStatus.UP) {
      log.warn("Instance is " + event.getSource().getStatus() + "... stopping old pipeline cleanup");
      stopPolling();
    }
  }

  private void startPolling() {
    subscription = Observable
      .timer(pollingIntervalMs, TimeUnit.MILLISECONDS, scheduler)
      .repeat()
      .subscribe(aLong -> tick());
  }

  private void tick() {
    List<String> applications = new ArrayList<>();

    try {
      redisClientDelegate.withKeyScan("pipeline:app:*", 200, r -> {
        applications.addAll(r.getResults().stream().map(k -> k.split(":")[2]).collect(Collectors.toList()));
      });

      applications.forEach(app -> {
        log.debug("Cleaning up " + app);
        cleanupApp(toStream(executionRepository.retrievePipelinesForApplication(app)));
      });

    } catch (Exception e) {
      log.error("Cleanup failed", e);
    }
  }

  private void cleanupApp(Stream<Execution> executions) {
    List<PipelineExecutionDetails> allPipelines = executions.filter(filter).map(mapper).collect(Collectors.toList());

    Map<String, List<PipelineExecutionDetails>> groupedPipelines = new HashMap<>();
    allPipelines.forEach(p -> {
      if (!groupedPipelines.containsKey(p.pipelineConfigId)) {
        groupedPipelines.put(p.pipelineConfigId, new ArrayList<>());
      }
      groupedPipelines.get(p.pipelineConfigId).add(p);
    });

    groupedPipelines.forEach((key, value) -> cleanup(value));
  }

  private void cleanup(List<PipelineExecutionDetails> executions) {
    if (executions.size() <= minimumPipelineExecutions) {
      return;
    }

    executions.sort(sorter);
    executions.subList(0, (executions.size() - minimumPipelineExecutions)).forEach(p -> {
      long startTime = p.startTime == null ? p.buildTime : p.startTime;
      long days = ChronoUnit.DAYS.between(Instant.ofEpochMilli(startTime), Instant.ofEpochMilli(clock.millis()));
      if (days > thresholdDays && !hasEntityTags(p.id)) {
        log.info("Deleting pipeline execution " + p.id + ": " + p.toString());
        executionRepository.delete(PIPELINE, p.id);
      }
    });
  }

  private boolean hasEntityTags(String pipelineId) {
    // TODO rz - This index exists only in Netflix-land. Should be added to OSS eventually
    return redisClientDelegate.withCommandsClient(c -> {
      return c.sismember("existingServerGroups:pipeline", "pipeline:" + pipelineId);
    });
  }

  private static class PipelineExecutionDetails {
    String id;
    String application;
    String pipelineConfigId;
    ExecutionStatus status;
    Long startTime;
    Long buildTime;

    PipelineExecutionDetails(String id, String application, String pipelineConfigId, ExecutionStatus status, Long startTime, Long buildTime) {
      this.id = id;
      this.application = application;
      this.pipelineConfigId = pipelineConfigId;
      this.status = status;
      this.startTime = startTime;
      this.buildTime = buildTime;
    }

    Long getRealStartTime() {
      return startTime == null ? buildTime : startTime;
    }

    @Override
    public String toString() {
      return "PipelineExecutionDetails{" +
        "id='" + id + '\'' +
        ", application='" + application + '\'' +
        ", pipelineConfigId='" + pipelineConfigId + '\'' +
        ", status=" + status +
        ", startTime=" + startTime +
        ", buildTime=" + buildTime +
        '}';
    }
  }
}
