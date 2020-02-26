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

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.netflix.spinnaker.orca.pipeline.CompoundExecutionOperator;
import com.netflix.spinnaker.orca.pipeline.model.Execution;
import javax.validation.constraints.NotNull;

/** Common interface for all interlink event messages */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "eventType")
@JsonSubTypes({
  @JsonSubTypes.Type(value = CancelInterlinkEvent.class, name = "CANCEL"),
  @JsonSubTypes.Type(value = PauseInterlinkEvent.class, name = "PAUSE"),
  @JsonSubTypes.Type(value = ResumeInterlinkEvent.class, name = "RESUME"),
  @JsonSubTypes.Type(value = DeleteInterlinkEvent.class, name = "DELETE")
})
public interface InterlinkEvent {
  enum EventType {
    CANCEL,
    PAUSE,
    DELETE,
    RESUME,
  }

  @JsonIgnore
  EventType getEventType();

  Execution.ExecutionType getExecutionType();

  String getExecutionId();

  String getPartition();

  void setPartition(String partition);

  default InterlinkEvent withPartition(String partition) {
    setPartition(partition);
    return this;
  }

  void applyTo(CompoundExecutionOperator executionOperator);

  @JsonIgnore
  @NotNull
  default String getFingerprint() {
    return getEventType() + ":" + getExecutionType() + ":" + getExecutionId();
  }
}
