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
import com.netflix.spinnaker.orca.ExecutionStatus.SUCCEEDED
import com.netflix.spinnaker.orca.pipeline.model.Stage
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository
import com.netflix.spinnaker.orca.q.*
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
) : MessageHandler<ContinueParentStage> {

  private val log: Logger = LoggerFactory.getLogger(javaClass)
  private val retryDelay = Duration.ofMillis(retryDelayMs)

  override fun handle(message: ContinueParentStage) {
    message.withStage { stage ->
      if (stage.allBeforeStagesComplete()) {
        if (stage.hasTasks()) {
          stage.runFirstTask()
        } else if (stage.hasAfterStages()) {
          stage.runAfterStages()
        } else {
          queue.push(CompleteStage(stage, SUCCEEDED))
        }
      } else if (!stage.anyBeforeStagesFailed()) {
        log.warn("Re-queuing $message as other BEFORE stages are still running")
        queue.push(message, retryDelay)
      }
    }
  }

  private fun Stage<*>.runFirstTask() {
    val firstTask = getTasks().first()
    if (firstTask.status == NOT_STARTED) {
      queue.push(StartTask(this, firstTask))
    } else {
      log.warn("Ignoring $messageType for ${getId()} as tasks are already running")
    }
  }

  private fun Stage<*>.runAfterStages() {
    val afterStages = firstAfterStages()
    if (afterStages.all { it.getStatus() == NOT_STARTED }) {
      afterStages.forEach {
        queue.push(StartStage(it))
      }
    } else {
      log.warn("Ignoring $messageType for ${getId()} as AFTER stages are already running")
    }
  }

  override val messageType = ContinueParentStage::class.java
}
