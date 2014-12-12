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

import com.netflix.spinnaker.orca.model.JobViewModel
import com.netflix.spinnaker.orca.model.PipelineViewModel
import com.netflix.spinnaker.orca.pipeline.model.Orchestration
import com.netflix.spinnaker.orca.pipeline.model.Pipeline
import com.netflix.spinnaker.orca.pipeline.model.Stage
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository
import org.springframework.batch.core.JobExecution
import org.springframework.batch.core.explore.JobExplorer
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.bind.annotation.*

@RestController
class TaskController {
  @Autowired
  JobExplorer jobExplorer

  @Autowired
  ExecutionRepository executionRepository

  @RequestMapping(value = "/applications/{application}/tasks", method = RequestMethod.GET)
  List<Orchestration> list(@PathVariable String application) {
    executionRepository.retrieveOrchestrationsForApplication(application)
  }

  @RequestMapping(value = "/tasks", method = RequestMethod.GET)
  List<Orchestration> list() {
    executionRepository.retrieveOrchestrations()
  }

  @RequestMapping(value = "/tasks/{id}", method = RequestMethod.GET)
  Orchestration getTask(@PathVariable String id) {
    executionRepository.retrieveOrchestration(id)
  }

  @RequestMapping(value = "/pipelines/{id}", method = RequestMethod.GET)
  Pipeline getPipeline(@PathVariable String id) {
    executionRepository.retrievePipeline(id)
  }

  @RequestMapping(value = "/pipelines", method = RequestMethod.GET)
  List<Pipeline> getPipelines() {
    executionRepository.retrievePipelines()
  }

  @RequestMapping(value = "/applications/{application}/pipelines", method = RequestMethod.GET)
  List<Pipeline> getApplicationPipelines(@PathVariable String application) {
    executionRepository.retrievePipelinesForApplication(application)
  }

  private JobViewModel convert(JobExecution jobExecution) {
    def steps = jobExecution.stepExecutions.collect {
      def stepName = it.stepName.contains('.') ? it.stepName.tokenize('.')[1] : it.stepName
      [name: stepName, status: it.exitStatus.exitCode, startTime: it.startTime?.time, endTime: it.endTime?.time]
    }
    def variables = [:]
    if (jobExecution.jobParameters.parameters.containsKey("description")) {
      variables.description = jobExecution.jobParameters.getString("description")
    }
    if (jobExecution.jobParameters.parameters.containsKey("name")) {
      variables.description = jobExecution.jobParameters.getString("name")
    }
    if (jobExecution.jobParameters.parameters.containsKey("pipeline")) {
      String pipelineId = jobExecution.jobParameters.parameters.get("pipeline")
      Pipeline pipeline = executionRepository.retrievePipeline(pipelineId)
      for (stage in pipeline.stages) {
        for (entry in stage.context.entrySet()) {
          def key = "${stage.type}.${entry.key}"
          variables[key] = entry.value
        }
      }
    } else if (jobExecution.jobParameters.parameters.containsKey("orchestration")) {
      String orchestrationId = jobExecution.jobParameters.parameters.get("orchestration")
      Orchestration orchestration = executionRepository.retrieveOrchestration(orchestrationId)
      for (stage in orchestration.stages) {
        for (entry in stage.context.entrySet()) {
          variables[entry.key] = entry.value
        }
      }
    } else {
      for (stepExecution in jobExecution.stepExecutions) {
        def stageName = stepExecution.stepName.find(/^\w+(?=\.)/)
        if (!stageName) continue
        Stage stage = (Stage) stepExecution.jobExecution.executionContext.get(stageName)
        for (entry in stage.context.entrySet()) {
          variables[entry.key] = entry.value
        }
      }
    }
    new JobViewModel(id: jobExecution.id, name: jobExecution.jobInstance.jobName, status: jobExecution.status,
      variables: variables.entrySet(), steps: steps, startTime: jobExecution.startTime?.time,
      endTime: jobExecution.endTime?.time)
  }
}
