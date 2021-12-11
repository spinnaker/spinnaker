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
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** The runtime execution state of a Pipeline. */
@Beta
public interface PipelineExecution {

  @Nonnull
  ExecutionType getType();

  String getId();

  void setId(String id);

  String getApplication();

  void setApplication(String application);

  String getName();

  void setName(String name);

  Long getBuildTime();

  void setBuildTime(Long buildTime);

  boolean isCanceled();

  void setCanceled(boolean canceled);

  String getCanceledBy();

  void setCanceledBy(String canceledBy);

  String getCancellationReason();

  void setCancellationReason(String cancellationReason);

  boolean isLimitConcurrent();

  void setLimitConcurrent(boolean limitConcurrent);

  int getMaxConcurrentExecutions();

  void setMaxConcurrentExecutions(int maxConcurrentExecutions);

  boolean isKeepWaitingPipelines();

  void setKeepWaitingPipelines(boolean keepWaitingPipelines);

  Map<String, Object> getContext();

  List<StageExecution> getStages();

  Long getStartTime();

  void setStartTime(Long startTime);

  Long getEndTime();

  void setEndTime(Long endTime);

  Long getStartTimeExpiry();

  void setStartTimeExpiry(Long startTimeExpiry);

  ExecutionStatus getStatus();

  void setStatus(ExecutionStatus status);

  AuthenticationDetails getAuthentication();

  void setAuthentication(AuthenticationDetails authentication);

  PausedDetails getPaused();

  void setPaused(PausedDetails paused);

  String getOrigin();

  void setOrigin(String origin);

  Trigger getTrigger();

  void setTrigger(Trigger trigger);

  String getDescription();

  void setDescription(String description);

  String getPipelineConfigId();

  void setPipelineConfigId(String pipelineConfigId);

  PipelineSource getSource();

  void setSource(PipelineSource source);

  List<Map<String, Object>> getNotifications();

  void setNotifications(List<Map<String, Object>> notifications);

  String getSpelEvaluator();

  void setSpelEvaluator(String spelEvaluator);

  Map<String, Object> getTemplateVariables();

  void setTemplateVariables(Map<String, Object> templateVariables);

  String getPartition();

  void setPartition(String partition);

  // -------

  StageExecution namedStage(String type);

  StageExecution stageById(String stageId);

  StageExecution stageByRef(String refId);

  /**
   * Based on the value of `status`, will also update synthetic fields like `canceled` and `endTime`
   */
  void updateStatus(ExecutionStatus status);

  class AuthenticationDetails {

    private String user;

    public @Nullable String getUser() {
      return user;
    }

    public void setUser(@Nullable String user) {
      this.user = user;
    }

    private Collection<String> allowedAccounts = emptySet();

    public Collection<String> getAllowedAccounts() {
      return allowedAccounts;
    }

    public void setAllowedAccounts(Collection<String> allowedAccounts) {
      this.allowedAccounts = Set.copyOf(allowedAccounts);
    }

    public AuthenticationDetails() {
      this(null, Collections.emptySet());
    }

    public AuthenticationDetails(String user, String... allowedAccounts) {
      this(user, Set.of(allowedAccounts));
    }

    public AuthenticationDetails(String user, Collection<String> allowedAccounts) {
      this.user = user;
      this.allowedAccounts =
          allowedAccounts == null ? Collections.emptySet() : Set.copyOf(allowedAccounts);
    }
  }

  class PausedDetails {
    String pausedBy;

    public @Nullable String getPausedBy() {
      return pausedBy;
    }

    public void setPausedBy(@Nullable String pausedBy) {
      this.pausedBy = pausedBy;
    }

    String resumedBy;

    public @Nullable String getResumedBy() {
      return resumedBy;
    }

    public void setResumedBy(@Nullable String resumedBy) {
      this.resumedBy = resumedBy;
    }

    Long pauseTime;

    public @Nullable Long getPauseTime() {
      return pauseTime;
    }

    public void setPauseTime(@Nullable Long pauseTime) {
      this.pauseTime = pauseTime;
    }

    Long resumeTime;

    public @Nullable Long getResumeTime() {
      return resumeTime;
    }

    public void setResumeTime(@Nullable Long resumeTime) {
      this.resumeTime = resumeTime;
    }

    public boolean isPaused() {
      return pauseTime != null && resumeTime == null;
    }

    public long getPausedMs() {
      return (pauseTime != null && resumeTime != null) ? resumeTime - pauseTime : 0;
    }
  }

  class PipelineSource {
    private String id;

    public @Nonnull String getId() {
      return id;
    }

    public void setId(@Nonnull String id) {
      this.id = id;
    }

    private String type;

    public @Nonnull String getType() {
      return type;
    }

    public void setType(@Nonnull String type) {
      this.type = type;
    }

    private String version;

    public @Nonnull String getVersion() {
      return version;
    }

    public void setVersion(@Nonnull String version) {
      this.version = version;
    }
  }
}
