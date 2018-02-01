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

package com.netflix.spinnaker.orca.pipeline.model;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeName;
import com.netflix.spinnaker.kork.artifacts.model.Artifact;

@JsonTypeName("manual")
public class ManualTrigger extends Trigger {

  private final String correlationId;
  private final List<Map<String, Object>> notifications;

  @JsonCreator
  public ManualTrigger(
    @Nullable @JsonProperty("correlationId") String correlationId,
    @Nonnull @JsonProperty("user") String user,
    @Nonnull @JsonProperty("parameters") Map<String, Object> parameters,
    @Nonnull @JsonProperty("notifications") List<Map<String, Object>> notifications,
    @Nullable @JsonProperty("artifacts") List<Artifact> artifacts
  ) {
    super(user, parameters, artifacts);
    this.correlationId = correlationId;
    this.notifications = notifications;
  }

  public @Nonnull List<Map<String, Object>> getNotifications() {
    return notifications;
  }

  public @Nullable String getCorrelationId() {
    return correlationId;
  }

  @Override public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    if (!super.equals(o)) return false;
    ManualTrigger trigger = (ManualTrigger) o;
    return Objects.equals(correlationId, trigger.correlationId) &&
      Objects.equals(notifications, trigger.notifications);
  }

  @Override public int hashCode() {
    return Objects.hash(super.hashCode(), correlationId, notifications);
  }

  @Override public String toString() {
    return "ManualTrigger{" +
      super.toString() +
      ", correlationId='" + correlationId + '\'' +
      ", notifications=" + notifications +
      '}';
  }
}
