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

import static com.netflix.spinnaker.orca.interlink.events.InterlinkEvent.EventType.RESUME;

import com.netflix.spinnaker.orca.pipeline.model.Execution;
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository;
import javax.annotation.Nullable;
import lombok.*;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ResumeInterlinkEvent implements InterlinkEvent {
  final EventType eventType = RESUME;
  @Nullable String partition;
  @NonNull Execution.ExecutionType executionType;
  @NonNull String executionId;
  @Nullable String user;
  @NonNull Boolean ignoreCurrentStatus;

  public ResumeInterlinkEvent(
      @NonNull Execution.ExecutionType executionType,
      @NonNull String executionId,
      @Nullable String user,
      @NonNull Boolean ignoreCurrentStatus) {
    this.executionType = executionType;
    this.executionId = executionId;
    this.user = user;
    this.ignoreCurrentStatus = ignoreCurrentStatus;
  }

  @Override
  public void applyTo(ExecutionRepository executionRepository) {
    executionRepository.resume(executionType, executionId, user, ignoreCurrentStatus);
  }
}
