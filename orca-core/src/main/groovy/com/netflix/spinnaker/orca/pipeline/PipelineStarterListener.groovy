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

import com.netflix.spinnaker.orca.pipeline.model.Pipeline
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionNotFoundException
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import org.springframework.batch.core.JobExecution
import org.springframework.batch.core.JobExecutionListener
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.core.annotation.Order

/**
 * Reacts to pipelines finishing and schedules the next job waiting
 */
@Slf4j
@Order(0)
@CompileStatic
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
    startTracker.getAllStartedExecutions().each { startedExecutionId ->
      try {
        def execution = executionRepository.retrievePipeline(startedExecutionId)
        if (execution.status.complete) {
          processPipelines(execution)
        }
      } catch (ExecutionNotFoundException ignored) {
        log.warn("Unable to update pipeline status for missing execution (executionId: ${startedExecutionId})")
      } catch (Exception e) {
        log.error('failed to update pipeline status', e)
      }
    }
  }

  void processPipelines(Pipeline execution) {
    log.info("marking pipeline finished ${execution.id}")
    startTracker.markAsFinished(execution.pipelineConfigId, execution.id)
    if (execution.pipelineConfigId) {
      def queuedPipelines = startTracker.getQueuedPipelines(execution.pipelineConfigId)
      if (!queuedPipelines.empty) {
        def nextPipelineId = queuedPipelines.first()
        queuedPipelines.each { id ->
          if (id == nextPipelineId) {
            def queuedExecution = executionRepository.retrievePipeline(id)
            log.info("starting pipeline ${nextPipelineId} due to ${execution.id} ending")
            pipelineStarter.startExecution(queuedExecution)
            startTracker.removeFromQueue(execution.pipelineConfigId, id)
          } else if (!execution.keepWaitingPipelines) {
            log.info("marking pipeline ${nextPipelineId} as canceled due to ${execution.id} ending")
            executionRepository.cancel(id)
            startTracker.removeFromQueue(execution.pipelineConfigId, id)
          } // else we want to keep the pipeline in the queue

        }
      }
    }
  }
}
