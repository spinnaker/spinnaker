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

package com.netflix.spinnaker.orca.pipeline.persistence

import com.netflix.spinnaker.orca.pipeline.model.Orchestration
import com.netflix.spinnaker.orca.pipeline.model.OrchestrationStage
import com.netflix.spinnaker.orca.pipeline.model.Pipeline
import com.netflix.spinnaker.orca.pipeline.model.PipelineStage
import groovy.transform.CompileStatic
import org.springframework.beans.factory.annotation.Autowired

@CompileStatic
class DefaultExecutionRepository implements ExecutionRepository {

  private final ExecutionStore<Orchestration> orchestrationStore
  private final ExecutionStore<Pipeline> pipelineStore

  @Autowired
  DefaultExecutionRepository(ExecutionStore<Orchestration> orchestrationStore, ExecutionStore<Pipeline> pipelineStore) {
    this.orchestrationStore = orchestrationStore
    this.pipelineStore = pipelineStore
  }

  @Override
  void store(Orchestration orchestration) {
    this.orchestrationStore.store(orchestration)
  }

  @Override
  void store(Pipeline pipeline) {
    this.pipelineStore.store(pipeline)
  }

  @Override
  void storeStage(PipelineStage stage) {
    this.pipelineStore.storeStage(stage)
  }

  @Override
  void storeStage(OrchestrationStage stage) {
    this.orchestrationStore.storeStage(stage)
  }

  @Override
  Pipeline retrievePipeline(String id) {
    this.pipelineStore.retrieve(id)
  }

  @Override
  void deletePipeline(String id) {
    this.pipelineStore.delete(id)
  }

  @Override
  List<Pipeline> retrievePipelines() {
    this.pipelineStore.all()
  }

  @Override
  List<Pipeline> retrievePipelinesForApplication(String application) {
    this.pipelineStore.allForApplication(application)
  }

  @Override
  Orchestration retrieveOrchestration(String id) {
    this.orchestrationStore.retrieve(id)
  }

  @Override
  void deleteOrchestration(String id) {
    this.orchestrationStore.delete(id)
  }

  @Override
  List<Orchestration> retrieveOrchestrations() {
    this.orchestrationStore.all()
  }

  @Override
  List<Orchestration> retrieveOrchestrationsForApplication(String application) {
    this.orchestrationStore.allForApplication(application)
  }
}
