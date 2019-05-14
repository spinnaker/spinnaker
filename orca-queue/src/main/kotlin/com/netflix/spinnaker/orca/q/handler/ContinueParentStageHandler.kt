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
import com.netflix.spinnaker.orca.ext.allAfterStagesComplete
import com.netflix.spinnaker.orca.ext.allBeforeStagesSuccessful
import com.netflix.spinnaker.orca.ext.anyBeforeStagesFailed
import com.netflix.spinnaker.orca.ext.hasTasks
import com.netflix.spinnaker.orca.pipeline.model.Stage
import com.netflix.spinnaker.orca.pipeline.model.SyntheticStageOwner.STAGE_BEFORE
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository
import com.netflix.spinnaker.orca.q.CompleteStage
import com.netflix.spinnaker.orca.q.ContinueParentStage
import com.netflix.spinnaker.orca.q.StartTask
import com.netflix.spinnaker.q.Queue
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.time.Duration

@Component
class ContinueParentStageHandler(
  override val queue: Queue,
  override val repository: ExecutionRepository,
  @Value("\${queue.retry.delay.ms:5000}") retryDelayMs: Long
) : OrcaMessageHandler<ContinueParentStage> {

  private val log: Logger = LoggerFactory.getLogger(javaClass)
  private val retryDelay = Duration.ofMillis(retryDelayMs)

  override fun handle(message: ContinueParentStage) {
    message.withStage { stage ->
      if (message.phase == STAGE_BEFORE) {
        if (stage.allBeforeStagesSuccessful()) {
          when {
            stage.hasTasks() -> stage.runFirstTask()
            else -> queue.push(CompleteStage(stage))
          }
        } else if (!stage.anyBeforeStagesFailed()) {
          log.info("Re-queuing $message as other ${message.phase} stages are still running")
          queue.push(message, retryDelay)
        }
      } else {
        if (stage.allAfterStagesComplete()) {
          queue.push(CompleteStage(stage))
        } else {
          log.info("Re-queuing $message as other ${message.phase} stages are still running")
          queue.push(message, retryDelay)
        }
      }
    }
  }

  private fun Stage.runFirstTask() {
    val firstTask = tasks.first()
    if (firstTask.status == NOT_STARTED) {
      queue.push(StartTask(this, firstTask))
    } else {
      log.warn("Ignoring $messageType for $id as tasks are already running")
    }
  }

  override val messageType = ContinueParentStage::class.java
}
