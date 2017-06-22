/*
 * Copyright 2017 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.orca.batch

import java.time.Clock
import java.util.concurrent.TimeUnit
import com.netflix.spectator.api.Registry
import com.netflix.spinnaker.orca.ExecutionStatus
import com.netflix.spinnaker.orca.RetryableTask
import com.netflix.spinnaker.orca.TaskResult
import com.netflix.spinnaker.orca.batch.exceptions.ExceptionHandler
import com.netflix.spinnaker.orca.batch.exceptions.TimeoutException
import com.netflix.spinnaker.orca.batch.retry.PollRequiresRetry
import com.netflix.spinnaker.orca.pipeline.model.Execution.PausedDetails
import com.netflix.spinnaker.orca.pipeline.model.Stage
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository
import com.netflix.spinnaker.orca.pipeline.util.ContextParameterProcessor
import com.netflix.spinnaker.orca.pipeline.util.StageNavigator
import groovy.transform.CompileStatic
import org.joda.time.Duration
import org.joda.time.format.PeriodFormat
import org.springframework.batch.core.StepContribution
import org.springframework.batch.core.scope.context.ChunkContext
import org.springframework.batch.repeat.RepeatStatus
import static com.netflix.spinnaker.orca.pipeline.model.Stage.STAGE_TIMEOUT_OVERRIDE_KEY

@CompileStatic
class RetryableTaskTasklet extends TaskTasklet {
  static final long MAX_PAUSE_TIME_MS = TimeUnit.DAYS.toMillis(3)

  private final Clock clock
  private final long timeoutMs

  RetryableTaskTasklet(RetryableTask task,
                       ExecutionRepository executionRepository,
                       List<ExceptionHandler> exceptionHandlers,
                       Registry registry,
                       StageNavigator stageNavigator,
                       ContextParameterProcessor contextParameterProcessor,
                       Clock clock = Clock.systemUTC()) {
    super(task, executionRepository, exceptionHandlers, registry, stageNavigator, contextParameterProcessor)
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
    if (stage.execution.paused?.isPaused()) {
      checkTimeout(clock.millis() - stage.execution.paused.pauseTime, MAX_PAUSE_TIME_MS)
    }

    if (stage.execution.status == ExecutionStatus.PAUSED) {
      // If the pipeline is PAUSED, ensure that the current stage and all running tasks are PAUSED
      stage.status = ExecutionStatus.PAUSED
      stage.tasks.findAll { it.status == ExecutionStatus.RUNNING }.each {
        it.status = ExecutionStatus.PAUSED
      }
      return new TaskResult(ExecutionStatus.PAUSED)
    } else if (stage.execution.status == ExecutionStatus.RUNNING) {
      // If the pipeline is RUNNING, ensure that the current stage and all paused tasks are RUNNING
      stage.status = ExecutionStatus.RUNNING
      stage.tasks.findAll { it.status == ExecutionStatus.PAUSED }.each {
        it.status = ExecutionStatus.RUNNING
      }
    }

    def startTime = chunkContext.stepContext.getStepExecution().startTime.time
    def executionTimeMs = determineCurrentExecutionTime(clock, startTime, stage.execution.paused)
    def timeoutMs = (stage.context[STAGE_TIMEOUT_OVERRIDE_KEY] ?: timeoutMs) as long

    if (executionTimeMs > timeoutMs && stage.context['markSuccessfulOnTimeout']) {
      return new TaskResult(ExecutionStatus.SUCCEEDED)
    }

    checkTimeout(executionTimeMs, timeoutMs)
    return super.doExecuteTask(stage, chunkContext)
  }

  /**
   * In order to accurately determine whether this task has timed out, any time spent in a paused state must be discounted.
   */
  private static long determineCurrentExecutionTime(Clock clock, long startTime, PausedDetails pausedDetails) {
    return clock.millis() - ((pausedDetails?.pausedMs ?: 0) as Long) - startTime
  }

  private static void checkTimeout(long elapsedTimeMs, long timeoutMs) {
    if (elapsedTimeMs > timeoutMs) {
      def formatter = PeriodFormat.getDefault()
      def dur = formatter.print(new Duration(timeoutMs).toPeriod())
      throw new TimeoutException("Operation timed out after ${dur}")
    }
  }
}
