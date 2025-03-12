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

import static com.netflix.spinnaker.orca.interlink.events.InterlinkEvent.EventType.RESTART;

import com.netflix.spinnaker.orca.api.pipeline.models.ExecutionType;
import com.netflix.spinnaker.orca.pipeline.CompoundExecutionOperator;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * This event is published on the interlink as a result of a user RESTARTING a stage on an orca
 * instance that can't handle the partition for the given execution.
 *
 * <p>The event is then handled by an orca instance (listening on interlink) whose partition matches
 * that of the execution. The resulting repository mutations of this event will then be peered by
 * the PeeringAgent
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class RestartStageInterlinkEvent implements InterlinkEvent {
  final EventType eventType = RESTART;
  @Nullable String partition;
  @Nonnull ExecutionType executionType;
  @Nonnull String executionId;
  @Nonnull String stageId;

  public RestartStageInterlinkEvent(
      @Nonnull ExecutionType executionType, @Nonnull String executionId, @Nonnull String stageId) {
    // for the moment, only ExecutionType.PIPELINE can be restarted
    // but since we are defining the protocol on the wire here, let's be a bit future proof and
    // accept potentially different execution types
    this.executionType = executionType;
    this.executionId = executionId;
    this.stageId = stageId;
  }

  @Override
  public void applyTo(CompoundExecutionOperator executionOperator) {
    executionOperator.restartStage(executionId, stageId);
  }
}
