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

package com.netflix.spinnaker.orca.batch

import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Unroll
import com.netflix.spinnaker.orca.DefaultTaskResult
import com.netflix.spinnaker.orca.Task
import com.netflix.spinnaker.orca.TaskContext
import com.netflix.spinnaker.orca.TaskResult
import org.springframework.batch.core.ExitStatus
import org.springframework.batch.core.StepContribution
import org.springframework.batch.core.scope.context.ChunkContext
import org.springframework.batch.core.scope.context.StepContext
import org.springframework.batch.repeat.RepeatStatus
import static com.netflix.spinnaker.orca.TaskResult.Status.SUCCEEDED
import static org.springframework.batch.test.MetaDataInstanceFactory.createStepExecution

class TaskTaskletAdapterSpec extends Specification {

  def step = Mock(Task)

  @Subject tasklet = new TaskTaskletAdapter(step)

  def stepExecution = createStepExecution()
  def stepContext = new StepContext(stepExecution)
  def stepContribution = new StepContribution(stepExecution)
  def chunkContext = new ChunkContext(stepContext)

  def "should invoke the step when executed"() {
    when:
    tasklet.execute(stepContribution, chunkContext)

    then:
    1 * step.execute(*_) >> new DefaultTaskResult(SUCCEEDED)
  }

  def "should wrap job and step context in task context passed to execute method"() {
    given:
    stepExecution.executionContext.put(key, value)

    when:
    tasklet.execute(stepContribution, chunkContext)

    then:
    1 * step.execute(*_) >> { TaskContext context ->
      assert context.inputs[key] == value
      new DefaultTaskResult(SUCCEEDED)
    }

    where:
    key = "foo"
    value = "bar"
  }

  @Unroll("should convert a result of #taskResultStatus to repeat status #repeatStatus and exitStatus #exitStatus")
  def "should convert step return status to equivalent batch status"() {
    given:
    step.execute(*_) >> new DefaultTaskResult(taskResultStatus)

    expect:
    tasklet.execute(stepContribution, chunkContext) == repeatStatus

    and:
    stepContribution.exitStatus == exitStatus

    where:
    taskResultStatus            | repeatStatus             | exitStatus
    SUCCEEDED                   | RepeatStatus.FINISHED    | ExitStatus.COMPLETED
    TaskResult.Status.FAILED    | RepeatStatus.FINISHED    | ExitStatus.FAILED
    TaskResult.Status.RUNNING   | RepeatStatus.CONTINUABLE | ExitStatus.EXECUTING
    TaskResult.Status.SUSPENDED | RepeatStatus.FINISHED    | ExitStatus.STOPPED
  }

  @Unroll
  def "should write any task outputs to the step context if the task status is #taskStatus"() {
    given:
    step.execute(*_) >> new DefaultTaskResult(taskStatus, outputs)

    when:
    tasklet.execute(stepContribution, chunkContext)

    then:
    stepContext.stepExecutionContext == outputs
    stepContext.jobExecutionContext.isEmpty()

    where:
    taskStatus << [TaskResult.Status.RUNNING]
    outputs = [foo: "bar", baz: "qux"]
  }

  @Unroll
  def "should write any task outputs to the job context if the task status is #taskStatus"() {
    given:
    step.execute(*_) >> new DefaultTaskResult(taskStatus, outputs)

    when:
    tasklet.execute(stepContribution, chunkContext)

    then:
    stepContext.stepExecutionContext.isEmpty()
    stepContext.jobExecutionContext == outputs

    where:
    taskStatus << [TaskResult.Status.FAILED, SUCCEEDED]
    outputs = [foo: "bar", baz: "qux"]
  }

}
