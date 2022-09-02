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
package com.netflix.spinnaker.orca.api.pipeline.models;

import static java.util.Collections.emptySet;

import com.netflix.spinnaker.kork.annotations.Beta;
import com.netflix.spinnaker.orca.api.pipeline.SyntheticStageOwner;
import java.util.*;
import java.util.function.Predicate;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.NonNull;

/** The runtime execution state of a stage. */
@Beta
public interface StageExecution {

  @Nonnull
  String getId();

  void setId(@Nonnull String id);

  @Nullable
  String getRefId();

  void setRefId(@Nullable String refId);

  @Nonnull
  String getType();

  void setType(@Nonnull String type);

  @Nonnull
  String getName();

  void setName(@Nonnull String name);

  /** TODO(rz): Rename to getPipelineExecution */
  @Nonnull
  PipelineExecution getExecution();

  void setExecution(@Nonnull PipelineExecution execution);

  /** TODO(rz): Convert to Instant */
  @Nullable
  Long getStartTime();

  void setStartTime(@Nullable Long startTime);

  /** TODO(rz): Convert to Instant */
  @Nullable
  Long getEndTime();

  void setEndTime(@Nullable Long endTime);

  /** TODO(rz): Convert to Instant */
  @Nullable
  Long getStartTimeExpiry();

  void setStartTimeExpiry(@Nullable Long startTimeExpiry);

  @Nonnull
  ExecutionStatus getStatus();

  void setStatus(@Nonnull ExecutionStatus status);

  /** TODO(rz): Try to use StageContext instead? */
  @Nonnull
  Map<String, Object> getContext();

  void setContext(@Nonnull Map<String, Object> context);

  /** TODO(rz): getOutputs(Class)? */
  @Nonnull
  Map<String, Object> getOutputs();

  void setOutputs(@Nonnull Map<String, Object> outputs);

  @Nonnull
  List<TaskExecution> getTasks();

  void setTasks(@Nonnull List<TaskExecution> tasks);

  @Nullable
  SyntheticStageOwner getSyntheticStageOwner();

  void setSyntheticStageOwner(@Nullable SyntheticStageOwner syntheticStageOwner);

  @Nullable
  String getParentStageId();

  void setParentStageId(@Nullable String parentStageId);

  @Nonnull
  Collection<String> getRequisiteStageRefIds();

  void setRequisiteStageRefIds(@Nonnull Collection<String> requisiteStageRefIds);

  /** TODO(rz): Convert to Instant */
  @Nullable
  Long getScheduledTime();

  void setScheduledTime(@Nullable Long scheduledTime);

  @Nullable
  LastModifiedDetails getLastModified();

  void setLastModified(@Nullable LastModifiedDetails lastModified);

  @Nullable
  Map<String, String> getAdditionalMetricTags();

  void setAdditionalMetricTags(Map<String, String> additionalMetricTags);

  // ------------- InternalStageExecution?
  // A lot of these methods are used in a single place somewhere in Orca. I don't know why we
  // decided to put a bunch
  // of these methods on the class...?

  TaskExecution taskById(@Nonnull String taskId);

  @Nonnull
  List<StageExecution> ancestors();

  @Nonnull
  List<StageExecution> directAncestors();

  @Nullable
  StageExecution findAncestor(@Nonnull Predicate<StageExecution> predicate);

  @Nonnull
  List<StageExecution> allDownstreamStages();

  @Nonnull
  List<StageExecution> directChildren();

  @Nonnull
  <O> O mapTo(@Nonnull Class<O> type);

  @Nonnull
  <O> O mapTo(@Nullable String pointer, @Nonnull Class<O> type);

  @Nonnull
  <O> O decodeBase64(@Nullable String pointer, @Nonnull Class<O> type);

  void resolveStrategyParams();

  @Nullable
  StageExecution getParent();

  @Nonnull
  StageExecution getTopLevelStage();

  @Nonnull
  Optional<StageExecution> getParentWithTimeout();

  @Nonnull
  Optional<Long> getTimeout();

  boolean getAllowSiblingStagesToContinueOnFailure();

  void setAllowSiblingStagesToContinueOnFailure(boolean allowSiblingStagesToContinueOnFailure);

  boolean getContinuePipelineOnFailure();

  void setContinuePipelineOnFailure(boolean continuePipelineOnFailure);

  boolean isJoin();

  boolean isManualJudgmentType();

  boolean withPropagateAuthentication();

  void appendErrorMessage(String errorMessage);

  @Nonnull
  List<StageExecution> downstreamStages();

  @Data
  @NoArgsConstructor
  class LastModifiedDetails {
    @NonNull private String user;

    @NonNull private Collection<String> allowedAccounts = emptySet();

    /** TODO(rz): Convert to Instant */
    @NonNull private Long lastModifiedTime;

    public @Nonnull Collection<String> getAllowedAccounts() {
      return Set.copyOf(allowedAccounts);
    }

    public void setAllowedAccounts(@Nonnull Collection<String> allowedAccounts) {
      this.allowedAccounts = Set.copyOf(allowedAccounts);
    }
  }
}
