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
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeName;
import com.netflix.spinnaker.kork.artifacts.model.Artifact;

/**
 * The trigger used when a pipeline is triggered by another pipeline completing.
 */
@JsonTypeName("pipeline")
public final class PipelineTrigger extends Trigger {

  @JsonCreator
  public PipelineTrigger(
    @Nonnull @JsonProperty("parentExecution") Execution parentExecution,
    @Nullable @JsonProperty("parentPipelineStageId")
      String parentPipelineStageId,
    @JsonProperty("user") @Nullable String user,
    @JsonProperty("parameters") @Nullable Map<String, Object> parameters,
    @JsonProperty("artifacts") @Nullable List<Artifact> artifacts
    ) {
    super(user, parameters, artifacts);
    this.parentExecution = parentExecution;
    this.parentPipelineStageId = parentPipelineStageId;
  }

  public PipelineTrigger(Execution parentExecution, Map<String, Object> parameters) {
    this(parentExecution, null, null, parameters, null);
  }

  private final Execution parentExecution;
  private final String parentPipelineStageId;

  public @Nonnull Execution getParentExecution() {
    return parentExecution;
  }

  @JsonIgnore
  public @Nullable String getParentPipelineId() {
    return parentExecution.getPipelineConfigId();
  }

  public String getParentPipelineStageId() {
    return parentPipelineStageId;
  }

  @Override public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    if (!super.equals(o)) return false;
    PipelineTrigger that = (PipelineTrigger) o;
    return Objects.equals(parentExecution.getId(), that.parentExecution.getId()) &&
      Objects.equals(parentPipelineStageId, that.parentPipelineStageId);
  }

  @Override public int hashCode() {
    return Objects.hash(super.hashCode(), parentExecution.getId(), parentPipelineStageId);
  }

  @Override public String toString() {
    return "PipelineTrigger{" +
      super.toString() +
      ", parentExecution=" + parentExecution.getId() +
      ", parentPipelineStageId='" + parentPipelineStageId + '\'' +
      '}';
  }
}
