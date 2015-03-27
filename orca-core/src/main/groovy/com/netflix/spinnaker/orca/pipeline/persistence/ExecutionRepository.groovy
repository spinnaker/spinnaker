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

interface ExecutionRepository {
  void store(Orchestration orchestration)
  void store(Pipeline pipeline)

  void storeStage(OrchestrationStage stage)
  void storeStage(PipelineStage stage)

  Pipeline retrievePipeline(String id)
  void deletePipeline(String id)
  List<Pipeline> retrievePipelines()
  List<Pipeline> retrievePipelinesForApplication(String application)
  Orchestration retrieveOrchestration(String id)
  void deleteOrchestration(String id)
  List<Orchestration> retrieveOrchestrations()
  List<Orchestration> retrieveOrchestrationsForApplication(String application)
}
