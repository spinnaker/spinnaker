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

import com.netflix.spinnaker.orca.DefaultTaskResult
import com.netflix.spinnaker.orca.RetryableTask
import com.netflix.spinnaker.orca.batch.TaskTaskletAdapter
import com.netflix.spinnaker.orca.batch.lifecycle.BatchExecutionSpec
import com.netflix.spinnaker.orca.jackson.OrcaObjectMapper
import com.netflix.spinnaker.orca.pipeline.model.Pipeline
import com.netflix.spinnaker.orca.pipeline.persistence.DefaultExecutionRepository
import com.netflix.spinnaker.orca.pipeline.persistence.memory.InMemoryOrchestrationStore
import com.netflix.spinnaker.orca.pipeline.persistence.memory.InMemoryPipelineStore
import org.springframework.batch.core.BatchStatus
import org.springframework.batch.core.Job
import org.springframework.batch.core.job.builder.JobBuilder
import org.springframework.retry.backoff.Sleeper
import spock.lang.Shared
import static com.netflix.spinnaker.orca.ExecutionStatus.RUNNING
import static com.netflix.spinnaker.orca.ExecutionStatus.SUCCEEDED

class RetryableTaskTaskletSpec extends BatchExecutionSpec {

  @Shared backoffPeriod = 1000L

  def task = Mock(RetryableTask) {
    getBackoffPeriod() >> backoffPeriod
  }

  def sleeper = Mock(Sleeper)
  def objectMapper = new OrcaObjectMapper()
  def pipelineStore = new InMemoryPipelineStore(objectMapper)
  def orchestrationStore = new InMemoryOrchestrationStore(objectMapper)
  def executionRepository = new DefaultExecutionRepository(orchestrationStore, pipelineStore)
  def taskFactory = new TaskTaskletAdapter(executionRepository, [], sleeper)
  Pipeline pipeline

  @Override
  protected Job configureJob(JobBuilder jobBuilder) {
    pipeline = Pipeline.builder().withStage("retryable").build()
    pipelineStore.store(pipeline)
    def step = steps.get("${pipeline.stages[0].id}.retryable.task1")
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
}
