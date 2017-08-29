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

import com.netflix.spinnaker.orca.ExecutionStatus.*
import com.netflix.spinnaker.orca.events.StageComplete
import com.netflix.spinnaker.orca.pipeline.model.Stage
import com.netflix.spinnaker.orca.pipeline.model.SyntheticStageOwner.STAGE_AFTER
import com.netflix.spinnaker.orca.pipeline.model.SyntheticStageOwner.STAGE_BEFORE
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository
import com.netflix.spinnaker.orca.q.*
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Component
import java.time.Clock

@Component
class CompleteStageHandler(
  override val queue: Queue,
  override val repository: ExecutionRepository,
  private val publisher: ApplicationEventPublisher,
  private val clock: Clock
) : MessageHandler<CompleteStage> {

  private val log = LoggerFactory.getLogger(javaClass)

  override fun handle(message: CompleteStage) {
    message.withStage { stage ->
      if (stage.getStatus() in setOf(RUNNING, NOT_STARTED)) {
        stage.setStatus(message.status)
        stage.setEndTime(clock.millis())
        repository.storeStage(stage)

        if (message.status in listOf(SUCCEEDED, FAILED_CONTINUE, SKIPPED)) {
          stage.startNext()
        } else {
          queue.push(CancelStage(message))
          if (stage.getSyntheticStageOwner() == null) {
            log.debug("Stage has no synthetic owner, completing execution (original message: $message)")
            queue.push(CompleteExecution(message))
          } else {
            queue.push(message.copy(stageId = stage.getParentStageId()!!))
          }
        }

        publisher.publishEvent(StageComplete(this, stage))
      }
    }
  }

  override val messageType = CompleteStage::class.java

  private fun Stage<*>.startNext() {
    getExecution().let { execution ->
      val downstreamStages = downstreamStages()
      if (downstreamStages.isNotEmpty()) {
        downstreamStages.forEach {
          queue.push(StartStage(it))
        }
      } else if (getSyntheticStageOwner() == STAGE_BEFORE) {
        queue.push(ContinueParentStage(parent()))
      } else if (getSyntheticStageOwner() == STAGE_AFTER) {
        parent().let { parent ->
          queue.push(CompleteStage(parent, SUCCEEDED))
        }
      } else {
        log.debug("No stages waiting to start, completing execution (executionId: ${getExecution().getId()}, stageId: ${getId()})")
        queue.push(CompleteExecution(execution))
      }
    }
  }
}
