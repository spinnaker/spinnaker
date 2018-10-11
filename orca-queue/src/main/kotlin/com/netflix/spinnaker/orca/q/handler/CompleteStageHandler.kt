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
import com.netflix.spinnaker.orca.ExecutionStatus
import com.netflix.spinnaker.orca.ExecutionStatus.*
import com.netflix.spinnaker.orca.events.StageComplete
import com.netflix.spinnaker.orca.ext.*
import com.netflix.spinnaker.orca.pipeline.StageDefinitionBuilderFactory
import com.netflix.spinnaker.orca.pipeline.graph.StageGraphBuilder
import com.netflix.spinnaker.orca.pipeline.model.Stage
import com.netflix.spinnaker.orca.pipeline.model.Task
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository
import com.netflix.spinnaker.orca.pipeline.util.ContextParameterProcessor
import com.netflix.spinnaker.orca.q.*
import com.netflix.spinnaker.q.Queue
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Component
import java.time.Clock
import java.util.concurrent.TimeUnit

@Component
class CompleteStageHandler(
  override val queue: Queue,
  override val repository: ExecutionRepository,
  @Qualifier("queueEventPublisher") private val publisher: ApplicationEventPublisher,
  private val clock: Clock,
  override val contextParameterProcessor: ContextParameterProcessor,
  private val registry: Registry,
  override val stageDefinitionBuilderFactory: StageDefinitionBuilderFactory
) : OrcaMessageHandler<CompleteStage>, StageBuilderAware, ExpressionAware {

  override fun handle(message: CompleteStage) {
    message.withStage { stage ->
      if (stage.status in setOf(RUNNING, NOT_STARTED)) {
        var status = stage.determineStatus()
        if (stage.shouldFailOnFailedExpressionEvaluation()) {
          log.warn("Stage ${stage.id} (${stage.type}) of ${stage.execution.id} " +
            "is set to fail because of failed expressions.")
          status = TERMINAL
        }

        try {
          if (status in setOf(RUNNING, NOT_STARTED) || (status.isComplete && !status.isHalt)) {
            // check to see if this stage has any unplanned synthetic after stages
            var afterStages = stage.firstAfterStages()
            if (afterStages.isEmpty()) {
              stage.planAfterStages()
              afterStages = stage.firstAfterStages()
            }
            if (afterStages.isNotEmpty() && afterStages.any { it.status == NOT_STARTED }) {
              afterStages
                .filter { it.status == NOT_STARTED }
                .forEach { queue.push(StartStage(message, it.id)) }
              return@withStage
            } else if (status == NOT_STARTED) {
              // stage had no synthetic stages or tasks, which is odd but whatever
              log.warn("Stage ${stage.id} (${stage.type}) of ${stage.execution.id} had no tasks or synthetic stages!")
              status = SKIPPED
            }
          } else if (status.isFailure) {
            if (stage.planOnFailureStages()) {
              stage.firstAfterStages().forEach {
                queue.push(StartStage(it))
              }
              return@withStage
            }
          }

          stage.status = status
          stage.endTime = clock.millis()
        } catch (e: Exception) {
          log.error("Failed to construct after stages for $stage.id", e)
          stage.status = TERMINAL
          stage.endTime = clock.millis()
        }

        stage.includeExpressionEvaluationSummary()
        repository.storeStage(stage)

        // When a synthetic stage ends with FAILED_CONTINUE, propagate that status up to the stage's
        // parent so that no more of the parent's synthetic children will run.
        if (stage.status == FAILED_CONTINUE && stage.syntheticStageOwner != null) {
          queue.push(message.copy(stageId = stage.parentStageId!!))
        } else if (stage.status in listOf(SUCCEEDED, FAILED_CONTINUE, SKIPPED)) {
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

  // TODO: this should be done out of band by responding to the StageComplete event
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
      duration > TimeUnit.MINUTES.toMillis(5)  -> "gt5m"
      else                                     -> "lt5m"
    }

  override val messageType = CompleteStage::class.java

  /**
   * Plan any outstanding synthetic after stages.
   */
  private fun Stage.planAfterStages() {
    var hasPlannedStages = false

    builder().buildAfterStages(this) { it: Stage ->
      repository.addStage(it)
      hasPlannedStages = true
    }

    if (hasPlannedStages) {
      this.execution = repository.retrieve(this.execution.type, this.execution.id)
    }
  }

  /**
   * Plan any outstanding synthetic on failure stages.
   */
  private fun Stage.planOnFailureStages(): Boolean {
    // Avoid planning failure stages if _any_ with the same name are already complete
    val previouslyPlannedAfterStageNames = afterStages().filter { it.status.isComplete }.map { it.name }

    val graph = StageGraphBuilder.afterStages(this)
    builder().onFailureStages(this, graph)

    val onFailureStages = graph.build().toList()
    onFailureStages.forEachIndexed { index, stage ->
      if (index > 0) {
        // all on failure stages should be run linearly
        graph.connect(onFailureStages.get(index - 1), stage)
      }
    }

    val alreadyPlanned = onFailureStages.any { previouslyPlannedAfterStageNames.contains(it.name) }

    return if (alreadyPlanned || onFailureStages.isEmpty()) {
      false
    } else {
      removeNotStartedSynthetics() // should be all synthetics (nothing should have been started!)
      appendAfterStages(onFailureStages) {
        repository.addStage(it)
      }
      true
    }
  }

  private fun Stage.removeNotStartedSynthetics() {
    syntheticStages()
      .filter { it.status == NOT_STARTED }
      .forEach { stage ->
        execution
          .stages
          .filter { it.requisiteStageRefIds.contains(stage.id) }
          .forEach {
            it.requisiteStageRefIds = it.requisiteStageRefIds - stage.id
            repository.addStage(it)
          }
        stage.removeNotStartedSynthetics() // should be all synthetics!
        repository.removeStage(execution, stage.id)
      }
  }

  private fun Stage.determineStatus(): ExecutionStatus {
    val syntheticStatuses = syntheticStages().map(Stage::getStatus)
    val taskStatuses = tasks.map(Task::getStatus)
    val planningStatus = if (hasPlanningFailure()) listOf(failureStatus()) else emptyList()
    val allStatuses = syntheticStatuses + taskStatuses + planningStatus
    val afterStageStatuses = afterStages().map(Stage::getStatus)
    return when {
      allStatuses.isEmpty()                    -> NOT_STARTED
      allStatuses.contains(TERMINAL)           -> TERMINAL
      allStatuses.contains(STOPPED)            -> STOPPED
      allStatuses.contains(CANCELED)           -> CANCELED
      allStatuses.contains(FAILED_CONTINUE)    -> FAILED_CONTINUE
      allStatuses.all { it == SUCCEEDED }      -> SUCCEEDED
      afterStageStatuses.contains(NOT_STARTED) -> RUNNING // after stages were planned but not run yet
      else                                     -> {
        log.error("Unhandled condition for stage $id of $execution.id, marking as TERMINAL. syntheticStatuses=$syntheticStatuses, taskStatuses=$taskStatuses, planningStatus=$planningStatus, afterStageStatuses=$afterStageStatuses")
        TERMINAL
      }
    }
  }
}

private fun Stage.hasPlanningFailure() =
  context["beforeStagePlanningFailed"] == true
