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
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionNotFoundException
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import org.springframework.batch.core.JobExecution
import org.springframework.batch.core.StepExecution
import org.springframework.batch.core.listener.JobExecutionListenerSupport
import org.springframework.core.Ordered
import static com.netflix.spinnaker.orca.ExecutionStatus.*

@Slf4j
@CompileStatic
class ExecutionPropagationListener extends JobExecutionListenerSupport implements Ordered {
  private final ExecutionRepository executionRepository

  ExecutionPropagationListener(ExecutionRepository executionRepository) {
    this.executionRepository = executionRepository
  }

  @Override
  void beforeJob(JobExecution jobExecution) {
    def id = executionId(jobExecution)
    executionRepository.updateStatus(id, RUNNING)

    def execution = execution(executionRepository, jobExecution)
    if (execution?.context) {
      execution.context.each { String key, Object value ->
        jobExecution.executionContext.put(key, value)
      }
      log.info("Restored execution context for $id (beforeJob)")
    }

    log.info("Marked $id as $RUNNING (beforeJob)")
  }

  @Override
  void afterJob(JobExecution jobExecution) {
    def id = executionId(jobExecution)

    def orcaTaskStatus
    if (executionRepository.isCanceled(id)) {
      orcaTaskStatus = CANCELED
    } else if (jobExecution.failureExceptions) {
      orcaTaskStatus = TERMINAL
    } else {
      def stepExecutions = new ArrayList<StepExecution>(jobExecution.stepExecutions).sort { it.lastUpdated }.reverse()
      def stepExecution = stepExecutions.find { it.status == jobExecution.status } ?: stepExecutions[0]
      orcaTaskStatus = stepExecution?.executionContext?.get("orcaTaskStatus") as ExecutionStatus ?: TERMINAL
    }

    if (orcaTaskStatus == STOPPED) {
      orcaTaskStatus = SUCCEEDED
    }

    executionRepository.updateStatus(id, orcaTaskStatus)

    log.info("Marked $id as $orcaTaskStatus (afterJob)")
  }

  private static String executionId(JobExecution jobExecution) {
    if (jobExecution.jobParameters.getString("pipeline")) {
      return jobExecution.jobParameters.getString("pipeline")
    }
    return jobExecution.jobParameters.getString("orchestration")
  }

  @Override
  int getOrder() {
    HIGHEST_PRECEDENCE
  }

  private static Execution execution(ExecutionRepository executionRepository, JobExecution jobExecution) {
    try {
      if (jobExecution.jobParameters.getString("pipeline")) {
        return executionRepository.retrievePipeline(jobExecution.jobParameters.getString("pipeline"))
      }
      return executionRepository.retrieveOrchestration(jobExecution.jobParameters.getString("orchestration"))
    } catch (ExecutionNotFoundException ignored) {
      return null
    }
  }
}
