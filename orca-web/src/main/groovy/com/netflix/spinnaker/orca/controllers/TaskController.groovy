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

package com.netflix.spinnaker.orca.controllers

import com.netflix.spinnaker.orca.model.OrchestrationViewModel
import com.netflix.spinnaker.orca.pipeline.model.Orchestration
import com.netflix.spinnaker.orca.pipeline.model.Pipeline
import com.netflix.spinnaker.orca.pipeline.model.PipelineStage
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.web.bind.annotation.*

import java.time.Clock

@RestController
class TaskController {
  @Autowired
  ExecutionRepository executionRepository

  @Value('${daysOfExecutionHistory:14}')
  int daysOfExecutionHistory

  Clock clock = Clock.systemUTC()

  @RequestMapping(value = "/applications/{application}/tasks", method = RequestMethod.GET)
  List<Orchestration> list(@PathVariable String application) {
    def startTimeCutoff = (new Date(clock.millis()) - daysOfExecutionHistory).time
    executionRepository.retrieveOrchestrationsForApplication(application)
      .findAll { !it.startTime || (it.startTime > startTimeCutoff) }
      .collect { convert it }
      .sort(startTimeOrId)
  }

  @RequestMapping(value = "/tasks", method = RequestMethod.GET)
  List<OrchestrationViewModel> list() {
    executionRepository.retrieveOrchestrations().collect { convert it }
  }

  @RequestMapping(value = "/tasks/{id}", method = RequestMethod.GET)
  OrchestrationViewModel getTask(@PathVariable String id) {
    convert executionRepository.retrieveOrchestration(id)
  }

  @RequestMapping(value = "/tasks/{id}", method = RequestMethod.DELETE)
  OrchestrationViewModel deleteTask(@PathVariable String id) {
    executionRepository.deleteOrchestration(id)
  }

  @RequestMapping(value = "/tasks/{id}/cancel", method = RequestMethod.PUT)
  OrchestrationViewModel cancelTask(@PathVariable String id) {
    def orchestration = executionRepository.retrieveOrchestration(id)
    orchestration.canceled = true
    executionRepository.store(orchestration)
    convert orchestration
  }

  @RequestMapping(value = "/pipelines/{id}", method = RequestMethod.GET)
  Pipeline getPipeline(@PathVariable String id) {
    executionRepository.retrievePipeline(id)
  }

  @RequestMapping(value = "/pipelines/{id}", method = RequestMethod.DELETE)
  void deletePipeline(@PathVariable String id) {
    executionRepository.deletePipeline(id)
  }

  @RequestMapping(value = "/pipelines/{id}/cancel", method = RequestMethod.PUT)
  Pipeline cancel(@PathVariable String id) {
    def pipeline = executionRepository.retrievePipeline(id)
    pipeline.canceled = true
    executionRepository.store(pipeline)
    pipeline
  }

  @RequestMapping(value = "/pipelines/{id}/stages/{stageId}", method = RequestMethod.PATCH)
  Pipeline updatePipelineStage(@PathVariable String id, @PathVariable String stageId, @RequestBody Map context) {
    def pipeline = executionRepository.retrievePipeline(id)

    def stage = pipeline.stages.find { it.id == stageId } as PipelineStage
    if (stage) {
      stage.context.putAll(context)
      executionRepository.storeStage(stage)
    }

    pipeline
  }

  @RequestMapping(value = "/pipelines", method = RequestMethod.GET)
  List<Pipeline> getPipelines() {
    executionRepository.retrievePipelines().sort { it.startTime ?: it.id }.reverse()
  }

  @RequestMapping(value = "/applications/{application}/pipelines", method = RequestMethod.GET)
  List<Pipeline> getApplicationPipelines(@PathVariable String application) {
    def startTimeCutoff = (new Date(clock.millis()) - daysOfExecutionHistory).time
    executionRepository.retrievePipelinesForApplication(application)
      .findAll { !it.startTime || (it.startTime > startTimeCutoff) }
      .sort(startTimeOrId)
  }

  private static Closure startTimeOrId = { a, b ->
    a.startTime && b.startTime ? a.startTime - b.startTime
      : a.startTime ? 1 : b.startTime ? -1
      : b.id <=> a.id
  }

  private OrchestrationViewModel convert(Orchestration orchestration) {
    def variables = [:]
    for (stage in orchestration.stages) {
      for (entry in stage.context.entrySet()) {
        variables[entry.key] = entry.value
      }
    }
    new OrchestrationViewModel(
      id: orchestration.id,
      name: orchestration.description,
      status: orchestration.getStatus(),
      variables: variables.collect { key, value ->
        [
          "key"  : key,
          "value": value
        ]
      },
      steps: orchestration.stages.tasks.flatten(),
      startTime: orchestration.startTime,
      endTime: orchestration.endTime
    )
  }
}
