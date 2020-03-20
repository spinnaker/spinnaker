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

import static com.netflix.spinnaker.orca.interlink.events.InterlinkEvent.EventType.START_PENDING;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.netflix.spinnaker.orca.api.pipeline.models.ExecutionType;
import com.netflix.spinnaker.orca.pipeline.CompoundExecutionOperator;
import javax.annotation.Nullable;
import javax.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.NonNull;

/**
 * This event is published on the interlink as a result of an orca instance completing an execution.
 * NOTE: unlike other interlink events, this one is not published as a result of a user action.
 *
 * <p>When an execution completes, orca checks to see if there are any pending executions to be
 * kicked off. However, the pending executions aren't peered and in a peered configuration a peer
 * might have an execution pending that needs to be started. If, upon completion of an execution,
 * current orca doesn't see any pending executions it will publish this event so that its peers can
 * start any pending executions if they have any.
 *
 * <p>The event is then handled by an orca instance (listening on interlink) whose partition matches
 * that of the execution. The resulting repository mutations of this event will then be peered by
 * the PeeringAgent
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class StartPendingInterlinkEvent implements InterlinkEvent {
  final EventType eventType = START_PENDING;
  @Nullable String partition;
  @NonNull ExecutionType executionType;
  @NonNull String executionId;
  @NonNull String pipelineConfigId;
  boolean purgeQueue;

  public StartPendingInterlinkEvent(
      @NonNull ExecutionType executionType, @NonNull String pipelineConfigId, boolean purgeQueue) {
    this.executionType = executionType;
    this.executionId = "NO_EXECUTION_ID";
    this.pipelineConfigId = pipelineConfigId;
    this.purgeQueue = purgeQueue;
  }

  @Override
  public void applyTo(CompoundExecutionOperator executionOperator) {
    executionOperator.startPending(pipelineConfigId, purgeQueue);
  }

  @JsonIgnore
  @NotNull
  @Override
  public String getFingerprint() {
    return getEventType() + ":" + getExecutionType() + ":" + pipelineConfigId;
  }
}
