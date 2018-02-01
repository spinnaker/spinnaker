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

import com.netflix.spinnaker.orca.ExecutionStatus.NOT_STARTED
import com.netflix.spinnaker.orca.ExecutionStatus.RUNNING
import com.netflix.spinnaker.orca.pipeline.StageDefinitionBuilderFactory
import com.netflix.spinnaker.orca.pipeline.model.Stage
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository
import com.netflix.spinnaker.orca.q.RestartStage
import com.netflix.spinnaker.orca.q.StartStage
import com.netflix.spinnaker.q.Queue
import org.springframework.stereotype.Component
import java.time.Clock

@Component
class RestartStageHandler(
  override val queue: Queue,
  override val repository: ExecutionRepository,
  override val stageDefinitionBuilderFactory: StageDefinitionBuilderFactory,
  private val clock: Clock
) : OrcaMessageHandler<RestartStage>, StageBuilderAware {

  override val messageType = RestartStage::class.java

  override fun handle(message: RestartStage) {
    message.withStage { stage ->
      // If RestartStage is requested for a synthetic stage, operate on its parent
      val topStage = stage.topLevelStage
      val startMessage = StartStage(message.executionType, message.executionId, message.application, topStage.id)
      if (topStage.status.isComplete) {
        topStage.addRestartDetails(message.user)
        topStage.reset()
        repository.updateStatus(topStage.execution.id, RUNNING)
        queue.push(StartStage(startMessage))
      }
    }
  }

  private fun Stage.addRestartDetails(user: String?) {
    context["restartDetails"] = mapOf(
      "restartedBy" to (user ?: "anonymous"),
      "restartTime" to clock.millis(),
      "previousException" to context.remove("exception")
    )
  }

  private fun Stage.reset() {
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

  private fun Stage.removeSynthetics() {
    execution
      .stages
      .filter { it.parentStageId == id }
      .forEach {
        it.removeSynthetics()
        repository.removeStage(execution, it.id)
      }
  }
}
