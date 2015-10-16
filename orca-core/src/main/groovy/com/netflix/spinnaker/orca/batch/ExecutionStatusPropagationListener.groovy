/*
 * Copyright 2015 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
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


package com.netflix.spinnaker.orca.batch

import com.netflix.spinnaker.orca.ExecutionStatus
import com.netflix.spinnaker.orca.pipeline.model.Execution
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository
import groovy.util.logging.Slf4j
import org.springframework.batch.core.JobExecution
import org.springframework.batch.core.StepExecution
import org.springframework.batch.core.listener.JobExecutionListenerSupport

@Slf4j
class ExecutionStatusPropagationListener extends JobExecutionListenerSupport {
  private final ExecutionRepository executionRepository

  ExecutionStatusPropagationListener(ExecutionRepository executionRepository) {
    this.executionRepository = executionRepository
  }

  @Override
  void afterJob(JobExecution jobExecution) {
    def stepExecutions = new ArrayList<StepExecution>(jobExecution.stepExecutions).sort { it.lastUpdated }.reverse()
    def stepExecution = stepExecutions.find { it.status == jobExecution.status } ?: stepExecutions[0]
    def orcaTaskStatus = stepExecution?.executionContext?.get("orcaTaskStatus") as ExecutionStatus ?: ExecutionStatus.TERMINAL

    def execution = fetchExecution(executionRepository, jobExecution)
    execution.executionStatus = orcaTaskStatus
    executionRepository.store(execution)

    log.info("Marked ${execution.class.simpleName} ${execution.id} as ${execution.executionStatus} (afterJob)")
  }

  @Override
  void beforeJob(JobExecution jobExecution) {
    def execution = fetchExecution(executionRepository, jobExecution)
    execution.executionStatus = ExecutionStatus.RUNNING
    executionRepository.store(execution)

    log.info("Marked ${execution.class.simpleName} ${execution.id} as ${execution.executionStatus} (beforeJob)")
  }

  private static Execution fetchExecution(ExecutionRepository executionRepository, JobExecution jobExecution) {
    if (jobExecution.jobParameters.getString("pipeline")) {
      return executionRepository.retrievePipeline(jobExecution.jobParameters.getString("pipeline"), false)
    }

    return executionRepository.retrieveOrchestration(jobExecution.jobParameters.getString("orchestration"), false)
  }
}
