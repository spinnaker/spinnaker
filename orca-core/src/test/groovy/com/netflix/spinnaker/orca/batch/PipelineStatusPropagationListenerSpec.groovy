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

import com.netflix.spinnaker.orca.DefaultTaskResult
import com.netflix.spinnaker.orca.PipelineStatus
import com.netflix.spinnaker.orca.pipeline.Pipeline
import org.springframework.batch.core.JobExecution
import org.springframework.batch.core.StepExecution
import spock.lang.Specification
import spock.lang.Subject
import static org.apache.commons.lang.math.RandomUtils.nextLong

class PipelineStatusPropagationListenerSpec extends Specification {

  def id = nextLong()
  def jobExecution = new JobExecution(id)
  def stepExecution = new StepExecution("foo.task1", jobExecution)

  def pipeline = Pipeline.builder().withStage("foo").build()

  @Subject listener = StageStatusPropagationListener.instance

  def setup() {
    jobExecution.executionContext.put("pipeline", pipeline)
    for (stage in pipeline.stages) jobExecution.executionContext.put(stage.type, stage)
  }

  def "updates the stage status to RUNNING when a task starts"() {
    when: "the listener is triggered"
    listener.beforeStep stepExecution

    then: "it updates the status of the stage"
    pipeline.stages.first().status == PipelineStatus.RUNNING
  }

  def "updates the stage status when a task execution completes"() {
    given: "a task has run"
    executeTaskReturning taskStatus, stepExecution

    when: "the listener is triggered"
    def exitStatus = listener.afterStep stepExecution

    then: "it updates the status of the stage"
    pipeline.stages.first().status == taskStatus

    and: "the exit status of the batch step is unchanged"
    exitStatus == null

    where:
    taskStatus               | _
    PipelineStatus.SUCCEEDED | _
  }

  /**
   * This just emulates a task running and the associated updates to the batch
   * execution context.
   *
   * @param taskStatus the status the task should return.
   * @param stepExecution the batch execution context we want to update.
   */
  private void executeTaskReturning(PipelineStatus taskStatus, StepExecution stepExecution) {
    def taskResult = BatchStepStatus.mapResult(new DefaultTaskResult(taskStatus))
    stepExecution.exitStatus = taskResult.exitStatus.addExitDescription(taskStatus.name())
  }
}
