/*
 * Copyright 2019 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.orca.pipeline.tasks;

import com.netflix.spinnaker.orca.ExecutionStatus;
import com.netflix.spinnaker.orca.RetryableTask;
import com.netflix.spinnaker.orca.TaskResult;
import com.netflix.spinnaker.orca.conditions.ConditionConfigurationProperties;
import com.netflix.spinnaker.orca.conditions.Condition;
import com.netflix.spinnaker.orca.conditions.ConditionSupplier;
import com.netflix.spinnaker.orca.pipeline.WaitForConditionStage.WaitForConditionContext;
import com.netflix.spinnaker.orca.pipeline.model.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Component;

import javax.annotation.Nonnull;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static com.netflix.spinnaker.orca.pipeline.WaitForConditionStage.WaitForConditionContext.*;

@Component
@ConditionalOnExpression("${tasks.evaluateCondition.enabled:false}")
public class EvaluateConditionTask implements RetryableTask {
  private static final Logger log = LoggerFactory.getLogger(EvaluateConditionTask.class);
  private final ConditionConfigurationProperties conditionsConfigurationProperties;
  private final Optional<List<ConditionSupplier>> suppliers;
  private final Clock clock;

  @Autowired
  public EvaluateConditionTask(
    ConditionConfigurationProperties conditionsConfigurationProperties,
    Optional<List<ConditionSupplier>> suppliers,
    Clock clock
  ) {
    this.conditionsConfigurationProperties = conditionsConfigurationProperties;
    this.suppliers = suppliers;
    this.clock = clock;
  }

  @Override
  public long getBackoffPeriod() {
    return 3000L;
  }

  @Override
  public long getTimeout() {
    return TimeUnit.SECONDS.toMillis(conditionsConfigurationProperties.getWaitTimeoutMs());
  }

  @Nonnull
  @Override
  public TaskResult execute(@Nonnull Stage stage) {
    final WaitForConditionContext ctx = stage.mapTo(WaitForConditionContext.class);
    if (ctx.getStatus() == Status.SKIPPED || !suppliers.isPresent()) {
      return new TaskResult(ExecutionStatus.SUCCEEDED, Collections.singletonMap("status", Status.SKIPPED));
    }

    Duration backoff = Duration.ofMillis(conditionsConfigurationProperties.getBackoffWaitMs());
    Instant startTime = getStartTime(stage);
    Instant now = clock.instant();
    if (ctx.getStatus() != null && startTime.plus(backoff).isAfter(now)) { // if status is null, then
      return new TaskResult(
        ExecutionStatus.RUNNING,
        Collections.singletonMap("status", Status.WAITING)
      );
    }

    try {
      Set<Condition> conditions = suppliers.get()
        .stream()
        .flatMap(supplier -> supplier.getConditions(stage).stream()).filter(Objects::nonNull)
        .collect(Collectors.toSet());

      log.info("Found conditions: {}", conditions);
      final Status status = conditions.isEmpty() ? Status.SKIPPED : Status.WAITING;
      if (status == Status.WAITING) {
        return new TaskResult(
          ExecutionStatus.RUNNING,
          Collections.singletonMap("status", status),
          Collections.singletonMap("conditions", conditions)
        );
      }

      return new TaskResult(ExecutionStatus.SUCCEEDED, Collections.singletonMap("status", status));
    } catch (Exception e) {
      log.error("Error occurred while fetching for conditions to eval.", e);
      return new TaskResult(ExecutionStatus.SUCCEEDED, Collections.singletonMap("status", Status.ERROR));
    }
  }

  private Instant getStartTime(Stage stage) {
    return Instant.ofEpochMilli(Optional.ofNullable(stage.getStartTime()).orElse(clock.millis()));
  }
}
