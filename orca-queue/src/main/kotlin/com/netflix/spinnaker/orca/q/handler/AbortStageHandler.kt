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
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository
import com.netflix.spinnaker.orca.q.*
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Component
import java.time.Clock

@Component
class AbortStageHandler(
  override val queue: Queue,
  override val repository: ExecutionRepository,
  private val publisher: ApplicationEventPublisher,
  private val clock: Clock
) : MessageHandler<AbortStage> {

  override fun handle(message: AbortStage) {
    message.withStage { stage ->
      if (stage.getStatus() in setOf(RUNNING, NOT_STARTED)) {
        stage.setStatus(TERMINAL)
        stage.setEndTime(clock.millis())
        repository.storeStage(stage)
        queue.push(CancelStage(message))
        if (stage.getParentStageId() == null) {
          queue.push(CompleteExecution(message))
        } else {
          queue.push(CompleteStage(stage.parent()))
        }
        publisher.publishEvent(StageComplete(this, stage))
      }
    }
  }

  override val messageType = AbortStage::class.java
}
