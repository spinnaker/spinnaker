/*
 * Copyright 2014 Netflix, Inc.
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
import com.netflix.spinnaker.orca.jackson.OrcaObjectMapper
import com.netflix.spinnaker.orca.pipeline.model.Pipeline
import com.netflix.spinnaker.orca.pipeline.persistence.DefaultExecutionRepository
import com.netflix.spinnaker.orca.pipeline.persistence.memory.InMemoryOrchestrationStore
import com.netflix.spinnaker.orca.pipeline.persistence.memory.InMemoryPipelineStore
import org.springframework.batch.core.JobExecution
import org.springframework.batch.core.JobParameter
import org.springframework.batch.core.JobParameters
import org.springframework.batch.core.StepExecution
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Subject

class StageStatusPropagationListenerSpec extends Specification {

  def mapper = new OrcaObjectMapper()
  def pipelineStore = new InMemoryPipelineStore(mapper)
  def orchestrationStore = new InMemoryOrchestrationStore(mapper)
  def executionRepository = new DefaultExecutionRepository(orchestrationStore, pipelineStore)
  @Subject listener = new StageStatusPropagationListener(executionRepository)
  @Shared random = Random.newInstance()

  def "updates the stage status when a task execution completes"() {
    given: "a pipeline model"
    def pipeline = Pipeline.builder().withStage(stageType).build()
    pipelineStore.store(pipeline)

    and: "a batch execution context"
    def jobExecution = new JobExecution(id, new JobParameters(pipeline: new JobParameter(pipeline.id)))
    def stepExecution = new StepExecution("${pipeline.stages[0].id}.${stageType}.task1", jobExecution)

    and: "a task has run"
    executeTaskReturning taskStatus, stepExecution

    when: "the listener is triggered"
    def exitStatus = listener.afterStep stepExecution

    then: "it updates the status of the stage"
    pipelineStore.retrieve(pipeline.id).stages.first().status == taskStatus

    and: "the exit status of the batch step is unchanged"
    exitStatus == null

    where:
    taskStatus               | _
    ExecutionStatus.SUCCEEDED | _

    id = random.nextLong()
    stageType = "foo"
  }

  /**
   * This just emulates a task running and the associated updates to the batch
   * execution context.
   *
   * @param taskStatus the status the task should return.
   * @param stepExecution the batch execution context we want to update.
   */
  private void executeTaskReturning(ExecutionStatus taskStatus, StepExecution stepExecution) {
    stepExecution.executionContext.put("orcaTaskStatus", taskStatus)
  }
}
