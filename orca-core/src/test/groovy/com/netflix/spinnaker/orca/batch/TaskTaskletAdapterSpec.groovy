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

import com.netflix.spinnaker.orca.DefaultTaskResult
import com.netflix.spinnaker.orca.Status
import com.netflix.spinnaker.orca.Task
import com.netflix.spinnaker.orca.TaskContext
import org.springframework.batch.core.ExitStatus
import org.springframework.batch.core.StepContribution
import org.springframework.batch.core.scope.context.ChunkContext
import org.springframework.batch.core.scope.context.StepContext
import org.springframework.batch.repeat.RepeatStatus
import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Unroll
import static com.netflix.spinnaker.orca.Status.SUCCEEDED
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
  def "should convert task result status to equivalent batch status"() {
    given:
    step.execute(*_) >> new DefaultTaskResult(taskResultStatus)

    expect:
    tasklet.execute(stepContribution, chunkContext) == repeatStatus

    and:
    stepContribution.exitStatus == exitStatus

    where:
    taskResultStatus            | repeatStatus             | exitStatus
    SUCCEEDED                   | RepeatStatus.FINISHED    | ExitStatus.COMPLETED
    Status.FAILED    | RepeatStatus.FINISHED    | ExitStatus.FAILED
    Status.RUNNING   | RepeatStatus.CONTINUABLE | ExitStatus.EXECUTING
    Status.SUSPENDED | RepeatStatus.FINISHED    | ExitStatus.STOPPED
  }

  // TODO: this feels a bit stringly-typed but I think it's better than just throwing it into the execution context under some arbitrary key
  @Unroll("should attach the task result status of #taskResultStatus as an exit description")
  def "should attach the task result status as an exit description"() {
    given:
    step.execute(*_) >> new DefaultTaskResult(taskResultStatus)

    when:
    tasklet.execute(stepContribution, chunkContext)

    then:
    stepContribution.exitStatus.exitDescription == taskResultStatus.name()

    where:
    taskResultStatus            | _
    SUCCEEDED                   | _
    Status.FAILED    | _
    Status.RUNNING   | _
    Status.SUSPENDED | _
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
    taskStatus << [Status.RUNNING]
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
    taskStatus << [Status.FAILED, SUCCEEDED]
    outputs = [foo: "bar", baz: "qux"]
  }

  def "should overwrite values in the context inputs if a task sets them as outputs"() {
    given:
    stepExecution.jobExecution.executionContext.put(key, value)

    and:
    step.execute(*_) >> new DefaultTaskResult(SUCCEEDED, [(key): value.reverse()])

    when:
    tasklet.execute(stepContribution, chunkContext)

    then:
    stepExecution.jobExecution.executionContext.get(key) == value.reverse()

    where:
    key = "foo"
    value = "bar"
  }

}
