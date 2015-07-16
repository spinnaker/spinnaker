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

import java.time.Clock
import java.time.Instant
import com.netflix.spectator.api.ExtendedRegistry
import com.netflix.spectator.api.NoopRegistry
import com.netflix.spinnaker.orca.DefaultTaskResult
import com.netflix.spinnaker.orca.RetryableTask
import com.netflix.spinnaker.orca.batch.TaskTaskletAdapter
import com.netflix.spinnaker.orca.batch.exceptions.TimeoutException
import com.netflix.spinnaker.orca.batch.lifecycle.BatchExecutionSpec
import com.netflix.spinnaker.orca.jackson.OrcaObjectMapper
import com.netflix.spinnaker.orca.pipeline.model.DefaultTask
import com.netflix.spinnaker.orca.pipeline.model.Pipeline
import com.netflix.spinnaker.orca.pipeline.model.PipelineStage
import com.netflix.spinnaker.orca.pipeline.persistence.memory.InMemoryExecutionRepository
import org.springframework.batch.core.BatchStatus
import org.springframework.batch.core.Job
import org.springframework.batch.core.StepExecution
import org.springframework.batch.core.job.builder.JobBuilder
import org.springframework.batch.core.scope.context.ChunkContext
import org.springframework.batch.core.scope.context.StepContext
import org.springframework.retry.backoff.Sleeper
import spock.lang.Shared
import spock.lang.Unroll
import static com.netflix.spinnaker.orca.ExecutionStatus.RUNNING
import static com.netflix.spinnaker.orca.ExecutionStatus.SUCCEEDED
import static com.netflix.spinnaker.orca.pipeline.model.Stage.STAGE_TIMEOUT_OVERRIDE_KEY
import static java.time.ZoneOffset.UTC

class RetryableTaskTaskletSpec extends BatchExecutionSpec {

  @Shared backoffPeriod = 1000L
  @Shared timeout = 60000L

  def task = Mock(RetryableTask) {
    getBackoffPeriod() >> backoffPeriod
    getTimeout() >> timeout
  }

  def sleeper = Mock(Sleeper)
  def objectMapper = new OrcaObjectMapper()
  def executionRepository = new InMemoryExecutionRepository()
  def taskFactory = new TaskTaskletAdapter(executionRepository, [], new ExtendedRegistry(new NoopRegistry()), sleeper)
  Pipeline pipeline

  @Shared def random = Random.newInstance()

  @Override
  protected Job configureJob(JobBuilder jobBuilder) {
    pipeline = Pipeline.builder().withStage("retryable").build()
    def taskModel = new DefaultTask(id: random.nextLong(), name: "task1")
    pipeline.stages.first().tasks << taskModel
    executionRepository.store(pipeline)
    def step = steps.get("${pipeline.stages[0].id}.retryable.${taskModel.name}.${taskModel.id}")
                    .tasklet(taskFactory.decorate(task))
                    .build()
    jobBuilder.start(step).build()
  }

  void "should backoff when the task returns continuable"() {
    when:
    def jobExecution = launchJob(pipeline: pipeline.id)

    then:
    3 * task.execute(_) >>> [
      new DefaultTaskResult(RUNNING),
      new DefaultTaskResult(RUNNING),
      new DefaultTaskResult(SUCCEEDED)
    ]

    and:
    2 * sleeper.sleep(backoffPeriod)

    and:
    jobExecution.status == BatchStatus.COMPLETED
  }

  void "should not retry if task immediately succeeds"() {
    given:
    task.execute(_) >> new DefaultTaskResult(SUCCEEDED)

    when:
    def jobExecution = launchJob(pipeline: pipeline.id)

    then:
    0 * sleeper._

    and:
    jobExecution.status == BatchStatus.COMPLETED
  }

  void "should not retry if an exception is thrown"() {
    given:
    task.execute(_) >> { throw new RuntimeException("o noes!") }

    when:
    def jobExecution = launchJob(pipeline: pipeline.id)

    then:
    0 * sleeper._

    and:
    jobExecution.status == BatchStatus.FAILED
  }

  @Unroll
  void "should raise TimeoutException if timeout exceeded"() {
    given:
    def clock = Clock.fixed(Instant.ofEpochMilli(currentTime), UTC)
    def chunkContext = new ChunkContext(
      new StepContext(Stub(StepExecution) {
        getStartTime() >> { new Date(0) }
      })
    )

    and:
    def stage = new PipelineStage(new Pipeline(), null, stageContext)
    def tasklet = new RetryableTaskTasklet(task, null, null, new ExtendedRegistry(new NoopRegistry()), clock)

    when:
    def exceptionThrown = false
    try {
      tasklet.doExecuteTask(stage, chunkContext)
    } catch (TimeoutException ignored) {
      exceptionThrown = true
    }

    then:
    exceptionThrown == expectedException

    where:
    currentTime | stageContext                                || expectedException
    0L          | [:]                                         || false
    timeout     | [:]                                         || false
    timeout + 1 | [(STAGE_TIMEOUT_OVERRIDE_KEY): timeout + 2] || false
    timeout     | [:]                                         || false
    timeout + 1 | [:]                                         || true
    timeout - 1 | [(STAGE_TIMEOUT_OVERRIDE_KEY): timeout - 2] || true
  }
}
