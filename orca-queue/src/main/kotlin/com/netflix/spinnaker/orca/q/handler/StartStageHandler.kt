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

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spectator.api.Registry
import com.netflix.spinnaker.orca.ExecutionStatus.NOT_STARTED
import com.netflix.spinnaker.orca.ExecutionStatus.RUNNING
import com.netflix.spinnaker.orca.events.StageStarted
import com.netflix.spinnaker.orca.exceptions.ExceptionHandler
import com.netflix.spinnaker.orca.ext.*
import com.netflix.spinnaker.orca.pipeline.StageDefinitionBuilderFactory
import com.netflix.spinnaker.orca.pipeline.expressions.PipelineExpressionEvaluator
import com.netflix.spinnaker.orca.pipeline.model.Execution.ExecutionType.PIPELINE
import com.netflix.spinnaker.orca.pipeline.model.OptionalStageSupport
import com.netflix.spinnaker.orca.pipeline.model.Stage
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository
import com.netflix.spinnaker.orca.pipeline.util.ContextParameterProcessor
import com.netflix.spinnaker.orca.pipeline.util.StageNavigator
import com.netflix.spinnaker.orca.q.*
import com.netflix.spinnaker.q.AttemptsAttribute
import com.netflix.spinnaker.q.MaxAttemptsAttribute
import com.netflix.spinnaker.q.Queue
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Component
import java.time.Clock
import java.time.Duration
import java.time.Instant
import kotlin.collections.set

@Component
class StartStageHandler(
  override val queue: Queue,
  override val repository: ExecutionRepository,
  override val stageNavigator: StageNavigator,
  override val stageDefinitionBuilderFactory: StageDefinitionBuilderFactory,
  override val contextParameterProcessor: ContextParameterProcessor,
  @Qualifier("queueEventPublisher") private val publisher: ApplicationEventPublisher,
  private val exceptionHandlers: List<ExceptionHandler>,
  @Qualifier("mapper") private val objectMapper: ObjectMapper,
  private val clock: Clock,
  private val registry: Registry,
  @Value("\${queue.retry.delay.ms:15000}") retryDelayMs: Long
) : OrcaMessageHandler<StartStage>, StageBuilderAware, ExpressionAware, AuthenticationAware {

  private val retryDelay = Duration.ofMillis(retryDelayMs)

  override fun handle(message: StartStage) {
    message.withStage { stage ->
      if (stage.anyUpstreamStagesFailed()) {
        // this only happens in restart scenarios
        log.warn("Tried to start stage ${stage.id} but something upstream had failed (executionId: ${message.executionId})")
        queue.push(CompleteExecution(message))
      } else if (stage.allUpstreamStagesComplete()) {
        if (stage.status != NOT_STARTED) {
          log.warn("Ignoring $message as stage is already ${stage.status}")
        } else if (stage.shouldSkip()) {
          queue.push(SkipStage(message))
        } else if (stage.isAfterStartTimeExpiry()) {
          log.warn("Stage is being skipped because its start time is after TTL (stageId: ${stage.id}, executionId: ${message.executionId})")
          queue.push(SkipStage(stage))
        } else {
          try {
            stage.withAuth {
              stage.plan()
            }

            stage.status = RUNNING
            stage.startTime = clock.millis()
            repository.storeStage(stage)

            stage.start()

            publisher.publishEvent(StageStarted(this, stage))
            trackResult(stage)
          } catch(e: Exception) {
            val exceptionDetails = exceptionHandlers.shouldRetry(e, stage.name)
            if (exceptionDetails?.shouldRetry == true) {
              val attempts = message.getAttribute<AttemptsAttribute>()?.attempts ?: 0
              log.warn("Error planning ${stage.type} stage for ${message.executionType}[${message.executionId}] (attempts: $attempts)")

              message.setAttribute(MaxAttemptsAttribute(40))
              queue.push(message, retryDelay)
            } else {
              log.error("Error running ${stage.type} stage for ${message.executionType}[${message.executionId}]", e)
              stage.apply {
                context["exception"] = exceptionDetails
                context["beforeStagePlanningFailed"] = true
              }
              repository.storeStage(stage)
              queue.push(CompleteStage(message))
            }
          }
        }
      } else {
        log.warn("Re-queuing $message as upstream stages are not yet complete")
        queue.push(message, retryDelay)
      }
    }
  }

  private fun trackResult(stage: Stage) {
    // We only want to record invocations of parent-level stages; not synthetics
    if (stage.parentStageId != null) {
      return
    }

    val id = registry.createId("stage.invocations")
      .withTag("type", stage.type)
      .withTag("application", stage.execution.application)
      .let { id ->
        // TODO rz - Need to check synthetics for their cloudProvider.
        stage.context["cloudProvider"]?.let {
          id.withTag("cloudProvider", it.toString())
        } ?: id
      }
    registry.counter(id).increment()
  }

  override val messageType = StartStage::class.java

  private fun Stage.plan() {
    builder().let { builder ->
      //if we have a top level stage, ensure that context expressions are processed
      val mergedStage = if (this.parentStageId == null) this.withMergedContext() else this
      builder.addContextFlags(mergedStage)
      builder.buildTasks(mergedStage)
      builder.buildBeforeStages(mergedStage) { it: Stage ->
        repository.addStage(it.withMergedContext())
      }
    }
  }

  private fun Stage.start() {
    val beforeStages = firstBeforeStages()
    if (beforeStages.isEmpty()) {
      val task = firstTask()
      if (task == null) {
        // TODO: after stages are no longer planned at this point. We could skip this
        val afterStages = firstAfterStages()
        if (afterStages.isEmpty()) {
          queue.push(CompleteStage(this))
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

  private fun Stage.shouldSkip(): Boolean {
    if (this.execution.type != PIPELINE) {
      return false
    }

    val clonedContext = objectMapper.convertValue(this.context, Map::class.java) as Map<String, Any>
    val clonedStage = Stage(this.execution, this.type, clonedContext).also {
      it.refId = refId
      it.requisiteStageRefIds = requisiteStageRefIds
      it.syntheticStageOwner = syntheticStageOwner
      it.parentStageId = parentStageId
    }
    if (clonedStage.context.containsKey(PipelineExpressionEvaluator.SUMMARY)) {
      this.context.put(PipelineExpressionEvaluator.SUMMARY, clonedStage.context[PipelineExpressionEvaluator.SUMMARY])
    }

    return OptionalStageSupport.isOptional(clonedStage.withMergedContext(), contextParameterProcessor)
  }

  private fun Stage.isAfterStartTimeExpiry(): Boolean =
    startTimeExpiry?.let { Instant.ofEpochMilli(it) }?.isBefore(clock.instant()) ?: false
}
