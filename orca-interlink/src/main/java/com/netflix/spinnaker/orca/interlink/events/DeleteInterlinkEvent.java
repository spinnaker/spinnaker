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

import com.netflix.spinnaker.orca.pipeline.model.Execution;
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository;
import javax.annotation.Nullable;
import lombok.*;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class DeleteInterlinkEvent implements InterlinkEvent {
  final EventType eventType = DELETE;
  @Nullable String partition;
  @NonNull Execution.ExecutionType executionType;
  @NonNull String executionId;

  public DeleteInterlinkEvent(
      @NonNull Execution.ExecutionType executionType, @NonNull String executionId) {
    this.executionType = executionType;
    this.executionId = executionId;
  }

  @Override
  public void applyTo(ExecutionRepository executionRepository) {
    executionRepository.delete(executionType, executionId);
  }
}
