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
import com.netflix.spinnaker.orca.ExecutionStatus.NOT_STARTED
import com.netflix.spinnaker.orca.ExecutionStatus.RUNNING
import com.netflix.spinnaker.orca.events.StageStarted
import com.netflix.spinnaker.orca.exceptions.ExceptionHandler
import com.netflix.spinnaker.orca.pipeline.StageDefinitionBuilderFactory
import com.netflix.spinnaker.orca.pipeline.expressions.PipelineExpressionEvaluator
import com.netflix.spinnaker.orca.pipeline.model.Execution.ExecutionType.PIPELINE
import com.netflix.spinnaker.orca.pipeline.model.OptionalStageSupport
import com.netflix.spinnaker.orca.pipeline.model.Stage
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository
import com.netflix.spinnaker.orca.pipeline.util.ContextParameterProcessor
import com.netflix.spinnaker.orca.pipeline.util.StageNavigator
import com.netflix.spinnaker.orca.q.*
import com.netflix.spinnaker.orca.q.StartStage
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Component
import java.time.Clock
import java.time.Duration

@Component
class StartStageHandler(
  override val queue: Queue,
  override val repository: ExecutionRepository,
  override val stageNavigator: StageNavigator,
  override val stageDefinitionBuilderFactory: StageDefinitionBuilderFactory,
  override val contextParameterProcessor: ContextParameterProcessor,
  private val publisher: ApplicationEventPublisher,
  private val exceptionHandlers: List<ExceptionHandler>,
  private val objectMapper: ObjectMapper,
  private val clock: Clock,
  @Value("\${queue.retry.delay.ms:15000}") retryDelayMs: Long
) : MessageHandler<StartStage>, StageBuilderAware, ExpressionAware, AuthenticationAware {

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
          } catch(e: Exception) {
            val exceptionDetails = exceptionHandlers.shouldRetry(e, stage.name)
            if (exceptionDetails?.shouldRetry == true) {
              val attempts = message.getAttribute<AttemptsAttribute>()?.attempts ?: 0
              log.warn("Error planning ${stage.type} stage for ${message.executionType}[${message.executionId}] (attempts: $attempts)")

              message.setAttribute(MaxAttemptsAttribute(40))
              queue.push(message, retryDelay)
            } else {
              log.error("Error running ${stage.type} stage for ${message.executionType}[${message.executionId}]", e)
              stage.context["exception"] = exceptionDetails
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

  override val messageType = StartStage::class.java

  private fun Stage.plan() {
    builder().let { builder ->
      builder.buildTasks(this)
      builder.buildSyntheticStages(this) { it: Stage ->
        repository.addStage(it)
      }
    }
  }

  private fun Stage.start() {
    val beforeStages = firstBeforeStages()
    if (beforeStages.isEmpty()) {
      val task = firstTask()
      if (task == null) {
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
}
