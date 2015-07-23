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

import com.netflix.spinnaker.orca.ExecutionStatus
import com.netflix.spinnaker.orca.pipeline.model.Execution
import com.netflix.spinnaker.orca.pipeline.model.Pipeline
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository
import groovy.util.logging.Slf4j
import org.springframework.batch.core.JobExecution
import org.springframework.batch.core.JobExecutionListener
import org.springframework.beans.factory.annotation.Autowired

/**
 * Reacts to pipelines finishing and schedules the next job waiting
 */
@Slf4j
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
    startTracker.getAllStartedExecutions().each { startedExecution ->
      try {
        Execution execution = executionRepository.retrievePipeline(startedExecution)
        if (execution.status in [
          ExecutionStatus.CANCELED,
          ExecutionStatus.FAILED,
          ExecutionStatus.TERMINAL
        ]) {
          processPipelines(execution)
        }
      } catch (Exception e) {
        log.error('failed to update pipeline status', e)
      }
    }
  }

  void processPipelines(Pipeline execution) {
    log.info("marking pipeline finished ${execution.id}")
    startTracker.markAsFinished(execution.pipelineConfigId, execution.id)
    if (execution.pipelineConfigId) {
      List<String> queuedPipelines = startTracker.getQueuedPipelines(execution.pipelineConfigId)
      if (!queuedPipelines.empty) {
        String toStartPipeline = queuedPipelines.first()
        queuedPipelines.each { id ->
          def queuedExecution = executionRepository.retrievePipeline(id)
          if (id == toStartPipeline) {
            log.info("starting pipeline ${toStartPipeline} due to ${execution.id} ending")
            pipelineStarter.startExecution(queuedExecution)
          } else {
            queuedExecution.canceled = true
            log.info("marking pipeline ${toStartPipeline} as canceled due to ${execution.id} ending")
            executionRepository.store(queuedExecution)
          }
          startTracker.removeFromQueue(execution.pipelineConfigId, id)
        }
      }
    }
  }
}
