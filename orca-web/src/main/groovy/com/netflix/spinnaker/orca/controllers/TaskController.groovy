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
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.bind.annotation.*

@RestController
class TaskController {
  @Autowired
  ExecutionRepository executionRepository

  @RequestMapping(value = "/applications/{application}/tasks", method = RequestMethod.GET)
  List<Orchestration> list(@PathVariable String application) {
    executionRepository.retrieveOrchestrationsForApplication(application).collect { convert it }.sort { it.startTime ?: it.id }.reverse()
  }

  @RequestMapping(value = "/tasks", method = RequestMethod.GET)
  List<OrchestrationViewModel> list() {
    executionRepository.retrieveOrchestrations().collect { convert it }
  }

  @RequestMapping(value = "/tasks/{id}", method = RequestMethod.GET)
  OrchestrationViewModel getTask(@PathVariable String id) {
    convert executionRepository.retrieveOrchestration(id)
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

  @RequestMapping(value = "/pipelines/{id}/cancel", method = RequestMethod.PUT)
  Pipeline cancel(@PathVariable String id) {
    def pipeline = executionRepository.retrievePipeline(id)
    pipeline.canceled = true
    executionRepository.store(pipeline)
    pipeline
  }

  @RequestMapping(value = "/pipelines", method = RequestMethod.GET)
  List<Pipeline> getPipelines() {
    executionRepository.retrievePipelines().sort { it.startTime ?: it.id }.reverse()
  }

  @RequestMapping(value = "/applications/{application}/pipelines", method = RequestMethod.GET)
  List<Pipeline> getApplicationPipelines(@PathVariable String application) {
    executionRepository.retrievePipelinesForApplication(application)
  }

  private OrchestrationViewModel convert(Orchestration orchestration) {
    def variables = [:]
      for (stage in orchestration.stages) {
        for (entry in stage.context.entrySet()) {
          variables[entry.key] = entry.value
        }
      }
    new OrchestrationViewModel(id: orchestration.id, name: orchestration.description, status: orchestration.getStatus(),
      variables: variables.entrySet(), steps: orchestration.stages.tasks.flatten(), startTime: orchestration.startTime,
      endTime: orchestration.endTime)
  }
}
