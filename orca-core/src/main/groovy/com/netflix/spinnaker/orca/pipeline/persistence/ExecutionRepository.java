package com.netflix.spinnaker.orca.pipeline.persistence;

import com.netflix.spinnaker.orca.ExecutionStatus;
import com.netflix.spinnaker.orca.pipeline.model.Execution;
import com.netflix.spinnaker.orca.pipeline.model.Orchestration;
import com.netflix.spinnaker.orca.pipeline.model.Pipeline;
import com.netflix.spinnaker.orca.pipeline.model.Stage;
import rx.Observable;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;

public interface ExecutionRepository {
  void store(@Nonnull Orchestration orchestration);

  void store(@Nonnull Pipeline pipeline);

  void storeExecutionContext(
    @Nonnull String id, @Nonnull Map<String, Object> context);

  void storeStage(@Nonnull Stage<? extends Execution> stage);

  void updateStageContext(@Nonnull Stage<? extends Execution> stage);

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

  @Nonnull Pipeline retrievePipeline(
    @Nonnull String id) throws ExecutionNotFoundException;

  void deletePipeline(@Nonnull String id);

  @Nonnull Observable<Pipeline> retrievePipelines();

  @Nonnull Observable<Pipeline> retrievePipelinesForApplication(
    @Nonnull String application);

  @Nonnull Observable<Pipeline> retrievePipelinesForPipelineConfigId(
    @Nonnull String pipelineConfigId, @Nonnull ExecutionCriteria criteria);

  @Nonnull Orchestration retrieveOrchestration(
    @Nonnull String id) throws ExecutionNotFoundException;

  void deleteOrchestration(@Nonnull String id);

  @Nonnull Observable<Orchestration> retrieveOrchestrations();

  @Nonnull Observable<Orchestration> retrieveOrchestrationsForApplication(
    @Nonnull String application, @Nonnull ExecutionCriteria criteria);

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
