/*
 * Copyright 2020 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
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

import com.netflix.spinnaker.orca.api.pipeline.SyntheticStageOwner;
import com.netflix.spinnaker.orca.api.pipeline.models.PipelineExecution;
import com.netflix.spinnaker.orca.api.pipeline.models.StageExecution;
import com.netflix.spinnaker.orca.pipeline.model.StageExecutionImpl;
import java.util.Map;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Pulled from StageDefinitionBuilder. No idea what is supposed to replace this method, but it's
 * still used everywhere.
 */
public class StageExecutionFactory {

  @Deprecated
  public static @Nonnull StageExecution newStage(
      @Nonnull PipelineExecution execution,
      @Nonnull String type,
      @Nullable String name,
      @Nonnull Map<String, Object> context,
      @Nullable StageExecution parent,
      @Nullable SyntheticStageOwner stageOwner) {
    StageExecution stage = new StageExecutionImpl(execution, type, name, context);
    if (parent != null) {
      stage.setParentStageId(parent.getId());
    }
    stage.setSyntheticStageOwner(stageOwner);
    return stage;
  }
}
