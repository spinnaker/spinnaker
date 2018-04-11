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

package com.netflix.spinnaker.orca.q.handler

import com.netflix.spinnaker.orca.CancellableStage
import com.netflix.spinnaker.orca.ExecutionStatus.RUNNING
import com.netflix.spinnaker.orca.Task
import com.netflix.spinnaker.orca.pipeline.StageDefinitionBuilderFactory
import com.netflix.spinnaker.orca.pipeline.model.Execution.ExecutionType.*
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository
import com.netflix.spinnaker.orca.q.CancelStage
import com.netflix.spinnaker.orca.q.RescheduleExecution
import com.netflix.spinnaker.orca.q.RunTask
import com.netflix.spinnaker.q.Queue
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Component
import java.util.concurrent.Executor

@Component
class CancelStageHandler(
  override val queue: Queue,
  override val repository: ExecutionRepository,
  override val stageDefinitionBuilderFactory: StageDefinitionBuilderFactory,
  @Qualifier("messageHandlerPool") private val executor: Executor
) : OrcaMessageHandler<CancelStage>, StageBuilderAware {

  override val messageType = CancelStage::class.java

  override fun handle(message: CancelStage) {
    message.withStage { stage ->
      /**
       * When an execution ends with status !SUCCEEDED, still-running stages
       * remain in the RUNNING state until their running tasks are dequeued
       * to RunTaskHandler. For tasks leveraging getDynamicBackoffPeriod(),
       * stages may incorrectly report as RUNNING for a considerable length
       * of time, unless we short-circuit their backoff time.
       *
       * For !SUCCEEDED executions, CompleteExecutionHandler enqueues CancelStage
       * messages for all top-level stages. For stages still RUNNING, we requeue
       * RunTask messages for any RUNNING tasks, for immediate execution. This
       * ensures prompt stage cancellation and correct handling of onFailure or
       * cancel conditions. This is safe as RunTaskHandler validates execution
       * status before processing work. RunTask messages are idempotent for
       * cancelled executions, though additional work is generally avoided due
       * to queue deduplication.
       *
       */
      if (stage.status == RUNNING) {
        stage.tasks
          .filter { it.status == RUNNING }
          .forEach {
            queue.reschedule(
              RunTask(
                stage.execution.type,
                stage.execution.id,
                stage.execution.application,
                stage.id,
                it.id,
                it.type
              )
            )
          }
      }
      if (stage.status.isHalt) {
        stage.builder().let { builder ->
          if (builder is CancellableStage) {
            // for the time being we execute this off-thread as some cancel
            // routines may run long enough to cause message acknowledgment to
            // time out.
            executor.execute {
              builder.cancel(stage)
              // Special case for PipelineStage to ensure prompt cancellation of
              // child pipelines and deployment strategies regardless of task backoff
              if (stage.type.equals("pipeline", true) && stage.context.containsKey("executionId")) {
                val childId = stage.context["executionId"] as? String
                if (childId != null) {
                  val child = repository.retrieve(PIPELINE, childId)
                  queue.push(RescheduleExecution(child))
                }
              }
            }
          }
        }
      }
    }
  }

  @Suppress("UNCHECKED_CAST")
  private val com.netflix.spinnaker.orca.pipeline.model.Task.type
    get() = Class.forName(implementingClass) as Class<out Task>
}
