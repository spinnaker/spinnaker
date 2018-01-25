/*
 * Copyright 2017 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.netflix.spinnaker.orca.pipeline.persistence;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import com.netflix.spinnaker.orca.ExecutionStatus;
import com.netflix.spinnaker.orca.pipeline.model.Execution;
import com.netflix.spinnaker.orca.pipeline.model.Execution.ExecutionType;
import com.netflix.spinnaker.orca.pipeline.model.Stage;
import rx.Observable;

public interface ExecutionRepository {
  void store(@Nonnull Execution orchestration);

  void storeStage(@Nonnull Stage stage);

  void updateStageContext(@Nonnull Stage stage);

  void removeStage(@Nonnull Execution execution, @Nonnull String stageId);

  void addStage(@Nonnull Stage stage);

  void cancel(@Nonnull String id);

  void cancel(
    @Nonnull String id, @Nullable String user, @Nullable String reason);

  void pause(@Nonnull String id, @Nullable String user);

  void resume(@Nonnull String id, @Nullable String user);

  void resume(
    @Nonnull String id, @Nullable String user, boolean ignoreCurrentStatus);

  boolean isCanceled(@Nonnull String id);

  void updateStatus(@Nonnull String id, @Nonnull ExecutionStatus status);

  @Nonnull Execution retrieve(
    @Nonnull ExecutionType type,
    @Nonnull String id) throws ExecutionNotFoundException;

  void delete(
    @Nonnull ExecutionType type,
    @Nonnull String id
  );

  @Nonnull Observable<Execution> retrieve(ExecutionType type);

  @Nonnull Observable<Execution> retrievePipelinesForApplication(
    @Nonnull String application);

  @Nonnull Observable<Execution> retrievePipelinesForPipelineConfigId(
    @Nonnull String pipelineConfigId, @Nonnull ExecutionCriteria criteria);

  @Nonnull Observable<Execution> retrieveOrchestrationsForApplication(
    @Nonnull String application, @Nonnull ExecutionCriteria criteria);

  @Nonnull Execution retrieveOrchestrationForCorrelationId(
    @Nonnull String correlationId) throws ExecutionNotFoundException;

  class ExecutionCriteria {
    public int getLimit() {
      return limit;
    }

    public ExecutionCriteria setLimit(int limit) {
      this.limit = limit;
      return this;
    }

    public Collection<String> getStatuses() {
      return statuses;
    }

    public ExecutionCriteria setStatuses(Collection<String> statuses) {
      this.statuses = statuses;
      return this;
    }

    private int limit;
    private Collection<String> statuses = new ArrayList<>();
  }
}
