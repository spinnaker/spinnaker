/*
 * Copyright 2018 Netflix, Inc.
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
import com.fasterxml.jackson.annotation.JsonProperty;
import com.netflix.spinnaker.orca.pipeline.model.Stage;
import com.netflix.spinnaker.orca.pipeline.tasks.WaitUntilTask;
import java.time.Instant;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.springframework.stereotype.Component;

@Component
public class WaitUntilStage implements StageDefinitionBuilder {

  public static String STAGE_TYPE = "waitUntil";

  @Override
  public void taskGraph(Stage stage, TaskNode.Builder builder) {
    builder.withTask("waitUntil", WaitUntilTask.class);
  }

  public static final class WaitUntilStageContext {
    private final Long epochMillis;
    private final Instant startTime;

    @JsonCreator
    public WaitUntilStageContext(
        @JsonProperty("epochMillis") @Nullable Long epochMillis,
        @JsonProperty("startTime") @Nullable Instant startTime) {
      this.epochMillis = epochMillis;
      this.startTime = startTime;
    }

    public WaitUntilStageContext(@Nonnull Long epochMillis) {
      this(epochMillis, null);
    }

    public @Nullable Long getEpochMillis() {
      return epochMillis;
    }

    public @Nullable Instant getStartTime() {
      return startTime;
    }
  }
}
