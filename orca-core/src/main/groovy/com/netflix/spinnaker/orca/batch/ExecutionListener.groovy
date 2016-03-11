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

package com.netflix.spinnaker.orca.batch

import com.netflix.spinnaker.orca.pipeline.model.Execution
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionNotFoundException
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository
import org.springframework.batch.core.JobExecution
import org.springframework.batch.core.listener.JobExecutionListenerSupport

abstract class ExecutionListener extends JobExecutionListenerSupport {

  private final ExecutionRepository executionRepository

  protected ExecutionListener(ExecutionRepository executionRepository) {
    this.executionRepository = executionRepository
  }

  void beforeExecution(String type, String id) {}

  void afterExecution(String type, String id) {}

  @Override
  final void beforeJob(JobExecution jobExecution) {
    def type = executionType(jobExecution)
    beforeExecution(type, executionId(jobExecution, type))
  }

  @Override
  final void afterJob(JobExecution jobExecution) {
    def type = executionType(jobExecution)
    afterExecution(type, executionId(jobExecution, type))
  }

  private final String executionType(JobExecution jobExecution) {
    if (jobExecution.jobParameters.parameters.keySet().contains("pipeline")) {
      return "pipeline"
    }
    return "orchestration"
  }

  private final String executionId(JobExecution jobExecution, String type) {
    return jobExecution.jobParameters.getString(type)
  }

  protected final Execution execution(String type, String id) {
    try {
      if (type == "pipeline") {
        return executionRepository.retrievePipeline(id)
      }
      return executionRepository.retrieveOrchestration(id)
    } catch (ExecutionNotFoundException ignored) {
      return null
    }
  }

}
