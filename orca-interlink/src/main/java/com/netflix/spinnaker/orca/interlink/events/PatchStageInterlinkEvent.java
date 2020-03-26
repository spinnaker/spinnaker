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

package com.netflix.spinnaker.orca.interlink.events;

import static com.netflix.spinnaker.orca.interlink.events.InterlinkEvent.EventType.PATCH;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Preconditions;
import com.netflix.spinnaker.orca.api.pipeline.models.ExecutionType;
import com.netflix.spinnaker.orca.api.pipeline.models.StageExecution;
import com.netflix.spinnaker.orca.pipeline.CompoundExecutionOperator;
import com.netflix.spinnaker.orca.pipeline.model.StageExecutionImpl;
import javax.annotation.Nullable;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

/**
 * This event is published on the interlink as a result of a user "patching" a stage on an orca
 * instance that can't handle the partition for the given execution. Patching a stage occurs when,
 * for example, the user skips wait, skips execution window, passes manual judgement
 *
 * <p>The event is then handled by an orca instance (listening on interlink) whose partition matches
 * that of the execution. The resulting repository mutations of this event will then be peered by
 * the PeeringAgent
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Slf4j
public class PatchStageInterlinkEvent implements InterlinkEvent {
  final EventType eventType = PATCH;
  @Nullable String partition;
  @NonNull ExecutionType executionType;
  @NonNull String executionId;
  @NonNull String stageId;
  @NonNull String stageBody;

  @JsonIgnore @Nullable ObjectMapper mapper;

  public PatchStageInterlinkEvent(
      @NonNull ExecutionType executionType,
      @NonNull String executionId,
      @NonNull String stageId,
      @NonNull String stageBody) {
    this.executionType = executionType;
    this.executionId = executionId;
    this.stageId = stageId;
    this.stageBody = stageBody;
  }

  @Override
  public void applyTo(CompoundExecutionOperator executionOperator) {
    Preconditions.checkNotNull(mapper, "applyTo requires an ObjectMapper");

    try {
      StageExecution stageFromMessage = mapper.readValue(stageBody, StageExecutionImpl.class);

      executionOperator.updateStage(
          executionType,
          executionId,
          stageId,
          stageFromRepo -> {
            stageFromRepo.getContext().putAll(stageFromMessage.getContext());

            if (stageFromMessage.getLastModified() != null) {
              stageFromRepo.setLastModified(stageFromMessage.getLastModified());
            } else {
              log.warn(
                  "Unexpected state: stageFromMessage.getLastModified() is null, stageFromMessage={}",
                  stageFromMessage);
            }
          });
    } catch (JsonProcessingException e) {
      log.error("failed to parse stageBody {}", stageBody, e);
    }
  }

  @Override
  public InterlinkEvent withObjectMapper(ObjectMapper mapper) {
    this.mapper = mapper;
    return this;
  }
}
