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

import groovy.transform.CompileStatic
import java.time.Clock
import com.netflix.spinnaker.orca.RetryableTask
import com.netflix.spinnaker.orca.TaskResult
import com.netflix.spinnaker.orca.batch.exceptions.ExceptionHandler
import com.netflix.spinnaker.orca.batch.exceptions.TimeoutException
import com.netflix.spinnaker.orca.batch.retry.PollRequiresRetry
import com.netflix.spinnaker.orca.pipeline.model.Stage
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository
import org.springframework.batch.core.StepContribution
import org.springframework.batch.core.scope.context.ChunkContext
import org.springframework.batch.repeat.RepeatStatus

import static com.netflix.spinnaker.orca.pipeline.model.Stage.STAGE_TIMEOUT_OVERRIDE_KEY

@CompileStatic
class RetryableTaskTasklet extends TaskTasklet {
  private final Clock clock
  private final long timeoutMs

  RetryableTaskTasklet(RetryableTask task,
                       ExecutionRepository executionRepository,
                       List<ExceptionHandler> exceptionHandlers,
                       Clock clock = Clock.systemUTC()) {
    super(task, executionRepository, exceptionHandlers)
    this.clock = clock
    this.timeoutMs = task.timeout
  }

  @Override
  RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) {
    def status = super.execute(contribution, chunkContext)
    if (status.continuable) {
      throw new PollRequiresRetry()
    }
    return status
  }

  @Override
  protected TaskResult doExecuteTask(Stage stage, ChunkContext chunkContext) {
    def now = clock.millis()
    def startTime = chunkContext.stepContext.getStepExecution().startTime.time

    def timeoutMs = (stage.context[STAGE_TIMEOUT_OVERRIDE_KEY] ?: timeoutMs) as long
    if (now - startTime > timeoutMs) {
      throw new TimeoutException("Operation timed out after ${timeoutMs}ms")
    }
    return super.doExecuteTask(stage, chunkContext)
  }
}
