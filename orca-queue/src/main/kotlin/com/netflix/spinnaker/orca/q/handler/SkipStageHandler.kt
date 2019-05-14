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

import com.netflix.spinnaker.orca.ExecutionStatus.FAILED_CONTINUE
import com.netflix.spinnaker.orca.ExecutionStatus.NOT_STARTED
import com.netflix.spinnaker.orca.ExecutionStatus.RUNNING
import com.netflix.spinnaker.orca.ExecutionStatus.SKIPPED
import com.netflix.spinnaker.orca.ExecutionStatus.SUCCEEDED
import com.netflix.spinnaker.orca.ExecutionStatus.TERMINAL
import com.netflix.spinnaker.orca.events.StageComplete
import com.netflix.spinnaker.orca.ext.isManuallySkipped
import com.netflix.spinnaker.orca.ext.recursiveSyntheticStages
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository
import com.netflix.spinnaker.orca.q.SkipStage
import com.netflix.spinnaker.q.Queue
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Component
import java.time.Clock

@Component
class SkipStageHandler(
  override val queue: Queue,
  override val repository: ExecutionRepository,
  @Qualifier("queueEventPublisher") private val publisher: ApplicationEventPublisher,
  private val clock: Clock
) : OrcaMessageHandler<SkipStage> {
  override fun handle(message: SkipStage) {
    message.withStage { stage ->
      if (stage.status in setOf(RUNNING, NOT_STARTED) || stage.isManuallySkipped()) {
        stage.status = SKIPPED
        if (stage.isManuallySkipped()) {
          stage.recursiveSyntheticStages().forEach {
            if (it.status !in setOf(SUCCEEDED, TERMINAL, FAILED_CONTINUE)) {
              it.status = SKIPPED
              it.endTime = clock.millis()
              repository.storeStage(it)
              publisher.publishEvent(StageComplete(this, it))
            }
          }
        }
        stage.endTime = clock.millis()
        repository.storeStage(stage)
        stage.startNext()
        publisher.publishEvent(StageComplete(this, stage))
      }
    }
  }

  override val messageType = SkipStage::class.java
}
