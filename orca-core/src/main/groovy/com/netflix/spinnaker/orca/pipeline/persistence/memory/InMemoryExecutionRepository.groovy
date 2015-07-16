package com.netflix.spinnaker.orca.pipeline.persistence.memory

import com.netflix.spinnaker.orca.pipeline.model.Orchestration
import com.netflix.spinnaker.orca.pipeline.model.OrchestrationStage
import com.netflix.spinnaker.orca.pipeline.model.Pipeline
import com.netflix.spinnaker.orca.pipeline.model.PipelineStage
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionNotFoundException
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository
import groovy.transform.CompileStatic
import rx.Observable

@CompileStatic
class InMemoryExecutionRepository implements ExecutionRepository {

  private final Map<String, Orchestration> orchestrations = [:]
  private final Map<String, Pipeline> pipelines = [:]

  @Override
  void store(Orchestration orchestration) {
    if (!orchestration.id) {
      orchestration.id = UUID.randomUUID().toString()
    }
    orchestrations[orchestration.id] = orchestration
  }

  @Override
  void store(Pipeline pipeline) {
    if (!pipeline.id) {
      pipeline.id = UUID.randomUUID().toString()
    }
    pipelines[pipeline.id] = pipeline
  }

  @Override
  void storeStage(OrchestrationStage stage) {

  }

  @Override
  void storeStage(PipelineStage stage) {

  }

  @Override
  Pipeline retrievePipeline(String id) {
    if (pipelines.containsKey(id)) {
      pipelines[id]
    } else {
      throw new ExecutionNotFoundException("$id not found")
    }
  }

  @Override
  void deletePipeline(String id) {
    pipelines.remove(id)
  }

  @Override
  Observable<Pipeline> retrievePipelines() {
    Observable.from(pipelines.values())
  }

  @Override
  Observable<Pipeline> retrievePipelinesForApplication(String application) {
    Observable.from(pipelines.values().findAll { it.application == application })
  }

  @Override
  Orchestration retrieveOrchestration(String id) {
    if (orchestrations.containsKey(id)) {
      orchestrations[id]
    } else {
      throw new ExecutionNotFoundException("$id not found")
    }
  }

  @Override
  void deleteOrchestration(String id) {
    orchestrations.remove(id)
  }

  @Override
  Observable<Orchestration> retrieveOrchestrations() {
    Observable.from(orchestrations.values())
  }

  @Override
  Observable<Orchestration> retrieveOrchestrationsForApplication(String application) {
    Observable.from(orchestrations.values().findAll { it.application == application })
  }

  void clear() {
    orchestrations.clear()
    pipelines.clear()
  }
}
