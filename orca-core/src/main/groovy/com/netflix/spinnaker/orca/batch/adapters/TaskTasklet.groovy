/*
 * Copyright 2014 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
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

import com.netflix.spinnaker.orca.ExecutionStatus
import com.netflix.spinnaker.orca.Task
import com.netflix.spinnaker.orca.TaskResult
import com.netflix.spinnaker.orca.batch.BatchStepStatus
import com.netflix.spinnaker.orca.pipeline.model.*
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository
import groovy.transform.CompileStatic
import groovy.transform.TypeCheckingMode
import org.springframework.batch.core.ExitStatus
import org.springframework.batch.core.StepContribution
import org.springframework.batch.core.scope.context.ChunkContext
import org.springframework.batch.core.step.tasklet.Tasklet
import org.springframework.batch.repeat.RepeatStatus

@CompileStatic
class TaskTasklet implements Tasklet {

  private final Task task
  private final ExecutionRepository executionRepository

  TaskTasklet(Task task, ExecutionRepository executionRepository) {
    this.task = task
    this.executionRepository = executionRepository
  }

  Class<? extends Task> getTaskType() {
    task.getClass()
  }

  @Override
  RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) {
    def stage = currentStage(chunkContext)

    try {
      def result = task.execute(stage.asImmutable())
      if (result.status == ExecutionStatus.TERMINAL) {
        chunkContext.stepContext.stepExecution.with {
          setTerminateOnly()
          executionContext.put("orcaTaskStatus", result.status)
          exitStatus = ExitStatus.FAILED
        }
      }

      stage.context.putAll result.outputs

      def batchStepStatus = BatchStepStatus.mapResult(result)
      chunkContext.stepContext.stepExecution.executionContext.put("orcaTaskStatus", result.status)
      contribution.exitStatus = batchStepStatus.exitStatus
      stage.endTime = !batchStepStatus.repeatStatus.continuable ? System.currentTimeMillis() : null

      return batchStepStatus.repeatStatus
    } finally {
      // because groovy...
      def execution = stage.execution
      if (execution instanceof Orchestration) {
        executionRepository.store(execution)
      } else if (execution instanceof Pipeline) {
        executionRepository.store(execution)
      }
    }
  }

  private Execution currentExecution(ChunkContext chunkContext) {
    if (chunkContext.stepContext.jobParameters.containsKey("pipeline")) {
      String id = chunkContext.stepContext.jobParameters.pipeline
      executionRepository.retrievePipeline(id)
    } else {
      String id = chunkContext.stepContext.jobParameters.orchestration
      executionRepository.retrieveOrchestration(id)
    }
  }

  private Stage currentStage(ChunkContext chunkContext) {
    currentExecution(chunkContext).namedStage(stageName(chunkContext))
  }

  private static String stageName(ChunkContext chunkContext) {
    chunkContext.stepContext.stepName.tokenize(".").first()
  }
}

