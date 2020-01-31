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
import java.time.Duration;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.springframework.stereotype.Component;

@Component
public class WaitStage implements StageDefinitionBuilder {

  public static String STAGE_TYPE = "wait";

  @Override
  public void taskGraph(@Nonnull Stage stage, TaskNode.Builder builder) {
    builder.withTask("wait", WaitTask.class);
  }

  public static final class WaitStageContext {
    private final Long waitTime;
    private final boolean skipRemainingWait;

    @JsonCreator
    public WaitStageContext(
        @JsonProperty("waitTime") @Nullable Long waitTime,
        @JsonProperty("skipRemainingWait") @Nullable Boolean skipRemainingWait) {
      this.waitTime = waitTime;
      this.skipRemainingWait = skipRemainingWait == null ? false : skipRemainingWait;
    }

    public WaitStageContext(@Nonnull Long waitTime) {
      this(waitTime, false);
    }

    @JsonIgnore
    public Duration getWaitDuration() {
      return waitTime == null ? Duration.ZERO : Duration.ofSeconds(waitTime);
    }

    public boolean isSkipRemainingWait() {
      return skipRemainingWait;
    }
  }
}
