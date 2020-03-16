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
package com.netflix.spinnaker.orca.jackson.mixin;

import com.fasterxml.jackson.annotation.JsonBackReference;
import com.fasterxml.jackson.annotation.JsonIdentityInfo;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.ObjectIdGenerators;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.netflix.spinnaker.orca.api.pipeline.models.PipelineExecution;
import com.netflix.spinnaker.orca.api.pipeline.models.StageExecution;
import com.netflix.spinnaker.orca.pipeline.model.support.RequisiteStageRefIdDeserializer;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import javax.annotation.Nonnull;

@JsonIdentityInfo(generator = ObjectIdGenerators.PropertyGenerator.class, property = "id")
public interface StageExecutionMixin {

  @JsonBackReference
  PipelineExecution getExecution();

  @JsonDeserialize(using = RequisiteStageRefIdDeserializer.class)
  void setRequisiteStageRefIds(@Nonnull Collection<String> requisiteStageRefIds);

  @JsonIgnore
  StageExecution getParent();

  @JsonIgnore
  StageExecution getTopLevelStage();

  @JsonIgnore
  Optional<StageExecution> getParentWithTimeout();

  @JsonIgnore
  Optional<Long> getTimeout();

  @JsonIgnore
  boolean getAllowSiblingStagesToContinueOnFailure();

  @JsonIgnore
  void setAllowSiblingStagesToContinueOnFailure(boolean allowSiblingStagesToContinueOnFailure);

  @JsonIgnore
  boolean getContinuePipelineOnFailure();

  @JsonIgnore
  void setContinuePipelineOnFailure(boolean continuePipelineOnFailure);

  @JsonIgnore
  boolean isJoin();

  @JsonIgnore
  List<StageExecution> downstreamStages();

  @JsonIgnore
  boolean getHasSucceeded();

  @JsonIgnore
  boolean getHasFailed();
}
