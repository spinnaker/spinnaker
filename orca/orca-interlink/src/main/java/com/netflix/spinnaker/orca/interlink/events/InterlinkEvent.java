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
import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.orca.api.pipeline.models.ExecutionType;
import com.netflix.spinnaker.orca.pipeline.CompoundExecutionOperator;
import jakarta.validation.constraints.NotNull;

/**
 * Common interface for interlink events
 *
 * <p>Interlink events are published to communicate across orca peers. When an orca encounters an
 * operation it can't handle due its partition not matching that of the execution/request it will
 * generally broadcast an interlink event which will be handled by an orca instance (listening on
 * interlink) whose partition matches that of the execution.
 *
 * <p>Interlink events don't have a delivery guarantee, since most such events are triggered by a
 * user action this lack of guarantee is not a problem.
 *
 * <p>The resulting repository mutations of interlink events will then be peered by the PeeringAgent
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "eventType")
@JsonSubTypes({
  @JsonSubTypes.Type(value = CancelInterlinkEvent.class, name = "CANCEL"),
  @JsonSubTypes.Type(value = PauseInterlinkEvent.class, name = "PAUSE"),
  @JsonSubTypes.Type(value = ResumeInterlinkEvent.class, name = "RESUME"),
  @JsonSubTypes.Type(value = DeleteInterlinkEvent.class, name = "DELETE"),
  @JsonSubTypes.Type(value = PatchStageInterlinkEvent.class, name = "PATCH"),
  @JsonSubTypes.Type(value = RestartStageInterlinkEvent.class, name = "RESTART")
})
public interface InterlinkEvent {
  enum EventType {
    CANCEL,
    PAUSE,
    DELETE,
    RESUME,
    PATCH,
    RESTART
  }

  @JsonIgnore
  EventType getEventType();

  ExecutionType getExecutionType();

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

  default InterlinkEvent withObjectMapper(ObjectMapper objectMapper) {
    return this;
  }
}
