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

import com.netflix.spinnaker.orca.api.pipeline.models.ExecutionStatus.NOT_STARTED
import com.netflix.spinnaker.orca.api.pipeline.models.ExecutionStatus.RUNNING
import com.netflix.spinnaker.orca.api.pipeline.models.StageExecution
import com.netflix.spinnaker.orca.pipeline.StageDefinitionBuilderFactory
import com.netflix.spinnaker.orca.pipeline.model.PipelineTrigger
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository
import com.netflix.spinnaker.orca.q.RestartStage
import com.netflix.spinnaker.orca.q.StartStage
import com.netflix.spinnaker.orca.q.pending.PendingExecutionService
import com.netflix.spinnaker.q.Queue
import java.time.Clock
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@Component
class RestartStageHandler(
  override val queue: Queue,
  override val repository: ExecutionRepository,
  override val stageDefinitionBuilderFactory: StageDefinitionBuilderFactory,
  private val pendingExecutionService: PendingExecutionService,
  private val clock: Clock
) : OrcaMessageHandler<RestartStage>, StageBuilderAware {

  override val messageType = RestartStage::class.java

  private val log: Logger get() = LoggerFactory.getLogger(javaClass)

  override fun handle(message: RestartStage) {
    message.withStage { stage ->
      if (stage.execution.shouldQueue()) {
        // this pipeline is already running and has limitConcurrent = true
        stage.execution.pipelineConfigId?.let {
          log.info("Queueing restart of {} {} {}", stage.execution.application, stage.execution.name, stage.execution.id)
          pendingExecutionService.enqueue(it, message)
        }
      } else {
        // If RestartStage is requested for a synthetic stage, operate on its parent
        val topStage = stage.topLevelStage
        val startMessage = StartStage(message.executionType, message.executionId, message.application, topStage.id)
        if (topStage.status.isComplete) {
          topStage.addRestartDetails(message.user)
          topStage.reset()
          restartParentPipelineIfNeeded(message, topStage)
          repository.updateStatus(topStage.execution.type, topStage.execution.id, RUNNING)
          queue.push(StartStage(startMessage))
        }
      }
    }
  }

  private fun restartParentPipelineIfNeeded(message: RestartStage, topStage: StageExecution) {
    if (topStage.execution.trigger !is PipelineTrigger) {
      return
    }

    val trigger = topStage.execution.trigger as PipelineTrigger
    // We have a copy of the parent execution, not the live one. So we retrieve the live one.
    val parentExecution = repository.retrieve(trigger.parentExecution.type, trigger.parentExecution.id)

    if (!parentExecution.status.isComplete()) {
      // only attempt to restart the parent pipeline if it's not running
      return
    }

    val parentStage = parentExecution.stageById(trigger.parentPipelineStageId)
    parentStage.addSkipRestart()
    repository.storeStage(parentStage)

    queue.push(RestartStage(trigger.parentExecution, parentStage.id, message.user))
  }

  /**
   * Inform the parent stage when it restarts that the child is already running
   */
  private fun StageExecution.addSkipRestart() {
    context["_skipPipelineRestart"] = true
  }

  private fun StageExecution.addRestartDetails(user: String?) {
    context["restartDetails"] = mapOf(
      "restartedBy" to (user ?: "anonymous"),
      "restartTime" to clock.millis(),
      "previousException" to context.remove("exception")
    )
  }

  private fun StageExecution.reset() {
    if (status.isComplete) {
      status = NOT_STARTED
      startTime = null
      endTime = null
      tasks = emptyList()
      builder().prepareStageForRestart(this)
      repository.storeStage(this)

      removeSynthetics()
    }

    downstreamStages().forEach { it.reset() }
  }

  private fun StageExecution.removeSynthetics() {
    execution
      .stages
      .filter { it.parentStageId == id }
      .forEach {
        it.removeSynthetics()
        repository.removeStage(execution, it.id)
      }
  }
}
