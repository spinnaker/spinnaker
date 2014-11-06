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
import com.netflix.spinnaker.orca.batch.PipelineInitializerTasklet
import com.netflix.spinnaker.orca.batch.StageStatusPropagationListener
import com.netflix.spinnaker.orca.batch.TaskTaskletAdapter
import com.netflix.spinnaker.orca.batch.lifecycle.BatchExecutionSpec
import com.netflix.spinnaker.orca.pipeline.Pipeline
import org.springframework.batch.core.BatchStatus
import org.springframework.batch.core.Job
import org.springframework.batch.core.job.builder.JobBuilder
import org.springframework.retry.backoff.Sleeper
import spock.lang.Shared
import static com.netflix.spinnaker.orca.PipelineStatus.RUNNING
import static com.netflix.spinnaker.orca.PipelineStatus.SUCCEEDED

class RetryableTaskTaskletSpec extends BatchExecutionSpec {

  @Shared backoffPeriod = 1000L

  def task = Mock(RetryableTask) {
    getBackoffPeriod() >> backoffPeriod
  }

  def sleeper = Mock(Sleeper)
  def taskFactory = new TaskTaskletAdapter(sleeper)

  @Override
  protected Job configureJob(JobBuilder jobBuilder) {
    def pipeline = new Pipeline.Builder().withStage("retryable").build()
    def step1 = steps.get("init")
                     .tasklet(new PipelineInitializerTasklet(pipeline))
                     .build()
    def step2 = steps.get("retryable.task1")
                     .listener(StageStatusPropagationListener.instance)
                     .tasklet(taskFactory.decorate(task))
                     .build()
    jobBuilder.start(step1).next(step2).build()
  }

  void "should backoff when the task returns continuable"() {
    when:
    def jobExecution = launchJob()

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
    def jobExecution = launchJob()

    then:
    0 * sleeper._

    and:
    jobExecution.status == BatchStatus.COMPLETED
  }

  void "should not retry if an exception is thrown"() {
    given:
    task.execute(_) >> { throw new RuntimeException("o noes!") }

    when:
    def jobExecution = launchJob()

    then:
    0 * sleeper._

    and:
    jobExecution.status == BatchStatus.FAILED
  }
}
