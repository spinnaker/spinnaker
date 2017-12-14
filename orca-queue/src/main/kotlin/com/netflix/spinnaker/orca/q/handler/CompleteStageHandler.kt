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

import com.netflix.spectator.api.Registry
import com.netflix.spectator.api.histogram.BucketCounter
import com.netflix.spinnaker.orca.ExecutionStatus.FAILED_CONTINUE
import com.netflix.spinnaker.orca.ExecutionStatus.NOT_STARTED
import com.netflix.spinnaker.orca.ExecutionStatus.RUNNING
import com.netflix.spinnaker.orca.ExecutionStatus.SUCCEEDED
import com.netflix.spinnaker.orca.events.StageComplete
import com.netflix.spinnaker.orca.pipeline.StageDefinitionBuilderFactory
import com.netflix.spinnaker.orca.pipeline.model.Stage
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository
import com.netflix.spinnaker.orca.pipeline.util.ContextParameterProcessor
import com.netflix.spinnaker.orca.q.CancelStage
import com.netflix.spinnaker.orca.q.CompleteExecution
import com.netflix.spinnaker.orca.q.CompleteStage
import com.netflix.spinnaker.orca.q.MessageHandler
import com.netflix.spinnaker.orca.q.Queue
import com.netflix.spinnaker.orca.q.StartStage
import com.netflix.spinnaker.orca.q.buildAfterStages
import com.netflix.spinnaker.orca.q.firstAfterStages
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Component
import java.time.Clock
import java.util.concurrent.TimeUnit

@Component
class CompleteStageHandler(
  override val queue: Queue,
  override val repository: ExecutionRepository,
  private val publisher: ApplicationEventPublisher,
  private val clock: Clock,
  override val contextParameterProcessor: ContextParameterProcessor,
  private val registry: Registry,
  override val stageDefinitionBuilderFactory: StageDefinitionBuilderFactory
) : MessageHandler<CompleteStage>, StageBuilderAware, ExpressionAware {

  override fun handle(message: CompleteStage) {
    message.withStage { stage ->
      if (stage.status in setOf(RUNNING, NOT_STARTED)) {
        val status = stage.determineStatus()
        if (status.isComplete && !status.isHalt) {
          // check to see if this stage has any unplanned synthetic after stages
          var afterStages = stage.firstAfterStages()
          if (afterStages.isEmpty()) {
            stage.planAfterStages()

            afterStages = stage.firstAfterStages()
            if (afterStages.isNotEmpty()) {
              afterStages.forEach {
                queue.push(StartStage(message, it.id))
              }

              return@withStage
            }
          }
        }

        stage.status = status
        stage.endTime = clock.millis()
        stage.includeExpressionEvaluationSummary()
        repository.storeStage(stage)

        if (status in listOf(SUCCEEDED, FAILED_CONTINUE)) {
          stage.startNext()
        } else {
          queue.push(CancelStage(message))
          if (stage.syntheticStageOwner == null) {
            log.debug("Stage has no synthetic owner, completing execution (original message: $message)")
            queue.push(CompleteExecution(message))
          } else {
            queue.push(message.copy(stageId = stage.parentStageId!!))
          }
        }

        publisher.publishEvent(StageComplete(this, stage))
        trackResult(stage)
      }
    }
  }

  private fun trackResult(stage: Stage) {
    // We only want to record durations of parent-level stages; not synthetics.
    if (stage.parentStageId != null) {
      return
    }

    val id = registry.createId("stage.invocations.duration")
      .withTag("status", stage.status.toString())
      .withTag("stageType", stage.type)
      .let { id ->
        // TODO rz - Need to check synthetics for their cloudProvider.
        stage.context["cloudProvider"]?.let {
          id.withTag("cloudProvider", it.toString())
        } ?: id
      }

    BucketCounter
      .get(registry, id, { v -> bucketDuration(v) })
      .record((stage.endTime ?: clock.millis()) - (stage.startTime ?: 0))
  }

  private fun bucketDuration(duration: Long) =
    when {
      duration > TimeUnit.MINUTES.toMillis(60) -> "gt60m"
      duration > TimeUnit.MINUTES.toMillis(30) -> "gt30m"
      duration > TimeUnit.MINUTES.toMillis(15) -> "gt15m"
      duration > TimeUnit.MINUTES.toMillis(5) -> "gt5m"
      else -> "lt5m"
    }

  override val messageType = CompleteStage::class.java

  /**
   * Plan any outstanding synthetic after stages.
   */
  private fun Stage.planAfterStages() {
    var hasPlannedStages = false

    builder().let { builder ->
      builder.buildAfterStages(this, builder.afterStages(this)) { it: Stage ->
        repository.addStage(it)
        hasPlannedStages = true
      }
    }

    if (hasPlannedStages) {
      this.execution = repository.retrieve(this.execution.type, this.execution.id)
    }
  }
}
