/*
 * Copyright 2018 Netflix, Inc.
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

package com.netflix.spinnaker.orca.ext

import com.netflix.spinnaker.orca.api.pipeline.SyntheticStageOwner.STAGE_AFTER
import com.netflix.spinnaker.orca.api.pipeline.SyntheticStageOwner.STAGE_BEFORE
import com.netflix.spinnaker.orca.api.pipeline.models.ExecutionStatus
import com.netflix.spinnaker.orca.api.pipeline.models.ExecutionStatus.CANCELED
import com.netflix.spinnaker.orca.api.pipeline.models.ExecutionStatus.FAILED_CONTINUE
import com.netflix.spinnaker.orca.api.pipeline.models.ExecutionStatus.SKIPPED
import com.netflix.spinnaker.orca.api.pipeline.models.ExecutionStatus.STOPPED
import com.netflix.spinnaker.orca.api.pipeline.models.ExecutionStatus.SUCCEEDED
import com.netflix.spinnaker.orca.api.pipeline.models.ExecutionStatus.TERMINAL
import com.netflix.spinnaker.orca.api.pipeline.models.StageExecution
import com.netflix.spinnaker.orca.api.pipeline.models.TaskExecution

/**
 * @return the stage's first before stage or `null` if there are none.
 */
fun StageExecution.firstBeforeStages(): List<StageExecution> =
  beforeStages().filter { it.isInitial() }

/**
 * @return the stage's first after stage or `null` if there are none.
 */
fun StageExecution.firstAfterStages(): List<StageExecution> =
  afterStages().filter { it.isInitial() }

fun StageExecution.isInitial(): Boolean =
  requisiteStageRefIds.isEmpty()

/**
 * @return the stage's first task or `null` if there are none.
 */
fun StageExecution.firstTask(): TaskExecution? = tasks.firstOrNull()

/**
 * @return the stage's parent stage.
 * @throws IllegalStateException if the stage is not synthetic.
 */
fun StageExecution.parent(): StageExecution =
  execution
    .stages
    .find { it.id == parentStageId }
    ?: throw IllegalStateException("Not a synthetic stage")

/**
 * @return the task that follows [task] or `null` if [task] is the end of the
 * stage.
 */
fun StageExecution.nextTask(task: TaskExecution): TaskExecution? =
  if (task.isStageEnd) {
    null
  } else {
    val index = tasks.indexOf(task)
    tasks[index + 1]
  }

/**
 * @return all stages directly upstream of this stage.
 */
fun StageExecution.upstreamStages(): List<StageExecution> =
  execution.stages.filter { it.refId in requisiteStageRefIds }

/**
 * @return `true` if all upstream stages of this stage were run successfully.
 */
fun StageExecution.allUpstreamStagesComplete(): Boolean =
  upstreamStages().all { it.status in listOf(SUCCEEDED, FAILED_CONTINUE, SKIPPED) }

fun StageExecution.syntheticStages(): List<StageExecution> =
  execution.stages.filter { it.parentStageId == id }

fun StageExecution.recursiveSyntheticStages(): List<StageExecution> =
  syntheticStages() + syntheticStages().flatMap {
    it.recursiveSyntheticStages()
  }

fun StageExecution.beforeStages(): List<StageExecution> =
  syntheticStages().filter { it.syntheticStageOwner == STAGE_BEFORE }

fun StageExecution.afterStages(): List<StageExecution> =
  syntheticStages().filter { it.syntheticStageOwner == STAGE_AFTER }

fun StageExecution.allBeforeStagesSuccessful(): Boolean =
  beforeStages().all { it.status in listOf(SUCCEEDED, FAILED_CONTINUE, SKIPPED) }

fun StageExecution.allAfterStagesSuccessful(): Boolean =
  afterStages().all { it.status in listOf(SUCCEEDED, FAILED_CONTINUE, SKIPPED) }

fun StageExecution.anyBeforeStagesFailed(): Boolean =
  beforeStages().any { it.status in listOf(TERMINAL, STOPPED, CANCELED) }

fun StageExecution.anyAfterStagesFailed(): Boolean =
  afterStages().any { it.status in listOf(TERMINAL, STOPPED, CANCELED) }

fun StageExecution.allAfterStagesComplete(): Boolean =
  afterStages().all { it.status.isComplete }

fun StageExecution.hasTasks(): Boolean =
  tasks.isNotEmpty()

fun StageExecution.hasAfterStages(): Boolean =
  firstAfterStages().isNotEmpty()

inline fun <reified O> StageExecution.mapTo(pointer: String): O = mapTo(pointer, O::class.java)

inline fun <reified O> StageExecution.mapTo(): O = mapTo(O::class.java)

fun StageExecution.shouldFailPipeline(): Boolean =
  context["failPipeline"] in listOf(null, true)

fun StageExecution.failureStatus(default: ExecutionStatus = TERMINAL) =
  when {
    continuePipelineOnFailure -> FAILED_CONTINUE
    shouldFailPipeline() -> default
    else -> STOPPED
  }

fun StageExecution.isManuallySkipped(): Boolean {
  return context["manualSkip"] == true || parent?.isManuallySkipped() == true
}
