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

import static com.netflix.spinnaker.orca.interlink.events.InterlinkEvent.EventType.DELETE;

import com.netflix.spinnaker.orca.api.pipeline.models.ExecutionType;
import com.netflix.spinnaker.orca.pipeline.CompoundExecutionOperator;
import javax.annotation.Nullable;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.NonNull;

/**
 * This event is published on the interlink as a result of a user DELETING an execution on an orca
 * instance that can't handle the partition for the given execution.
 *
 * <p>The event is then handled by an orca instance (listening on interlink) whose partition matches
 * that of the execution. The resulting repository mutations of this event will then be peered by
 * the PeeringAgent
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class DeleteInterlinkEvent implements InterlinkEvent {
  final EventType eventType = DELETE;
  @Nullable String partition;
  @NonNull ExecutionType executionType;
  @NonNull String executionId;

  public DeleteInterlinkEvent(@NonNull ExecutionType executionType, @NonNull String executionId) {
    this.executionType = executionType;
    this.executionId = executionId;
  }

  @Override
  public void applyTo(CompoundExecutionOperator executionOperator) {
    executionOperator.delete(executionType, executionId);
  }
}
