/*
 * Copyright 2017 Netflix, Inc.
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

package com.netflix.spinnaker.orca.events;

import javax.annotation.Nonnull;
import com.netflix.spinnaker.orca.pipeline.model.Execution.ExecutionType;
import com.netflix.spinnaker.orca.pipeline.model.Stage;

public final class StageStarted extends ExecutionEvent {
  private final String stageId;
  private final String stageType;
  private final String stageName;

  public StageStarted(
    @Nonnull Object source,
    @Nonnull ExecutionType executionType,
    @Nonnull String executionId,
    @Nonnull String stageId,
    @Nonnull String stageType,
    @Nonnull String stageName
  ) {
    super(source, executionType, executionId);
    this.stageId = stageId;
    this.stageType = stageType;
    this.stageName = stageName;
  }

  public StageStarted(
    @Nonnull Object source,
    @Nonnull Stage stage
  ) {
    this(source, stage.getExecution().getType(), stage.getExecution().getId(), stage.getId(), stage.getType(), stage.getName());
  }

  public @Nonnull String getStageId() {
    return stageId;
  }

  public @Nonnull String getStageType() {
    return stageType;
  }

  public @Nonnull String getStageName() {
    return stageName;
  }
}
