package com.netflix.spinnaker.orca.pipeline.persistence.memory

import com.netflix.spinnaker.orca.pipeline.model.*
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
      def pipeline = pipelines[id]
      sortStages(pipeline)
      return pipeline
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
      def orchestration = orchestrations[id]
      sortStages(orchestration)
      return orchestration
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

  private <T extends Execution> void sortStages(T execution) {
    def reorderedStages = []
    execution.stages.findAll { it.parentStageId == null }.each { Stage<T> parentStage ->
      reorderedStages << parentStage

      def children = new LinkedList<Stage<T>>(execution.stages.findAll { it.parentStageId == parentStage.id })
      while (!children.isEmpty()) {
        def child = children.remove(0)
        children.addAll(0, execution.stages.findAll { it.parentStageId == child.id })
        reorderedStages << child
      }
    }
    execution.stages = reorderedStages
  }
}
