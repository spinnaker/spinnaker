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

import java.time.Clock;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import javax.annotation.Nonnull;
import com.netflix.spinnaker.orca.RetryableTask;
import com.netflix.spinnaker.orca.TaskResult;
import com.netflix.spinnaker.orca.pipeline.model.Stage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import static com.netflix.spinnaker.orca.ExecutionStatus.RUNNING;
import static com.netflix.spinnaker.orca.ExecutionStatus.SUCCEEDED;
import static java.lang.Long.parseLong;
import static java.util.Collections.emptyMap;
import static java.util.Collections.singletonMap;

@Component
public class WaitTask implements RetryableTask {

  private final Clock clock;

  @Autowired
  public WaitTask(Clock clock) {this.clock = clock;}

  @Override
  public @Nonnull TaskResult execute(@Nonnull Stage stage) {
    if (stage.getContext().get("waitTime") == null) {
      return new TaskResult(SUCCEEDED);
    }
    // wait time is specified in seconds
    long waitTime = parseLong(stage.getContext().get("waitTime").toString());
    long waitTimeMs = TimeUnit.MILLISECONDS.convert(waitTime, TimeUnit.SECONDS);
    long now = clock.millis();

    Object waitTaskState = stage.getContext().get("waitTaskState");
    if (Boolean.TRUE.equals(stage.getContext().get("skipRemainingWait"))) {
      return new TaskResult(SUCCEEDED, singletonMap("waitTaskState", emptyMap()));
    } else if (waitTaskState == null || !(waitTaskState instanceof Map)) {
      return new TaskResult(RUNNING, singletonMap("waitTaskState", singletonMap("startTime", now)));
    } else if (now - ((long) ((Map) stage.getContext().get("waitTaskState")).get("startTime")) > waitTimeMs) {
      return new TaskResult(SUCCEEDED, singletonMap("waitTaskState", emptyMap()));
    } else {
      return new TaskResult(RUNNING);
    }
  }

  @Override public long getBackoffPeriod() {
    return 15_000;
  }

  @Override public long getTimeout() {
    return Integer.MAX_VALUE;
  }
}
