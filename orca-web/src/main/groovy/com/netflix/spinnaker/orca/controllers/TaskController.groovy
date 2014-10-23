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
import com.netflix.spinnaker.orca.pipeline.Pipeline
import org.springframework.batch.core.JobExecution
import org.springframework.batch.core.explore.JobExplorer
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestMethod
import org.springframework.web.bind.annotation.RestController

@RestController
class TaskController {
  @Autowired
  JobExplorer jobExplorer

  @RequestMapping(value = "/applications/{application}/tasks", method = RequestMethod.GET)
  def list(@PathVariable String application) {
    ((List<JobViewModel>) jobExplorer.jobNames.collectMany {
      jobExplorer.findJobInstancesByJobName(it, 0, 1000).collectMany {
        jobExplorer.getJobExecutions(it).findAll {
          it.jobParameters.parameters?.application?.parameter == application
        }.collect {
          TaskController.convert it
        }
      }
    }).sort { it.id }
  }

  @RequestMapping(value = "/tasks", method = RequestMethod.GET)
  List<JobViewModel> list() {
    ((List<JobViewModel>) jobExplorer.jobNames.collectMany {
      jobExplorer.findJobInstancesByJobName(it, 0, 1000).collectMany {
        jobExplorer.getJobExecutions(it).collect {
          TaskController.convert it
        }
      }
    }).sort { it.id }
  }

  @RequestMapping(value = "/tasks/{id}", method = RequestMethod.GET)
  JobViewModel getTask(@PathVariable Long id) {
    convert jobExplorer.getJobExecution(id)
  }

  private static JobViewModel convert(JobExecution jobExecution) {
    def steps = jobExecution.stepExecutions.collect {
      def stepName = it.stepName.contains('.') ? it.stepName.tokenize('.')[1] : it.stepName
      [name: stepName, status: it.exitStatus.exitCode, startTime: it.startTime, endTime: it.endTime]
    }
    def variables = []
    if (jobExecution.jobParameters.parameters.containsKey("description")) {
      variables << [description: jobExecution.jobParameters.getString("description")]
    }
    if (jobExecution.jobParameters.parameters.containsKey("name")) {
      variables << [description: jobExecution.jobParameters.getString("name")]
    }
    for (entry in jobExecution.executionContext.entrySet()) {
      if (entry.key != "pipeline") variables.add(entry)
    }
    if (jobExecution.executionContext.containsKey("pipeline")) {
      Pipeline pipeline = (Pipeline) jobExecution.executionContext.get("pipeline")
      for (stage in pipeline.stages) {
        for (entry in stage.context.entrySet()) {
          variables.add([key: "${stage.type}.${entry.key}".toString(), value: entry.value])
        }
      }
    }
    new JobViewModel(id: jobExecution.id, name: jobExecution.jobInstance.jobName, status: jobExecution.status,
      variables: variables, steps: steps, startTime: jobExecution.startTime,
      endTime: jobExecution.endTime)
  }
}
