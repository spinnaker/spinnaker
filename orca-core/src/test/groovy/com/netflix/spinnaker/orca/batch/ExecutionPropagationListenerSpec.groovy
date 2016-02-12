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
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionNotFoundException
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository
import org.springframework.batch.core.*
import spock.lang.Specification
import spock.lang.Unroll

class ExecutionPropagationListenerSpec extends Specification {
  def executionRepository = Mock(ExecutionRepository)
  def listener = new ExecutionPropagationListener(executionRepository, true, true)

  def "should mark pipeline/orchestration as RUNNING and set initial global execution context in beforeJob"() {
    given:
    def pipeline = new Pipeline(id: "PIPELINE-1")
    pipeline.context.putAll(["existing": "context"])
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
    1 * executionRepository.updateStatus(pipeline.id, ExecutionStatus.RUNNING)
    1 * executionRepository.updateStatus(orchestration.id, ExecutionStatus.RUNNING)
    1 * executionRepository.retrievePipeline(pipeline.id) >> { pipeline }
    1 * executionRepository.retrieveOrchestration(orchestration.id) >> {
      throw new ExecutionNotFoundException("No orchestration")
    }
    0 * _

    pipelineJobExecution.executionContext.get("existing") == "context"
    orchestrationJobExecution.executionContext.isEmpty()
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
    1 * executionRepository.isCanceled(_) >> false
    1 * executionRepository.updateStatus(pipeline.id, executionStatus)
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
      stepExecution("2", new Date(), BatchStatus.STOPPED, ExecutionStatus.STOPPED)
    ]              | BatchStatus.STOPPED        || ExecutionStatus.SUCCEEDED
    [
      stepExecution("1", new Date() - 1, BatchStatus.COMPLETED, ExecutionStatus.SUCCEEDED),
      stepExecution("2", new Date(), BatchStatus.STOPPED, ExecutionStatus.TERMINAL),
      stepExecution("3", new Date() + 1, BatchStatus.STOPPED, ExecutionStatus.CANCELED)
    ]              | BatchStatus.STOPPED        || ExecutionStatus.CANCELED
  }

  @Unroll
  def "should set executionStatus to #expectedStatus if execution was canceled and a stage was #stageStatus"() {
    given:
    def pipeline = new Pipeline(id: "PIPELINE-1")
    def pipelineJobExecution = new JobExecution(1L, new JobParameters([
      "pipeline": new JobParameter(pipeline.id)
    ]))
    pipelineJobExecution.addStepExecutions([stepExecution("whatever", new Date(), BatchStatus.COMPLETED, stageStatus)])

    and:
    executionRepository.isCanceled(pipeline.id) >> true

    when:
    listener.afterJob(pipelineJobExecution)

    then:
    1 * executionRepository.updateStatus(pipeline.id, expectedStatus)

    where:
    stageStatus | expectedStatus
    ExecutionStatus.CANCELED | ExecutionStatus.CANCELED
    ExecutionStatus.TERMINAL | ExecutionStatus.TERMINAL
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
