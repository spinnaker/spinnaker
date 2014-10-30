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
import com.netflix.spinnaker.orca.RetryableTask
import com.netflix.spinnaker.orca.pipeline.Pipeline
import org.springframework.batch.core.StepContribution
import org.springframework.batch.core.scope.context.ChunkContext
import org.springframework.batch.core.scope.context.StepContext
import org.springframework.util.StopWatch
import spock.lang.Specification
import static com.netflix.spinnaker.orca.PipelineStatus.RUNNING
import static com.netflix.spinnaker.orca.PipelineStatus.SUCCEEDED
import static org.apache.commons.lang.math.RandomUtils.nextLong
import static org.springframework.batch.test.MetaDataInstanceFactory.createStepExecution

class RetryableTaskSpec extends Specification {

  def pipeline = Pipeline.builder().withStage("stage").build()
  def stepExecution = createStepExecution("stage.retryableTask", nextLong())
  def stepContext = new StepContext(stepExecution)
  def stepContribution = new StepContribution(stepExecution)
  def chunkContext = new ChunkContext(stepContext)

  def setup() {
    new PipelineInitializerTasklet(pipeline).execute(stepContribution, chunkContext)
  }

  void "should backoff when the task is not complete"() {
    setup:
    def step = Mock(RetryableTask) {
      getBackoffPeriod() >> 1000L
      getTimeout() >> 5000L
    }

    def tasklet = TaskTaskletAdapter.decorate(step)
    def timer = new StopWatch()
    timer.start()

    when:
    tasklet.execute(stepContribution, chunkContext)
    timer.stop()

    then:
    2 * step.execute(*_) >>> [new DefaultTaskResult(RUNNING), new DefaultTaskResult(SUCCEEDED)]
    timer.totalTimeMillis > 1000
  }
}
