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
import com.netflix.spinnaker.orca.PipelineStatus
import com.netflix.spinnaker.orca.RetryableTask
import com.netflix.spinnaker.orca.Task
import com.netflix.spinnaker.orca.pipeline.Pipeline
import com.netflix.spinnaker.orca.pipeline.Stage
import org.springframework.batch.core.ExitStatus
import org.springframework.batch.core.StepContribution
import org.springframework.batch.core.scope.context.ChunkContext
import org.springframework.batch.core.step.tasklet.Tasklet
import org.springframework.batch.repeat.RepeatStatus
import org.springframework.retry.annotation.Retryable
import static com.netflix.spinnaker.orca.batch.PipelineInitializerTasklet.PIPELINE_CONTEXT_KEY

@CompileStatic
@Retryable
class TaskTaskletAdapter implements Tasklet {

  static Tasklet decorate(Task task) {
    if (task instanceof RetryableTask) {
      new RetryableTaskTaskletAdapter(task)
    } else {
      new TaskTaskletAdapter(task)
    }
  }

  private final Task task

  protected TaskTaskletAdapter(Task task) {
    this.task = task
  }

  Class<? extends Task> getTaskType() {
    task.getClass()
  }

  @Override
  RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) {
    def stage = currentStage(chunkContext)

    def result = task.execute(stage)

    if (result.status == PipelineStatus.TERMINAL) {
      chunkContext.stepContext.stepExecution.with {
        setTerminateOnly()
        exitStatus = ExitStatus.FAILED.addExitDescription(result.status.name())
      }
    }

    stage.outputs.putAll(result.outputs)

    def batchStepStatus = BatchStepStatus.mapResult(result)
    contribution.exitStatus = batchStepStatus.exitStatus.addExitDescription(result.status.name())
    return batchStepStatus.repeatStatus
  }

  private Stage currentStage(ChunkContext chunkContext) {
    currentPipeline(chunkContext).namedStage(stageName(chunkContext))
  }

  private Pipeline currentPipeline(ChunkContext chunkContext) {
    (Pipeline) chunkContext.stepContext.stepExecution.jobExecution
                           .executionContext.get(PIPELINE_CONTEXT_KEY)
  }

  private String stageName(ChunkContext chunkContext) {
    chunkContext.stepContext.stepName.tokenize(".").first()
  }
}

