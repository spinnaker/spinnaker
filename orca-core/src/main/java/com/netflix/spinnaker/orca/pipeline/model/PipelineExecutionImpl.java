/*
 * Copyright 2014 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.orca.pipeline.model;

import static com.netflix.spinnaker.orca.api.pipeline.models.ExecutionStatus.NOT_STARTED;
import static com.netflix.spinnaker.orca.api.pipeline.models.ExecutionStatus.RUNNING;
import static com.netflix.spinnaker.orca.api.pipeline.models.ExecutionType.ORCHESTRATION;
import static com.netflix.spinnaker.orca.api.pipeline.models.ExecutionType.PIPELINE;
import static java.lang.System.currentTimeMillis;
import static java.util.stream.Collectors.toMap;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.netflix.spinnaker.orca.api.pipeline.models.ExecutionStatus;
import com.netflix.spinnaker.orca.api.pipeline.models.ExecutionType;
import com.netflix.spinnaker.orca.api.pipeline.models.PipelineExecution;
import com.netflix.spinnaker.orca.api.pipeline.models.StageExecution;
import com.netflix.spinnaker.orca.api.pipeline.models.Trigger;
import com.netflix.spinnaker.security.AuthenticatedRequest;
import de.huxhorn.sulky.ulid.ULID;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public class PipelineExecutionImpl implements PipelineExecution, Serializable {

  public static final DefaultTrigger NO_TRIGGER = new DefaultTrigger("none");
  private static final ULID ID_GENERATOR = new ULID();

  public PipelineExecutionImpl(ExecutionType type, String application) {
    this(type, ID_GENERATOR.nextULID(), application);
  }

  @JsonCreator
  public PipelineExecutionImpl(
      @JsonProperty("type") ExecutionType type,
      @JsonProperty("id") String id,
      @JsonProperty("application") String application) {
    this.type = type;
    this.id = id;
    this.application = application;
  }

  private final ExecutionType type;

  public @Nonnull ExecutionType getType() {
    return type;
  }

  private String id;

  public @Nonnull String getId() {
    return id;
  }

  public void setId(@Nonnull String id) {
    this.id = id;
  }

  private String application;

  public @Nonnull String getApplication() {
    return application;
  }

  public void setApplication(@Nonnull String application) {
    this.application = application;
  }

  private String name;

  public @Nullable String getName() {
    return name;
  }

  public void setName(@Nullable String name) {
    this.name = name;
  }

  private Long buildTime;

  public @Nullable Long getBuildTime() {
    return buildTime;
  }

  public void setBuildTime(@Nullable Long buildTime) {
    this.buildTime = buildTime;
  }

  private boolean canceled;

  public boolean isCanceled() {
    return canceled;
  }

  public void setCanceled(boolean canceled) {
    this.canceled = canceled;
  }

  private String canceledBy;

  public @Nullable String getCanceledBy() {
    return canceledBy;
  }

  public void setCanceledBy(@Nullable String canceledBy) {
    this.canceledBy = canceledBy;
  }

  private String cancellationReason;

  public @Nullable String getCancellationReason() {
    return cancellationReason;
  }

  public void setCancellationReason(@Nullable String cancellationReason) {
    this.cancellationReason = cancellationReason;
  }

  private boolean limitConcurrent = false;

  public boolean isLimitConcurrent() {
    return limitConcurrent;
  }

  public void setLimitConcurrent(boolean limitConcurrent) {
    this.limitConcurrent = limitConcurrent;
  }

  private int maxConcurrentExecutions = 0;

  public int getMaxConcurrentExecutions() {
    return maxConcurrentExecutions;
  }

  public void setMaxConcurrentExecutions(int maxConcurrentExecutions) {
    this.maxConcurrentExecutions = maxConcurrentExecutions;
  }

  private boolean keepWaitingPipelines = false;

  public boolean isKeepWaitingPipelines() {
    return keepWaitingPipelines;
  }

  public void setKeepWaitingPipelines(boolean keepWaitingPipelines) {
    this.keepWaitingPipelines = keepWaitingPipelines;
  }

  @JsonIgnore
  @SuppressWarnings({"unchecked", "rawtypes"})
  public @Nonnull Map<String, Object> getContext() {
    return StageExecutionImpl.topologicalSort(stages)
        .map(StageExecution::getOutputs)
        .map(Map::entrySet)
        .flatMap(Collection::stream)
        .collect(
            toMap(
                Map.Entry::getKey,
                Map.Entry::getValue,
                (o, o2) -> {
                  if (o instanceof Collection && o2 instanceof Collection) {
                    return Stream.concat(((Collection) o).stream(), ((Collection) o2).stream())
                        .distinct()
                        .collect(Collectors.toList());
                  }
                  return o2;
                }));
  }

  private final List<StageExecution> stages = new ArrayList<>();

  /**
   * Gets the stages of this execution. Does not serialize the child Execution object from stages.
   * The child Execution object in Stage is a @JsonBackReference.
   */
  @JsonIgnoreProperties(value = "execution")
  public @Nonnull List<StageExecution> getStages() {
    return stages;
  }

  private Long startTime;

  public @Nullable Long getStartTime() {
    return startTime;
  }

  public void setStartTime(@Nullable Long startTime) {
    this.startTime = startTime;
  }

  private Long endTime;

  public @Nullable Long getEndTime() {
    return endTime;
  }

  public void setEndTime(@Nullable Long endTime) {
    this.endTime = endTime;
  }

  /**
   * Gets the start expiry timestamp for this execution. If the execution has not started before
   * this timestamp, the execution will immediately terminate.
   */
  private Long startTimeExpiry;

  public @Nullable Long getStartTimeExpiry() {
    return startTimeExpiry;
  }

  public void setStartTimeExpiry(@Nullable Long startTimeExpiry) {
    this.startTimeExpiry = startTimeExpiry;
  }

  private ExecutionStatus status = NOT_STARTED;

  public @Nonnull ExecutionStatus getStatus() {
    return status;
  }

  public void setStatus(@Nonnull ExecutionStatus status) {
    this.status = status;
  }

  @Override
  public void updateStatus(@Nonnull ExecutionStatus status) {
    this.status = status;
    if (status == RUNNING) {
      canceled = false;
      startTime = currentTimeMillis();
    } else if (status.isComplete() && startTime != null) {
      endTime = currentTimeMillis();
    }
  }

  private AuthenticationDetails authentication;

  public @Nullable AuthenticationDetails getAuthentication() {
    return authentication;
  }

  public void setAuthentication(@Nullable AuthenticationDetails authentication) {
    this.authentication = authentication;
  }

  private PausedDetails paused;

  public @Nullable PausedDetails getPaused() {
    return paused;
  }

  public void setPaused(@Nullable PausedDetails paused) {
    this.paused = paused;
  }

  private String origin;

  public @Nullable String getOrigin() {
    return origin;
  }

  public void setOrigin(@Nullable String origin) {
    this.origin = origin;
  }

  private Trigger trigger = NO_TRIGGER;

  public @Nonnull Trigger getTrigger() {
    return trigger;
  }

  public void setTrigger(@Nonnull Trigger trigger) {
    this.trigger = trigger;
  }

  private String description;

  public @Nullable String getDescription() {
    return description;
  }

  public void setDescription(@Nullable String description) {
    this.description = description;
  }

  private String pipelineConfigId;

  public @Nullable String getPipelineConfigId() {
    return pipelineConfigId;
  }

  public void setPipelineConfigId(@Nullable String pipelineConfigId) {
    this.pipelineConfigId = pipelineConfigId;
  }

  private PipelineSource source;

  public @Nullable PipelineSource getSource() {
    return source;
  }

  public void setSource(@Nullable PipelineSource source) {
    this.source = source;
  }

  private List<Map<String, Object>> notifications = new ArrayList<>();

  public @Nonnull List<Map<String, Object>> getNotifications() {
    return notifications;
  }

  public void setNotifications(List<Map<String, Object>> notifications) {
    this.notifications = notifications;
  }

  private final Map<String, Object> initialConfig = new HashMap<>();

  public @Nonnull Map<String, Object> getInitialConfig() {
    return initialConfig;
  }

  private final List<SystemNotification> systemNotifications = new ArrayList<>();

  public @Nonnull List<SystemNotification> getSystemNotifications() {
    return systemNotifications;
  }

  private String spelEvaluator;

  public @Nullable String getSpelEvaluator() {
    return spelEvaluator;
  }

  public void setSpelEvaluator(@Nullable String spelEvaluatorVersion) {
    this.spelEvaluator = spelEvaluatorVersion;
  }

  private Map<String, Object> templateVariables = null;

  public @Nullable Map<String, Object> getTemplateVariables() {
    return templateVariables;
  }

  public void setTemplateVariables(@Nullable Map<String, Object> templateVariables) {
    this.templateVariables = templateVariables;
  }

  public String partition = null;

  public void setPartition(@Nullable String partition) {
    this.partition = partition;
  }

  public @Nullable String getPartition() {
    return this.partition;
  }

  @Nullable
  public StageExecution namedStage(String type) {
    return stages.stream().filter(it -> it.getType().equals(type)).findFirst().orElse(null);
  }

  @Nonnull
  public StageExecution stageById(String stageId) {
    return stages.stream()
        .filter(it -> it.getId().equals(stageId))
        .findFirst()
        .orElseThrow(
            () ->
                new IllegalArgumentException(
                    String.format("No stage with id %s exists on execution %s", stageId, id)));
  }

  @Nonnull
  public StageExecution stageByRef(String refId) {
    return stages.stream()
        .filter(it -> refId.equals(it.getRefId()))
        .findFirst()
        .orElseThrow(
            () ->
                new IllegalArgumentException(
                    String.format("No stage with refId %s exists", refId)));
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    PipelineExecutionImpl execution = (PipelineExecutionImpl) o;
    return Objects.equals(id, execution.id);
  }

  @Override
  public int hashCode() {
    return Objects.hash(id);
  }

  @Deprecated
  public static PipelineExecutionImpl newOrchestration(String application) {
    return new PipelineExecutionImpl(ORCHESTRATION, application);
  }

  @Deprecated
  public static PipelineExecutionImpl newPipeline(String application) {
    return new PipelineExecutionImpl(PIPELINE, application);
  }

  public static class AuthenticationHelper {
    public static Optional<AuthenticationDetails> build() {
      Optional<String> spinnakerUserOptional = AuthenticatedRequest.getSpinnakerUser();
      Optional<String> spinnakerAccountsOptional = AuthenticatedRequest.getSpinnakerAccounts();
      if (spinnakerUserOptional.isPresent() || spinnakerAccountsOptional.isPresent()) {
        return Optional.of(
            new AuthenticationDetails(
                spinnakerUserOptional.orElse(null),
                spinnakerAccountsOptional.map(s -> s.split(",")).orElse(new String[0])));
      }

      return Optional.empty();
    }
  }
}
