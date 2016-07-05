/*
 * Copyright 2016 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.orca.echo.spring

import com.netflix.spinnaker.orca.ExecutionStatus
import com.netflix.spinnaker.orca.echo.EchoService
import com.netflix.spinnaker.orca.pipeline.model.Execution
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import org.springframework.batch.core.ExitStatus
import org.springframework.batch.core.JobExecution
import org.springframework.batch.core.JobExecutionListener
import org.springframework.core.annotation.Order

@Slf4j
@Order(0)
@CompileStatic
class EchoNotifyingPipelineExecutionListener implements JobExecutionListener {

  protected final ExecutionRepository executionRepository
  private final EchoService echoService

  EchoNotifyingPipelineExecutionListener(ExecutionRepository executionRepository, EchoService echoService) {
    this.executionRepository = executionRepository
    this.echoService = echoService
  }

  @Override
  void beforeJob(JobExecution jobExecution) {
    def execution = currentExecution(jobExecution)
    try {
      if (execution.status != ExecutionStatus.SUSPENDED) {
        echoService.recordEvent(
          details: [
            source     : "orca",
            type       : "orca:pipeline:starting",
            application: execution.application,
          ],
          content: [
            execution  : executionRepository.retrievePipeline(execution.id),
            executionId: execution.id
          ]
        )
      }
    } catch (Exception e) {
      log.error("Failed to send pipeline start event: ${execution?.id}")
    }
  }

  @Override
  void afterJob(JobExecution jobExecution) {
    def execution = currentExecution(jobExecution)
    try {
      if (execution.status != ExecutionStatus.SUSPENDED) {
        echoService.recordEvent(
          details: [
            source     : "orca",
            type       : "orca:pipeline:${(wasSuccessful(jobExecution, execution) ? "complete" : "failed")}".toString(),
            application: execution.application,
          ],
          content: [
            execution  : executionRepository.retrievePipeline(execution.id),
            executionId: execution.id
          ]
        )
      }
    } catch (Exception e) {
      log.error("Failed to send pipeline end event: ${execution?.id}")
    }
  }

  // TODO: this is dupe of method in StageExecutionListener
  protected final Execution currentExecution(JobExecution jobExecution) {
    if (jobExecution.jobParameters.parameters.containsKey("pipeline")) {
      String id = jobExecution.jobParameters.getString("pipeline")
      executionRepository.retrievePipeline(id)
    } else {
      String id = jobExecution.jobParameters.getString("orchestration")
      executionRepository.retrieveOrchestration(id)
    }
  }

  /**
   * Determines if the step was a success (from an Orca perspective). Note that
   * even if the Orca task failed we'll get a `jobExecution.status` of
   * `COMPLETED` as the error was handled.
   */
  private static boolean wasSuccessful(JobExecution jobExecution, Execution currentExecution) {
    jobExecution.exitStatus.exitCode == ExitStatus.COMPLETED.exitCode || currentExecution.status.isSuccessful()
  }
}
