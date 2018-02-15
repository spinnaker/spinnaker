/*
 * Copyright 2016 Netflix, Inc.
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

package com.netflix.spinnaker.orca.pipeline;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.netflix.spinnaker.orca.pipeline.model.Stage;
import com.netflix.spinnaker.orca.pipeline.tasks.WaitTask;
import org.springframework.stereotype.Component;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.time.Duration;
import java.time.Instant;

@Component
public class WaitStage implements StageDefinitionBuilder {
  @Override
  public void taskGraph(Stage stage, TaskNode.Builder builder) {
    builder.withTask("wait", WaitTask.class);
  }

  public static final class WaitStageContext {
    private final Long waitTime;
    private final boolean skipRemainingWait;
    private final Instant startTime;

    @JsonCreator
    public WaitStageContext(
      @JsonProperty("waitTime") @Nullable Long waitTime,
      @JsonProperty("skipRemainingWait") @Nullable Boolean skipRemainingWait,
      @JsonProperty("startTime") @Nullable Instant startTime
    ) {
      this.waitTime = waitTime;
      this.skipRemainingWait = skipRemainingWait == null ? false : skipRemainingWait;
      this.startTime = startTime;
    }

    public WaitStageContext(@Nonnull Long waitTime) {
      this(waitTime, false, null);
    }

    public @Nullable Long getWaitTime() {
      return waitTime;
    }

    @JsonIgnore
    public @Nullable Duration getWaitDuration() {
      return waitTime == null ? null : Duration.ofSeconds(waitTime);
    }

    public boolean isSkipRemainingWait() {
      return skipRemainingWait;
    }

    public @Nullable Instant getStartTime() {
      return startTime;
    }
  }
}
