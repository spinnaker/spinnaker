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

import static com.netflix.spinnaker.orca.api.pipeline.models.ExecutionType.PIPELINE;

import com.netflix.spectator.api.Id;
import com.netflix.spectator.api.LongTaskTimer;
import com.netflix.spectator.api.Registry;
import com.netflix.spinnaker.orca.api.pipeline.models.ExecutionStatus;
import com.netflix.spinnaker.orca.api.pipeline.models.PipelineExecution;
import com.netflix.spinnaker.orca.notifications.AbstractPollingNotificationAgent;
import com.netflix.spinnaker.orca.notifications.NotificationClusterLock;
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.functions.Function;
import io.reactivex.rxjava3.functions.Predicate;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnExpression(
    "${pollers.old-pipeline-cleanup.enabled:false} && !${execution-repository.sql.enabled:false}")
public class OldPipelineCleanupPollingNotificationAgent extends AbstractPollingNotificationAgent {

  private static final List<String> COMPLETED_STATUSES =
      ExecutionStatus.COMPLETED.stream().map(Enum::toString).collect(Collectors.toList());

  private final Logger log =
      LoggerFactory.getLogger(OldPipelineCleanupPollingNotificationAgent.class);

  private Predicate<PipelineExecution> filter =
      new Predicate<PipelineExecution>() {
        @Override
        public boolean test(PipelineExecution execution) {
          if (!COMPLETED_STATUSES.contains(execution.getStatus().toString())) {
            return false;
          }
          long startTime =
              execution.getStartTime() == null
                  ? execution.getBuildTime()
                  : execution.getStartTime();
          return startTime
              <= (Instant.ofEpochMilli(clock.millis())
                  .minus(thresholdDays, ChronoUnit.DAYS)
                  .toEpochMilli());
        }
      };

  private Function<? super PipelineExecution, PipelineExecutionDetails> mapper =
      execution ->
          new PipelineExecutionDetails(
              execution.getId(),
              execution.getApplication(),
              execution.getPipelineConfigId() == null
                  ? "ungrouped"
                  : execution.getPipelineConfigId(),
              execution.getStatus(),
              execution.getStartTime(),
              execution.getBuildTime());

  private Comparator<PipelineExecutionDetails> sorter =
      (o1, o2) -> {
        if (o1.getRealStartTime() > o2.getRealStartTime()) {
          return 1;
        }
        if (o1.getRealStartTime() < o2.getRealStartTime()) {
          return -1;
        }
        return 0;
      };

  private final Clock clock;
  private final ExecutionRepository executionRepository;
  private final Registry registry;

  private final long pollingIntervalMs;
  private final int thresholdDays;
  private final int minimumPipelineExecutions;
  private final List<PipelineDependencyCleanupOperator> pipelineDependencyCleanupOperators;

  private final Id deletedId;
  private final Id timerId;

  @Autowired
  public OldPipelineCleanupPollingNotificationAgent(
      NotificationClusterLock clusterLock,
      ExecutionRepository executionRepository,
      Clock clock,
      Registry registry,
      @Value("${pollers.old-pipeline-cleanup.interval-ms:3600000}") long pollingIntervalMs,
      @Value("${pollers.old-pipeline-cleanup.threshold-days:30}") int thresholdDays,
      @Value("${pollers.old-pipeline-cleanup.minimum-pipeline-executions:5}")
          int minimumPipelineExecutions,
      List<PipelineDependencyCleanupOperator> pipelineDependencyCleanupOperators) {
    super(clusterLock);
    this.executionRepository = executionRepository;
    this.clock = clock;
    this.registry = registry;
    this.pollingIntervalMs = pollingIntervalMs;
    this.thresholdDays = thresholdDays;
    this.minimumPipelineExecutions = minimumPipelineExecutions;
    this.pipelineDependencyCleanupOperators = pipelineDependencyCleanupOperators;

    deletedId = registry.createId("pollers.oldPipelineCleanup.deleted");
    timerId = registry.createId("pollers.oldPipelineCleanup.timing");
  }

  @Override
  protected long getPollingInterval() {
    return pollingIntervalMs;
  }

  @Override
  protected String getNotificationType() {
    return OldPipelineCleanupPollingNotificationAgent.class.getSimpleName();
  }

  @Override
  protected void tick() {
    LongTaskTimer timer = registry.longTaskTimer(timerId);
    long timerId = timer.start();
    try {
      executionRepository
          .retrieveAllApplicationNames(PIPELINE)
          .forEach(
              app -> {
                log.debug("Cleaning up " + app);
                cleanupApp(executionRepository.retrievePipelinesForApplication(app));
              });
    } catch (Exception e) {
      log.error("Cleanup failed", e);
    } finally {
      timer.stop(timerId);
    }
  }

  private void cleanupApp(Observable<PipelineExecution> observable) {
    List<PipelineExecutionDetails> allPipelines =
        observable.filter(filter).map(mapper).toList().blockingGet();

    Map<String, List<PipelineExecutionDetails>> groupedPipelines = new HashMap<>();
    allPipelines.forEach(
        p -> {
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

    List<PipelineExecutionDetails> removingPipelineExecutions =
        executions.subList(0, (executions.size() - minimumPipelineExecutions));

    List<String> removingPipelineExecutionIds =
        removingPipelineExecutions.stream()
            .map(pipelineExecutionDetails -> pipelineExecutionDetails.id)
            .collect(Collectors.toList());

    pipelineDependencyCleanupOperators.forEach(
        pipelineDependencyCleanupOperator ->
            pipelineDependencyCleanupOperator.cleanup(removingPipelineExecutionIds));

    removingPipelineExecutions.forEach(
        p -> {
          log.info("Deleting pipeline execution " + p.id + ": " + p.toString());
          executionRepository.delete(PIPELINE, p.id);
          registry.counter(deletedId.withTag("application", p.application)).increment();
        });
  }

  private static class PipelineExecutionDetails {
    String id;
    String application;
    String pipelineConfigId;
    ExecutionStatus status;
    Long startTime;
    Long buildTime;

    PipelineExecutionDetails(
        String id,
        String application,
        String pipelineConfigId,
        ExecutionStatus status,
        Long startTime,
        Long buildTime) {
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
      return "PipelineExecutionDetails{"
          + "id='"
          + id
          + '\''
          + ", application='"
          + application
          + '\''
          + ", pipelineConfigId='"
          + pipelineConfigId
          + '\''
          + ", status="
          + status
          + ", startTime="
          + startTime
          + ", buildTime="
          + buildTime
          + '}';
    }
  }
}
