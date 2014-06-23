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

import groovy.transform.CompileStatic
import com.netflix.spinnaker.orca.Task
import org.springframework.batch.core.StepContribution
import org.springframework.batch.core.scope.context.ChunkContext
import org.springframework.batch.core.step.tasklet.Tasklet
import org.springframework.batch.repeat.RepeatStatus
import org.springframework.retry.annotation.Retryable

@CompileStatic
@Retryable
class TaskTaskletAdapter implements Tasklet {

  private final Task task

  TaskTaskletAdapter(Task task) {
    this.task = task
  }

  static Tasklet decorate(Task task) {
    new TaskTaskletAdapter(task)
  }

  Class<? extends Task> getTaskType() {
    task.getClass()
  }

  @Override
  RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) {
    def jobExecutionContext = chunkContext.stepContext.stepExecution.jobExecution.executionContext
    def stepExecutionContext = chunkContext.stepContext.stepExecution.executionContext

    def result = task.execute(new ChunkContextAdapter(chunkContext))

    // TODO: could consider extending ExecutionContextPromotionListener in order to do this but then we need to know exactly which keys to promote
    def executionContext = result.status.complete ? jobExecutionContext : stepExecutionContext
    result.outputs.each { k, v ->
      executionContext.put(k, v)
    }

    def batchStepStatus = BatchStepStatus.mapResult(result)
    contribution.exitStatus = batchStepStatus.exitStatus
    return batchStepStatus.repeatStatus
  }
}

