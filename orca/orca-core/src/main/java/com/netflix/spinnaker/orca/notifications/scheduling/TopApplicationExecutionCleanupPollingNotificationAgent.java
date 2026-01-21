/*
 * Copyright 2015 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
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

import static com.netflix.spinnaker.orca.api.pipeline.models.ExecutionType.ORCHESTRATION;
import static java.lang.String.format;
import static java.time.temporal.ChronoUnit.DAYS;
import static java.util.Comparator.comparing;

import com.google.common.annotations.VisibleForTesting;
import com.netflix.spectator.api.Id;
import com.netflix.spectator.api.LongTaskTimer;
import com.netflix.spectator.api.Registry;
import com.netflix.spinnaker.orca.api.pipeline.models.PipelineExecution;
import com.netflix.spinnaker.orca.notifications.AbstractPollingNotificationAgent;
import com.netflix.spinnaker.orca.notifications.NotificationClusterLock;
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository;
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository.ExecutionCriteria;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.functions.Function;
import io.reactivex.rxjava3.functions.Predicate;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnExpression(
    "${pollers.top-application-execution-cleanup.enabled:false} && !${execution-repository.sql.enabled:false}")
public class TopApplicationExecutionCleanupPollingNotificationAgent
    extends AbstractPollingNotificationAgent {

  private final Logger log = LoggerFactory.getLogger(getClass());

  private Predicate<? super PipelineExecution> filter =
      (PipelineExecution execution) ->
          execution.getStatus().isComplete()
              || Instant.ofEpochMilli(execution.getBuildTime())
                  .isBefore(Instant.now().minus(31, DAYS));
  private Function<? super PipelineExecution, ? extends Map> mapper =
      (PipelineExecution execution) -> {
        Map<String, Object> builder = new HashMap<>();
        builder.put("id", execution.getId());
        builder.put("startTime", execution.getStartTime());
        builder.put("pipelineConfigId", execution.getPipelineConfigId());
        builder.put("status", execution.getStatus());
        return builder;
      };

  private final ExecutionRepository executionRepository;
  private final Registry registry;
  private final long pollingIntervalMs;
  private final int threshold;
  private final List<PipelineDependencyCleanupOperator> pipelineDependencyCleanupOperators;

  private final Id deleteCountId;
  private final Id timerId;

  @Autowired
  public TopApplicationExecutionCleanupPollingNotificationAgent(
      NotificationClusterLock clusterLock,
      ExecutionRepository executionRepository,
      Registry registry,
      @Value("${pollers.top-application-execution-cleanup.interval-ms:3600000}")
          long pollingIntervalMs,
      @Value("${pollers.top-application-execution-cleanup.threshold:2500}") int threshold,
      List<PipelineDependencyCleanupOperator> pipelineDependencyCleanupOperators) {
    super(clusterLock);
    this.executionRepository = executionRepository;
    this.registry = registry;
    this.pollingIntervalMs = pollingIntervalMs;
    this.threshold = threshold;
    this.pipelineDependencyCleanupOperators = pipelineDependencyCleanupOperators;

    deleteCountId = registry.createId("pollers.topApplicationExecutionCleanup.deleted");
    timerId = registry.createId("pollers.topApplicationExecutionCleanup.timing");
  }

  @Override
  protected long getPollingInterval() {
    return pollingIntervalMs;
  }

  @Override
  protected String getNotificationType() {
    return TopApplicationExecutionCleanupPollingNotificationAgent.class.getSimpleName();
  }

  @VisibleForTesting
  protected void tick() {
    LongTaskTimer timer = registry.longTaskTimer(timerId);
    long timerId = timer.start();

    log.info("Starting cleanup");
    try {
      executionRepository
          .retrieveAllApplicationNames(ORCHESTRATION, threshold)
          .forEach(
              app -> {
                log.info(
                    "Cleaning up orchestration executions (application: {}, threshold: {})",
                    app,
                    threshold);

                ExecutionCriteria executionCriteria = new ExecutionCriteria();
                executionCriteria.setPageSize(Integer.MAX_VALUE);
                cleanup(
                    executionRepository.retrieveOrchestrationsForApplication(
                        app, executionCriteria),
                    app,
                    "orchestration");
              });
    } catch (Exception e) {
      log.error("Cleanup failed", e);
    } finally {
      timer.stop(timerId);
    }
  }

  private void cleanup(Observable<PipelineExecution> observable, String application, String type) {
    List<? extends Map> executions = observable.filter(filter).map(mapper).toList().blockingGet();
    executions.sort(comparing(a -> (Long) Optional.ofNullable(a.get("startTime")).orElse(0L)));
    if (executions.size() > threshold) {
      List<? extends Map> removingPipelineExecutions =
          executions.subList(0, (executions.size() - threshold));

      List<String> removingPipelineExecutionIds =
          removingPipelineExecutions.stream()
              .map(pipelineExecution -> (String) pipelineExecution.get("id"))
              .collect(Collectors.toList());

      pipelineDependencyCleanupOperators.forEach(
          pipelineDependencyCleanupOperator ->
              pipelineDependencyCleanupOperator.cleanup(removingPipelineExecutionIds));

      removingPipelineExecutions.forEach(
          it -> {
            Long startTime =
                Optional.ofNullable((Long) it.get("startTime"))
                    .orElseGet(() -> (Long) it.get("buildTime"));
            log.info(
                "Deleting {} execution {} (startTime: {}, application: {}, pipelineConfigId: {}, status: {})",
                type,
                it.get("id"),
                startTime != null ? Instant.ofEpochMilli(startTime) : null,
                application,
                it.get("pipelineConfigId"),
                it.get("status"));
            if (type.equals("orchestration")) {
              executionRepository.delete(ORCHESTRATION, (String) it.get("id"));
              registry.counter(deleteCountId.withTag("application", application)).increment();
            } else {
              throw new IllegalArgumentException(format("Unsupported type '%s'", type));
            }
          });
    }
  }
}
