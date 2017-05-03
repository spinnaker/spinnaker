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
import com.netflix.spinnaker.orca.events.StageStarted
import com.netflix.spinnaker.orca.pipeline.StageDefinitionBuilder
import com.netflix.spinnaker.orca.pipeline.model.OptionalStageSupport
import com.netflix.spinnaker.orca.pipeline.model.Stage
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository
import com.netflix.spinnaker.orca.pipeline.util.ContextParameterProcessor
import com.netflix.spinnaker.orca.q.*
import com.netflix.spinnaker.orca.q.StartStage
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Component
import java.time.Clock

@Component
open class StartStageHandler @Autowired constructor(
  override val queue: Queue,
  override val repository: ExecutionRepository,
  override val stageDefinitionBuilders: Collection<StageDefinitionBuilder>,
  private val publisher: ApplicationEventPublisher,
  private val clock: Clock,
  private val contextParameterProcessor: ContextParameterProcessor
) : MessageHandler<StartStage>, StageBuilderAware {

  override fun handle(message: StartStage) {
    message.withStage { stage ->
      if (stage.allUpstreamStagesComplete()) {
        if (stage.shouldSkip()) {
          queue.push(CompleteStage(message, SKIPPED))
        } else {
          stage.plan()

          stage.setStatus(RUNNING)
          stage.setStartTime(clock.millis())
          repository.storeStage(stage)

          stage.start()
        }
        publisher.publishEvent(StageStarted(this, stage))
      }
    }
  }

  override val messageType = StartStage::class.java

  private fun Stage<*>.plan() {
    builder().let { builder ->
      builder.buildTasks(this)
      builder.buildSyntheticStages(this) { it: Stage<*> ->
        repository.addStage(it)
      }
    }
  }

  private fun Stage<*>.start() {
    val beforeStages = firstBeforeStages()
    if (beforeStages.isEmpty()) {
      val task = firstTask()
      if (task == null) {
        val afterStages = firstAfterStages()
        if (afterStages.isEmpty()) {
          queue.push(CompleteStage(this, SUCCEEDED))
        } else {
          afterStages.forEach {
            queue.push(StartStage(it))
          }
        }
      } else {
        queue.push(StartTask(this, task.id))
      }
    } else {
      beforeStages.forEach {
        queue.push(StartStage(it))
      }
    }
  }

  private fun Stage<*>.shouldSkip() =
    OptionalStageSupport.isOptional(this, contextParameterProcessor)
}
