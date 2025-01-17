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
import com.netflix.spinnaker.orca.TaskImplementationResolver
import com.netflix.spinnaker.orca.api.pipeline.models.ExecutionStatus
import com.netflix.spinnaker.orca.api.pipeline.models.ExecutionStatus.NOT_STARTED
import com.netflix.spinnaker.orca.api.pipeline.models.ExecutionStatus.RUNNING
import com.netflix.spinnaker.orca.api.pipeline.models.ExecutionType.PIPELINE
import com.netflix.spinnaker.orca.api.pipeline.models.StageExecution
import com.netflix.spinnaker.orca.events.StageStarted
import com.netflix.spinnaker.orca.exceptions.ExceptionHandler
import com.netflix.spinnaker.orca.ext.allUpstreamStagesComplete
import com.netflix.spinnaker.orca.ext.firstAfterStages
import com.netflix.spinnaker.orca.ext.firstBeforeStages
import com.netflix.spinnaker.orca.ext.firstTask
import com.netflix.spinnaker.orca.ext.upstreamStages
import com.netflix.spinnaker.orca.pipeline.StageDefinitionBuilderFactory
import com.netflix.spinnaker.orca.pipeline.expressions.PipelineExpressionEvaluator
import com.netflix.spinnaker.orca.pipeline.model.OptionalStageSupport
import com.netflix.spinnaker.orca.pipeline.model.StageExecutionImpl
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository
import com.netflix.spinnaker.orca.pipeline.util.ContextParameterProcessor
import com.netflix.spinnaker.orca.pipeline.util.StageNavigator
import com.netflix.spinnaker.orca.q.CompleteExecution
import com.netflix.spinnaker.orca.q.CompleteStage
import com.netflix.spinnaker.orca.q.SkipStage
import com.netflix.spinnaker.orca.q.StartStage
import com.netflix.spinnaker.orca.q.StartTask
import com.netflix.spinnaker.orca.q.addContextFlags
import com.netflix.spinnaker.orca.q.buildBeforeStages
import com.netflix.spinnaker.orca.q.buildTasks
import com.netflix.spinnaker.q.AttemptsAttribute
import com.netflix.spinnaker.q.MaxAttemptsAttribute
import com.netflix.spinnaker.q.Queue
import java.time.Clock
import java.time.Duration
import java.time.Instant
import kotlin.collections.set
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Component

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
  @Value("\${queue.retry.delay.ms:15000}") retryDelayMs: Long,
  private val taskImplementationResolver: TaskImplementationResolver
) : OrcaMessageHandler<StartStage>, StageBuilderAware, ExpressionAware, AuthenticationAware {

  private val retryDelay = Duration.ofMillis(retryDelayMs)

  override fun handle(message: StartStage) {
    message.withStage { stage ->
      try {
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
            stage.withAuth {
              try {
                // Set the startTime in case we throw an exception.
                stage.startTime = clock.millis()
                stage.plan()
                stage.status = RUNNING
                repository.storeStage(stage)

                stage.start()

                publisher.publishEvent(StageStarted(this, stage))
                trackResult(stage)
              } catch (e: Exception) {
                val exceptionDetails = exceptionHandlers.shouldRetry(e, stage.name)
                if (exceptionDetails?.shouldRetry == true) {
                  val attempts = message.getAttribute<AttemptsAttribute>()?.attempts ?: 0
                  log.warn("Error planning ${stage.type} stage for ${message.executionType}[${message.executionId}] (attempts: $attempts)")

                  message.setAttribute(MaxAttemptsAttribute(40))
                  queue.push(message, retryDelay)
                } else {
                  log.error("Error running ${stage.type}[${stage.id}] stage for ${message.executionType}[${message.executionId}]", e)
                  stage.apply {
                    context["exception"] = exceptionDetails
                    context["beforeStagePlanningFailed"] = true
                  }
                  repository.storeStage(stage)
                  queue.push(CompleteStage(message))
                }
              }
            }
          }
        } else {
          log.info("Re-queuing $message as upstream stages are not yet complete")
          queue.push(message, retryDelay)
        }

      } catch (e: Exception) {
        log.error("Error running ${stage.type}[${stage.id}] stage for ${message.executionType}[${message.executionId}]", e)

        stage.apply {
          val exceptionDetails = exceptionHandlers.shouldRetry(e, stage.name)
          context["exception"] = exceptionDetails
          context["beforeStagePlanningFailed"] = true
        }

        repository.storeStage(stage)
        queue.push(CompleteStage(message))
      }
    }
  }

  private fun trackResult(stage: StageExecution) {
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
      }.let { id ->
        stage.additionalMetricTags?.let {
          id.withTags(stage.additionalMetricTags)
        } ?: id
      }
    registry.counter(id).increment()
  }

  override val messageType = StartStage::class.java

  private fun StageExecution.anyUpstreamStagesFailed(): Boolean {
    // Memoized map of stageId to the result of anyUpstreamStagesFailed() for each stage
    val memo = HashMap<String, Boolean>()

    fun anyUpstreamStagesFailed(stage: StageExecution): Boolean {
      val stageId = stage.id
      if (memo.containsKey(stageId)) {
        return memo[stageId]!!
      }
      for (upstreamStage in stage.upstreamStages()) {
        if (upstreamStage.status in listOf(ExecutionStatus.TERMINAL, ExecutionStatus.STOPPED, ExecutionStatus.CANCELED)) {
          memo[stageId] = true
          return true
        }
        if (upstreamStage.status == NOT_STARTED && anyUpstreamStagesFailed(upstreamStage)) {
          memo[stageId] = true
          return true
        }
      }
      memo[stageId] = false
      return false
    }

    return anyUpstreamStagesFailed(this)
  }

  private fun StageExecution.plan() {
    builder().let { builder ->
      // if we have a top level stage, ensure that context expressions are processed
      val mergedStage = if (this.parentStageId == null) this.withMergedContext() else this
      builder.addContextFlags(mergedStage)
      builder.buildTasks(mergedStage, taskImplementationResolver)
      builder.buildBeforeStages(mergedStage) {
        repository.addStage(it.withMergedContext())
      }
    }
  }

  private fun StageExecution.start() {
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

  private fun StageExecution.shouldSkip(): Boolean {
    if (this.execution.type != PIPELINE) {
      return false
    }

    val clonedContext: MutableMap<String, Any> = this.context.toMutableMap()
    val clonedStage = StageExecutionImpl(this.execution, this.type, clonedContext).also {
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

  private fun StageExecution.isAfterStartTimeExpiry(): Boolean =
    startTimeExpiry?.let { Instant.ofEpochMilli(it) }?.isBefore(clock.instant()) ?: false
}
