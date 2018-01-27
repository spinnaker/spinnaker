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

import java.time.Instant;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import javax.annotation.PreDestroy;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.annotations.VisibleForTesting;
import com.netflix.discovery.StatusChangeEvent;
import com.netflix.spinnaker.kork.eureka.RemoteStatusChangedEvent;
import com.netflix.spinnaker.orca.pipeline.model.Execution;
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository;
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository.ExecutionCriteria;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.ScanParams;
import redis.clients.jedis.ScanResult;
import redis.clients.util.Pool;
import rx.Observable;
import rx.Scheduler;
import rx.Subscription;
import rx.functions.Func1;
import rx.schedulers.Schedulers;
import static com.netflix.appinfo.InstanceInfo.InstanceStatus.UP;
import static com.netflix.spinnaker.orca.pipeline.model.Execution.ExecutionType.ORCHESTRATION;
import static java.lang.String.format;
import static java.time.temporal.ChronoUnit.DAYS;
import static java.util.Comparator.comparing;
import static java.util.stream.Collectors.toList;

@Component
@ConditionalOnExpression(value = "${pollers.topApplicationExecutionCleanup.enabled:false}")
public class TopApplicationExecutionCleanupPollingNotificationAgent implements ApplicationListener<RemoteStatusChangedEvent> {

  private final Logger log = LoggerFactory.getLogger(getClass());

  private Scheduler scheduler = Schedulers.io();
  private Subscription subscription;

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

  @Autowired
  ObjectMapper objectMapper;

  @Autowired
  ExecutionRepository executionRepository;

  @Autowired
  Pool<Jedis> jedisPool;

  @Value("${pollers.topApplicationExecutionCleanup.intervalMs:3600000}")
  long pollingIntervalMs;

  @Value("${pollers.topApplicationExecutionCleanup.threshold:2500}")
  int threshold;

  @PreDestroy
  void stopPolling() {
    if (subscription != null) subscription.unsubscribe();
  }

  @Override
  public void onApplicationEvent(RemoteStatusChangedEvent event) {
    StatusChangeEvent it = event.getSource();
    if (it.getStatus() == UP) {
      log.info("Instance is {}... starting top application execution cleanup", it.getStatus());
      startPolling();
    } else if (it.getPreviousStatus() == UP) {
      log.warn("Instance is {}... stopping top application execution cleanup", it.getStatus());
      stopPolling();
    }
  }

  private void startPolling() {
    subscription = Observable
      .timer(pollingIntervalMs, TimeUnit.MILLISECONDS, scheduler)
      .repeat()
      .subscribe(interval -> tick());
  }

  @VisibleForTesting
  void tick() {
    ScanParams scanParams = new ScanParams().match("orchestration:app:*").count(2000);
    String cursor = "0";
    try {
      List<String> appOrchestrations = new ArrayList<>();
      while (true) {
        String finalCursor = cursor;
        ScanResult<String> result = jedis(it -> it.scan(finalCursor, scanParams));
        appOrchestrations.addAll(result.getResult());
        cursor = result.getStringCursor();
        if (cursor.equals("0")) {
          break;
        }
      }

      List<String> filtered = appOrchestrations.stream().filter(id ->
        jedis(it -> it.scard(id)) > threshold
      ).collect(toList());

      filtered.forEach(id -> {
        String[] parts = id.split(":");
        String type = parts[0];
        String application = parts[2];
        if (type.equals("orchestration")) {
          log.info("Cleaning up orchestration executions (application: {}, threshold: {})", application, threshold);

          ExecutionCriteria executionCriteria = new ExecutionCriteria();
          executionCriteria.setLimit(Integer.MAX_VALUE);
          cleanup(executionRepository.retrieveOrchestrationsForApplication(application, executionCriteria), application, "orchestration");
        } else {
          log.error("Unable to cleanup executions, unsupported type: {}", type);
        }
      });
    } catch (Exception e) {
      log.error("Cleanup failed", e);
    }
  }

  private <T> T jedis(Function<Jedis, T> work) {
    try (Jedis jedis = jedisPool.getResource()) {
      return work.apply(jedis);
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
        } else {
          throw new IllegalArgumentException(format("Unsupported type '%s'", type));
        }
      });
    }
  }
}
