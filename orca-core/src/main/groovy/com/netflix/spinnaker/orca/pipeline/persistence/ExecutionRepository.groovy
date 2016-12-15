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

import com.netflix.spinnaker.orca.ExecutionStatus
import com.netflix.spinnaker.orca.pipeline.model.*

interface ExecutionRepository {
  void store(Orchestration orchestration)
  void store(Pipeline pipeline)

  void storeExecutionContext(String id, Map<String, Object> context)

  void storeStage(Stage stage)
  void storeStage(OrchestrationStage stage)
  void storeStage(PipelineStage stage)

  void cancel(String id)
  void cancel(String id, String user, String reason)

  void pause(String id, String user)
  void resume(String id, String user)
  void resume(String id, String user, boolean ignoreCurrentStatus)

  boolean isCanceled(String id)
  void updateStatus(String id, ExecutionStatus status)

  Pipeline retrievePipeline(String id)
  void deletePipeline(String id)
  rx.Observable<Pipeline> retrievePipelines()
  rx.Observable<Pipeline> retrievePipelinesForApplication(String application)
  rx.Observable<Pipeline> retrievePipelinesForPipelineConfigId(String pipelineConfigId, ExecutionCriteria criteria)
  Orchestration retrieveOrchestration(String id)
  void deleteOrchestration(String id)
  rx.Observable<Orchestration> retrieveOrchestrations()
  rx.Observable<Orchestration> retrieveOrchestrationsForApplication(String application, ExecutionCriteria criteria)

  static class ExecutionCriteria {
    int limit
    Collection<String> statuses = []
  }
}
