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
import com.netflix.spinnaker.orca.pipeline.model.Orchestration
import com.netflix.spinnaker.orca.pipeline.model.Pipeline
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository
import org.springframework.batch.core.BatchStatus
import org.springframework.batch.core.JobExecution
import org.springframework.batch.core.JobParameter
import org.springframework.batch.core.JobParameters
import org.springframework.batch.core.StepExecution
import spock.lang.Specification
import spock.lang.Unroll

class ExecutionStatusPropagationListenerSpec extends Specification {
  def executionRepository = Mock(ExecutionRepository)
  def listener = new ExecutionStatusPropagationListener(executionRepository)

  def "should mark pipeline/orchestration as RUNNING in beforeJob"() {
    given:
    def pipeline = new Pipeline(id: "PIPELINE-1")
    def pipelineJobExecution = new JobExecution(1L, new JobParameters([
      "pipeline": new JobParameter(pipeline.id)
    ]))

    def orchestration = new Orchestration(id: "ORCHESTRATION-1")
    def orchestrationJobExecution = new JobExecution(1L, new JobParameters([
      "orchestration": new JobParameter(orchestration.id)
    ]))

    when:
    listener.beforeJob(pipelineJobExecution)
    listener.beforeJob(orchestrationJobExecution)

    then:
    1 * executionRepository.retrievePipeline(pipeline.id) >> { return pipeline }
    1 * executionRepository.store({ Pipeline p ->
      p.id == "PIPELINE-1" && p.executionStatus == ExecutionStatus.RUNNING
    } as Pipeline)

    1 * executionRepository.retrieveOrchestration(orchestration.id) >> { return orchestration }
    1 * executionRepository.store({ Orchestration o ->
      o.id == "ORCHESTRATION-1" && o.executionStatus == ExecutionStatus.RUNNING
    } as Orchestration)
    0 * _
  }

  @Unroll
  def "should set executionStatus based on relevant stepExecution status"() {
    given:
    def pipeline = new Pipeline(id: "PIPELINE-1")
    def pipelineJobExecution = new JobExecution(1L, new JobParameters([
      "pipeline": new JobParameter(pipeline.id)
    ]))
    pipelineJobExecution.addStepExecutions(stepExecutions)
    pipelineJobExecution.status = pipelineJobExecutionStatus

    when:
    listener.afterJob(pipelineJobExecution)

    then:
    1 * executionRepository.retrievePipeline(pipeline.id) >> { return pipeline }
    1 * executionRepository.store({ Pipeline p ->
      p.id == "PIPELINE-1" && p.executionStatus == executionStatus
    } as Pipeline)
    0 * _

    where:
    stepExecutions | pipelineJobExecutionStatus || executionStatus
    []             | BatchStatus.STOPPED        || ExecutionStatus.TERMINAL
    [
      stepExecution("1", new Date() - 1, BatchStatus.COMPLETED, ExecutionStatus.SUCCEEDED),
      stepExecution("2", new Date(), BatchStatus.STOPPED, ExecutionStatus.TERMINAL)
    ]              | BatchStatus.STOPPED        || ExecutionStatus.TERMINAL
    [
      stepExecution("1", new Date() - 1, BatchStatus.COMPLETED, ExecutionStatus.SUCCEEDED),
      stepExecution("2", new Date(), BatchStatus.STOPPED, ExecutionStatus.TERMINAL),
      stepExecution("3", new Date() + 1, BatchStatus.STOPPED, ExecutionStatus.CANCELED)
    ]              | BatchStatus.STOPPED        || ExecutionStatus.CANCELED
  }

  private static StepExecution stepExecution(String stepName,
                                             Date lastUpdated,
                                             BatchStatus status,
                                             ExecutionStatus executionStatus) {
    def stepExecution = new StepExecution(stepName, null)
    stepExecution.lastUpdated = lastUpdated
    stepExecution.status = status
    stepExecution.executionContext.put("orcaTaskStatus", executionStatus)

    return stepExecution
  }

}
