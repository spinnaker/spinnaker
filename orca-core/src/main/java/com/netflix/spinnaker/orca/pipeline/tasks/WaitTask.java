/*
 * Copyright 2014 Netflix, Inc.
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
import com.netflix.spinnaker.orca.pipeline.WaitStage;
import com.netflix.spinnaker.orca.pipeline.model.Stage;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import javax.annotation.Nonnull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class WaitTask implements RetryableTask {

  private final Clock clock;

  @Autowired
  public WaitTask(Clock clock) {
    this.clock = clock;
  }

  @Override
  public @Nonnull TaskResult execute(@Nonnull Stage stage) {
    WaitStage.WaitStageContext context = stage.mapTo(WaitStage.WaitStageContext.class);

    Instant now = clock.instant();

    if (context.isSkipRemainingWait()) {
      return TaskResult.SUCCEEDED;
    } else if (stage.getStartTime() != null
        && Instant.ofEpochMilli(stage.getStartTime())
            .plus(context.getWaitDuration())
            .isBefore(now)) {
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
    WaitStage.WaitStageContext context = stage.mapTo(WaitStage.WaitStageContext.class);

    if (context.isSkipRemainingWait()) {
      return 0L;
    }

    // Return a backoff time that reflects the requested waitTime
    if (stage.getStartTime() != null) {
      Instant now = clock.instant();
      Instant completion =
          Instant.ofEpochMilli(stage.getStartTime()).plus(context.getWaitDuration());

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
