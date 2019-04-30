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

package com.netflix.spinnaker.orca.pipeline.tasks;

import com.netflix.spinnaker.orca.RetryableTask;
import com.netflix.spinnaker.orca.TaskResult;
import com.netflix.spinnaker.orca.pipeline.WaitUntilStage;
import com.netflix.spinnaker.orca.pipeline.model.Stage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.annotation.Nonnull;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

import static com.netflix.spinnaker.orca.ExecutionStatus.RUNNING;
import static com.netflix.spinnaker.orca.ExecutionStatus.SUCCEEDED;
import static java.util.Collections.singletonMap;

@Component
public class WaitUntilTask implements RetryableTask {

  private final Clock clock;

  @Autowired
  public WaitUntilTask(Clock clock) {
    this.clock = clock;
  }

  @Override
  public @Nonnull
  TaskResult execute(@Nonnull Stage stage) {
    WaitUntilStage.WaitUntilStageContext context = stage.mapTo(WaitUntilStage.WaitUntilStageContext.class);

    if (context.getEpochMillis() == null) {
      return TaskResult.SUCCEEDED;
    }

    Instant now = clock.instant();

    if (context.getStartTime() == null || context.getStartTime() == Instant.EPOCH) {
      return TaskResult.builder(RUNNING).context(singletonMap("startTime", now)).build();
    } else if (context.getEpochMillis() <= now.toEpochMilli()) {
      return TaskResult.SUCCEEDED;
    } else {
      return TaskResult.RUNNING;
    }
  }

  @Override
  public long getBackoffPeriod() {
    return 1_000;
  }

  @Override
  public long getDynamicBackoffPeriod(Stage stage, Duration taskDuration) {
    WaitUntilStage.WaitUntilStageContext context = stage.mapTo(WaitUntilStage.WaitUntilStageContext.class);

    // Return a backoff time that reflects the requested target time.
    if (context.getStartTime() != null && context.getEpochMillis() != null) {
      Instant now = clock.instant();
      Instant completion = Instant.ofEpochMilli(context.getEpochMillis());

      if (completion.isAfter(now)) {
        return completion.toEpochMilli() - now.toEpochMilli();
      }
    }
    return getBackoffPeriod();
  }

  @Override
  public long getTimeout() {
    return Integer.MAX_VALUE;
  }
}
