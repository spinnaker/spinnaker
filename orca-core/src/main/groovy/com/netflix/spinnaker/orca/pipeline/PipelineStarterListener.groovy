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

package com.netflix.spinnaker.orca.pipeline

import com.netflix.spinnaker.orca.pipeline.model.Execution
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository
import org.springframework.batch.core.JobExecution
import org.springframework.batch.core.JobExecutionListener
import org.springframework.beans.factory.annotation.Autowired

/**
 * Reacts to pipelines finishing and schedules the next job waiting
 */
class PipelineStarterListener implements JobExecutionListener {

  @Autowired
  ExecutionRepository executionRepository

  @Autowired
  PipelineStartTracker startTracker

  @Autowired
  PipelineStarter pipelineStarter

  @Override
  void beforeJob(JobExecution jobExecution) {
    // do nothing when a job starts
  }

  @Override
  void afterJob(JobExecution jobExecution) {
    def execution = currentExecution(jobExecution)
    if (execution?.pipelineConfigId) {
      if (startTracker.getAllStartedExecutions().contains(execution.pipelineConfigId)) {
        startTracker.markAsFinished(execution.pipelineConfigId, execution.id)
      }
      List<String> queuedPipelines = startTracker.getQueuedPipelines(execution.pipelineConfigId)
      if (!queuedPipelines.empty) {
        String toStartPipeline = queuedPipelines.first()
        queuedPipelines.each { id ->
          def queuedExecution = executionRepository.retrievePipeline(id)
          if (id == toStartPipeline) {
            pipelineStarter.startExecution(queuedExecution)
          } else {
            queuedExecution.canceled = true
            executionRepository.store(queuedExecution)
          }
          startTracker.removeFromQueue(execution.pipelineConfigId, id)
        }
      }
    }
  }

  protected final Execution currentExecution(JobExecution jobExecution) {
    if (jobExecution.jobParameters.parameters.containsKey("pipeline")) {
      String id = jobExecution.jobParameters.getString("pipeline")
      executionRepository.retrievePipeline(id)
    } else {
      null
    }
  }

}
