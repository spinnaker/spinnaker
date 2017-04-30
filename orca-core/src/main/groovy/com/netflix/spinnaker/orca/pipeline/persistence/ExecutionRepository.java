package com.netflix.spinnaker.orca.pipeline.persistence;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;
import com.netflix.spinnaker.orca.ExecutionStatus;
import com.netflix.spinnaker.orca.pipeline.model.Execution;
import com.netflix.spinnaker.orca.pipeline.model.Orchestration;
import com.netflix.spinnaker.orca.pipeline.model.Pipeline;
import com.netflix.spinnaker.orca.pipeline.model.Stage;
import rx.Observable;

public interface ExecutionRepository {
  void store(Orchestration orchestration);

  void store(Pipeline pipeline);

  void storeExecutionContext(String id, Map<String, Object> context);

  void storeStage(Stage<? extends Execution> stage);

  void removeStage(Execution execution, String stageId);

  void addStage(Stage stage);

  void cancel(String id);

  void cancel(String id, String user, String reason);

  void pause(String id, String user);

  void resume(String id, String user);

  void resume(String id, String user, boolean ignoreCurrentStatus);

  boolean isCanceled(String id);

  void updateStatus(String id, ExecutionStatus status);

  Pipeline retrievePipeline(String id);

  void deletePipeline(String id);

  Observable<Pipeline> retrievePipelines();

  Observable<Pipeline> retrievePipelinesForApplication(String application);

  Observable<Pipeline> retrievePipelinesForPipelineConfigId(String pipelineConfigId, ExecutionCriteria criteria);

  Orchestration retrieveOrchestration(String id);

  void deleteOrchestration(String id);

  Observable<Orchestration> retrieveOrchestrations();

  Observable<Orchestration> retrieveOrchestrationsForApplication(String application, ExecutionCriteria criteria);

  class ExecutionCriteria {
    public int getLimit() {
      return limit;
    }

    public void setLimit(int limit) {
      this.limit = limit;
    }

    public Collection<String> getStatuses() {
      return statuses;
    }

    public void setStatuses(Collection<String> statuses) {
      this.statuses = statuses;
    }

    private int limit;
    private Collection<String> statuses = new ArrayList();
  }
}
