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

import com.netflix.spinnaker.orca.pipeline.model.Execution;
import com.netflix.spinnaker.orca.pipeline.model.Stage;

public final class StageStarted extends ExecutionEvent {
  private final String stageId;

  public StageStarted(Object source, Class<? extends Execution> executionType, String executionId, String stageId) {
    super(source, executionType, executionId);
    this.stageId = stageId;
  }

  public StageStarted(Object source, Stage<? extends Execution<?>> stage) {
    this(source, stage.getExecution().getClass(), stage.getExecution().getId(), stage.getId());
  }

  public String getStageId() {
    return stageId;
  }
}
