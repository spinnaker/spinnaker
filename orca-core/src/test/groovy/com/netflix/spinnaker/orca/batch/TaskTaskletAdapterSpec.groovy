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
import com.netflix.spinnaker.orca.PipelineStatus
import com.netflix.spinnaker.orca.Task
import com.netflix.spinnaker.orca.pipeline.Pipeline
import org.springframework.batch.core.ExitStatus
import org.springframework.batch.core.StepContribution
import org.springframework.batch.core.scope.context.ChunkContext
import org.springframework.batch.core.scope.context.StepContext
import org.springframework.batch.repeat.RepeatStatus
import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Unroll
import static PipelineStatus.SUCCEEDED
import static com.netflix.spinnaker.orca.PipelineStatus.*
import static org.apache.commons.lang.math.RandomUtils.nextLong
import static org.springframework.batch.test.MetaDataInstanceFactory.createStepExecution

class TaskTaskletAdapterSpec extends Specification {

  def pipeline = Pipeline.builder().withStage("stage").build()
  def stage = pipeline.stages.first()
  def task = Mock(Task)

  @Subject tasklet = new TaskTaskletAdapter(task)

  def stepExecution = createStepExecution("${stage.type}.task1", nextLong())
  def stepContext = new StepContext(stepExecution)
  def stepContribution = new StepContribution(stepExecution)
  def chunkContext = new ChunkContext(stepContext)

  def setup() {
    new PipelineInitializerTasklet(pipeline).execute(stepContribution, chunkContext)
  }

  def "should invoke the step when executed"() {
    when:
    tasklet.execute(stepContribution, chunkContext)

    then:
    1 * task.execute(*_) >> new DefaultTaskResult(SUCCEEDED)
  }

  def "should pass the correct stage to the task"() {
    when:
    tasklet.execute(stepContribution, chunkContext)

    then:
    1 * task.execute(stage) >> new DefaultTaskResult(SUCCEEDED)
  }

  @Unroll("should convert a result of #taskResultStatus to repeat status #repeatStatus and exitStatus #exitStatus")
  def "should convert task result status to equivalent batch status"() {
    given:
    task.execute(*_) >> new DefaultTaskResult(taskResultStatus)

    expect:
    tasklet.execute(stepContribution, chunkContext) == repeatStatus

    and:
    stepContribution.exitStatus == exitStatus

    where:
    taskResultStatus | repeatStatus             | exitStatus
    SUCCEEDED        | RepeatStatus.FINISHED    | ExitStatus.COMPLETED
    FAILED           | RepeatStatus.FINISHED    | ExitStatus.FAILED
    RUNNING          | RepeatStatus.CONTINUABLE | ExitStatus.EXECUTING
    SUSPENDED        | RepeatStatus.FINISHED    | ExitStatus.STOPPED
  }

  // TODO: this feels a bit stringly-typed but I think it's better than just throwing it into the execution context under some arbitrary key
  @Unroll("should attach the task result status of #taskResultStatus as an exit description")
  def "should attach the task result status as an exit description"() {
    given:
    task.execute(*_) >> new DefaultTaskResult(taskResultStatus)

    when:
    tasklet.execute(stepContribution, chunkContext)

    then:
    stepContribution.exitStatus.exitDescription == taskResultStatus.name()

    where:
    taskResultStatus | _
    SUCCEEDED        | _
    FAILED           | _
    RUNNING          | _
    SUSPENDED        | _
  }

  @Unroll
  def "should write any task outputs to the stage context if the task status is #taskStatus"() {
    given:
    task.execute(*_) >> new DefaultTaskResult(taskStatus, outputs)

    when:
    tasklet.execute(stepContribution, chunkContext)

    then:
    stage.context == outputs

    where:
    taskStatus << [RUNNING, FAILED, SUCCEEDED]
    outputs = [foo: "bar", baz: "qux"]
  }

  def "should overwrite values in the stage if a task returns them as outputs"() {
    given:
    stage.context[key] = value

    and:
    task.execute(*_) >> new DefaultTaskResult(SUCCEEDED, [(key): value.reverse()])

    when:
    tasklet.execute(stepContribution, chunkContext)

    then:
    stage.context[key] == value.reverse()

    where:
    key = "foo"
    value = "bar"
  }
}
