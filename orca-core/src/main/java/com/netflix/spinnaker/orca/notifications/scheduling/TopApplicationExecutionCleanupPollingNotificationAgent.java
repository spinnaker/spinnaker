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

import com.google.common.annotations.VisibleForTesting;
import com.netflix.spectator.api.Id;
import com.netflix.spectator.api.LongTaskTimer;
import com.netflix.spectator.api.Registry;
import com.netflix.spinnaker.orca.notifications.AbstractPollingNotificationAgent;
import com.netflix.spinnaker.orca.notifications.NotificationClusterLock;
import com.netflix.spinnaker.orca.pipeline.model.Execution;
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository.ExecutionCriteria;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Component;
import rx.Observable;
import rx.Scheduler;
import rx.functions.Func1;
import rx.schedulers.Schedulers;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static com.netflix.spinnaker.orca.pipeline.model.Execution.ExecutionType.ORCHESTRATION;
import static java.lang.String.format;
import static java.time.temporal.ChronoUnit.DAYS;
import static java.util.Comparator.comparing;

@Component
@ConditionalOnExpression(value = "${pollers.topApplicationExecutionCleanup.enabled:false}")
public class TopApplicationExecutionCleanupPollingNotificationAgent extends AbstractPollingNotificationAgent {

  private final Logger log = LoggerFactory.getLogger(getClass());

  private Scheduler scheduler = Schedulers.io();

  private Func1<Execution, Boolean> filter = (Execution execution) ->
    execution.getStatus().isComplete() || Instant.ofEpochMilli(execution.getBuildTime()).isBefore(Instant.now().minus(31, DAYS));
  private Func1<Execution, Map> mapper = (Execution execution) -> {
    Map<String, Object> builder = new HashMap<>();
    builder.put("id", execution.getId());
    builder.put("startTime", execution.getStartTime());
    builder.put("pipelineConfigId", execution.getPipelineConfigId());
    builder.put("status", execution.getStatus());
    return builder;
  };

  private final PollingAgentExecutionRepository executionRepository;
  private final Registry registry;
  private final long pollingIntervalMs;
  private final int threshold;

  private final Id deleteCountId;
  private final Id timerId;

  @Autowired
  public TopApplicationExecutionCleanupPollingNotificationAgent(NotificationClusterLock clusterLock,
                                                                PollingAgentExecutionRepository executionRepository,
                                                                Registry registry,
                                                                @Value("${pollers.topApplicationExecutionCleanup.intervalMs:3600000}") long pollingIntervalMs,
                                                                @Value("${pollers.topApplicationExecutionCleanup.threshold:2500}") int threshold) {
    super(clusterLock);
    this.executionRepository = executionRepository;
    this.registry = registry;
    this.pollingIntervalMs = pollingIntervalMs;
    this.threshold = threshold;

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

  protected void startPolling() {
    subscription = Observable
      .timer(pollingIntervalMs, TimeUnit.MILLISECONDS, scheduler)
      .repeat()
      .subscribe(interval -> tick());
  }

  @VisibleForTesting
  void tick() {
    LongTaskTimer timer = registry.longTaskTimer(timerId);
    long timerId = timer.start();

    log.info("Starting cleanup");
    try {
      executionRepository.retrieveAllApplicationNames(ORCHESTRATION, threshold).forEach(app -> {
        log.info("Cleaning up orchestration executions (application: {}, threshold: {})", app, threshold);

        ExecutionCriteria executionCriteria = new ExecutionCriteria();
        executionCriteria.setLimit(Integer.MAX_VALUE);
        cleanup(executionRepository.retrieveOrchestrationsForApplication(app, executionCriteria), app, "orchestration");
      });
    } catch (Exception e) {
      log.error("Cleanup failed", e);
    } finally {
      timer.stop(timerId);
    }
  }

  private void cleanup(Observable<Execution> observable, String application, String type) {
    List<Map> executions = observable.filter(filter).map(mapper).toList().toBlocking().single();
    executions.sort(comparing(a -> (Long) Optional.ofNullable(a.get("startTime")).orElse(0L)));
    if (executions.size() > threshold) {
      executions.subList(0, (executions.size() - threshold)).forEach(it -> {
        Long startTime = Optional.ofNullable((Long) it.get("startTime")).orElseGet(() -> (Long) it.get("buildTime"));
        log.info("Deleting {} execution {} (startTime: {}, application: {}, pipelineConfigId: {}, status: {})", type, it.get("id"), startTime != null ? Instant.ofEpochMilli(startTime) : null, application, it.get("pipelineConfigId"), it.get("status"));
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
