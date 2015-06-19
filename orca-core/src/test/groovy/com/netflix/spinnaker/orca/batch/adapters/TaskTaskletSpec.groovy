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

package com.netflix.spinnaker.orca.batch.adapters

import com.netflix.spectator.api.ExtendedRegistry
import com.netflix.spectator.api.NoopRegistry
import com.netflix.spinnaker.orca.DefaultTaskResult
import com.netflix.spinnaker.orca.RetryableTask
import com.netflix.spinnaker.orca.Task
import com.netflix.spinnaker.orca.batch.exceptions.ExceptionHandler
import com.netflix.spinnaker.orca.jackson.OrcaObjectMapper
import com.netflix.spinnaker.orca.pipeline.model.Pipeline
import com.netflix.spinnaker.orca.pipeline.model.PipelineStage
import com.netflix.spinnaker.orca.pipeline.model.Stage
import com.netflix.spinnaker.orca.pipeline.persistence.DefaultExecutionRepository
import com.netflix.spinnaker.orca.pipeline.persistence.memory.InMemoryOrchestrationStore
import com.netflix.spinnaker.orca.pipeline.persistence.memory.InMemoryPipelineStore
import org.springframework.batch.core.*
import org.springframework.batch.core.scope.context.ChunkContext
import org.springframework.batch.core.scope.context.StepContext
import org.springframework.batch.repeat.RepeatStatus
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Unroll
import static com.netflix.spinnaker.orca.ExecutionStatus.*
import static org.springframework.batch.test.MetaDataInstanceFactory.createJobExecution
import static org.springframework.batch.test.MetaDataInstanceFactory.createStepExecution

class TaskTaskletSpec extends Specification {

  def objectMapper = new OrcaObjectMapper()
  def pipelineStore = new InMemoryPipelineStore(objectMapper)
  def orchestrationStore = new InMemoryOrchestrationStore(objectMapper)
  def executionRepository = new DefaultExecutionRepository(orchestrationStore, pipelineStore)
  def pipeline = Pipeline.builder().withStage("stage", "stage", [foo: "foo"]).build()
  def stage = pipeline.stages.first()
  def task = Mock(Task)

  @Subject
  def tasklet = new TaskTasklet(task, executionRepository, [], new ExtendedRegistry(new NoopRegistry()))

  JobExecution jobExecution
  StepExecution stepExecution
  StepContext stepContext
  StepContribution stepContribution
  ChunkContext chunkContext

  @Shared
  def random = Random.newInstance()

  void setup() {
    pipelineStore.store(pipeline)
    jobExecution = createJobExecution(
      "whatever", random.nextLong(), random.nextLong(),
      new JobParametersBuilder().addString("pipeline", pipeline.id).toJobParameters()
    )
    stepExecution = createStepExecution(jobExecution, "${stage.id}.${stage.type}.task1", random.nextLong())
    stepContext = new StepContext(stepExecution)
    stepContribution = new StepContribution(stepExecution)
    chunkContext = new ChunkContext(stepContext)
  }

  void cleanup() {
    pipelineStore.clear()
  }

  def "should invoke the step when executed"() {
    when:
    tasklet.execute(stepContribution, chunkContext)

    then:
    1 * task.execute(*_) >> new DefaultTaskResult(SUCCEEDED)
  }

  def "should pass the correct stage to the task"() {
    given:
    Stage stageArgument = null
    task.execute(_ as Stage) >> {
      stageArgument = it[0]
      return new DefaultTaskResult(SUCCEEDED)
    }

    when:
    tasklet.execute(stepContribution, chunkContext)

    then:
    stageArgument.immutable
    stageArgument.type == stage.type
    stageArgument.context == stage.context
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
    TERMINAL | RepeatStatus.FINISHED | ExitStatus.FAILED
    CANCELED | RepeatStatus.FINISHED | ExitStatus.STOPPED
    REDIRECT | RepeatStatus.FINISHED | new ExitStatus(REDIRECT.name())
  }

  @Unroll
  def "should attach the task result of #taskResultStatus status to the execution context"() {
    given:
    task.execute(*_) >> new DefaultTaskResult(taskResultStatus)

    when:
    tasklet.execute(stepContribution, chunkContext)

    then:
    chunkContext.stepContext.stepExecutionContext.orcaTaskStatus == taskResultStatus

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
    with(pipelineStore.retrieve(pipeline.id)) {
      stages.first().context == outputs + (taskStatus == RUNNING ? [:] : ['batch.task.id.task1': stepExecution.id])
    }

    where:
    taskStatus | _
    RUNNING    | _
    FAILED     | _
    SUCCEEDED  | _

    outputs = [foo: "bar", baz: "qux", appConfig: [:]]
  }

  def "should overwrite values in the stage if a task returns them as outputs"() {
    given:
    stage.context[key] = value

    and:
    task.execute(*_) >> new DefaultTaskResult(SUCCEEDED, [(key): value.reverse()])

    when:
    tasklet.execute(stepContribution, chunkContext)

    then:
    with(pipelineStore.retrieve(pipeline.id)) {
      stages.first().context[key] == value.reverse()
    }

    where:
    key = "foo"
    value = "bar"
  }

  def "should write values in the job execution context if a task returns them as global outputs"() {
    given:
    task.execute(*_) >> new DefaultTaskResult(SUCCEEDED, [:], outputs)

    when:
    tasklet.execute(stepContribution, chunkContext)

    then:
    chunkContext.stepContext.jobExecutionContext == outputs.collect {
      ["global-${it.key}" as String, it.value]
    }.collectEntries()

    where:
    outputs = [foo: "bar", appConfig: [:]]
  }

  @Unroll
  def "should invoke matching exception handler when task execution fails"() {
    expect:
    with(sourceTasklet.executeTask(new PipelineStage(), chunkContext)) {
      status == expectedStatus
    }

    where:
    sourceTasklet                     || expectedStatus
    buildTasklet(Task, false)         || TERMINAL
    buildTasklet(Task, true)          || TERMINAL
    buildTasklet(RetryableTask, true) || RUNNING

    exceptionType = "E1"
    operation = "E2"
    error = "E3"
    errors = ["E4", "E5"]
  }

  private buildTasklet(Class taskType, boolean shouldRetry) {
    def task = Mock(taskType)
    task.execute(_) >> { throw new RuntimeException() }

    def tasklet = new TaskTasklet(task, executionRepository, [], new ExtendedRegistry(new NoopRegistry()))
    tasklet.exceptionHandlers << Mock(ExceptionHandler) {
      1 * handles(_) >> {
        true
      }
      1 * handle(_, _) >> {
        return new ExceptionHandler.Response([shouldRetry: shouldRetry])
      }
    }

    return tasklet
  }
}
