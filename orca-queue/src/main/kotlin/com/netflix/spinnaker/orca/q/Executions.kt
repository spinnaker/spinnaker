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

package com.netflix.spinnaker.orca.q

import com.netflix.spinnaker.orca.ExecutionStatus.*
import com.netflix.spinnaker.orca.pipeline.model.Execution
import com.netflix.spinnaker.orca.pipeline.model.Stage
import com.netflix.spinnaker.orca.pipeline.model.SyntheticStageOwner.STAGE_AFTER
import com.netflix.spinnaker.orca.pipeline.model.SyntheticStageOwner.STAGE_BEFORE
import com.netflix.spinnaker.orca.pipeline.model.Task

/**
 * @return the initial stages of the execution.
 */
fun Execution.initialStages() =
  stages
    .filter { it.isInitial() }

/**
 * @return the stage's first before stage or `null` if there are none.
 */
fun Stage.firstBeforeStages() =
  execution
    .stages
    .filter {
      it.parentStageId == id && it.syntheticStageOwner == STAGE_BEFORE && it.requisiteStageRefIds.isEmpty()
    }

/**
 * @return the stage's first after stage or `null` if there are none.
 */
fun Stage.firstAfterStages() =
  execution
    .stages
    .filter {
      it.parentStageId == id && it.syntheticStageOwner == STAGE_AFTER && it.requisiteStageRefIds.isEmpty()
    }

fun Stage.isInitial() =
  requisiteStageRefIds == null || requisiteStageRefIds.isEmpty()

/**
 * @return the stage's first task or `null` if there are none.
 */
fun Stage.firstTask() = tasks.firstOrNull()

/**
 * @return the stage's parent stage.
 * @throws IllegalStateException if the stage is not synthetic.
 */
fun Stage.parent() =
  execution
    .stages
    .find { it.id == parentStageId } ?: throw IllegalStateException("Not a synthetic stage")

/**
 * @return the task that follows [task] or `null` if [task] is the end of the
 * stage.
 */
fun Stage.nextTask(task: Task) =
  if (task.isStageEnd) {
    null
  } else {
    val index = tasks.indexOf(task)
    tasks[index + 1]
  }

/**
 * @return all upstream stages of this stage.
 */
fun Stage.upstreamStages(): List<Stage> =
  execution.stages.filter { it.refId in requisiteStageRefIds }

/**
 * @return `true` if all upstream stages of this stage were run successfully.
 */
fun Stage.allUpstreamStagesComplete(): Boolean =
  upstreamStages().all { it.status in listOf(SUCCEEDED, FAILED_CONTINUE, SKIPPED) }

fun Stage.anyUpstreamStagesFailed(): Boolean =
  upstreamStages().any { it.status in listOf(TERMINAL, STOPPED, CANCELED) || it.status == NOT_STARTED && it.anyUpstreamStagesFailed() }

fun Stage.syntheticStages(): List<Stage> =
  execution.stages.filter { it.parentStageId == id }

fun Stage.beforeStages(): List<Stage> =
  syntheticStages().filter { it.syntheticStageOwner == STAGE_BEFORE }

fun Stage.afterStages(): List<Stage> =
  syntheticStages().filter { it.syntheticStageOwner == STAGE_AFTER }

fun Stage.allBeforeStagesComplete(): Boolean =
  beforeStages().all { it.status in listOf(SUCCEEDED, FAILED_CONTINUE, SKIPPED) }

fun Stage.anyBeforeStagesFailed(): Boolean =
  beforeStages().any { it.status in listOf(TERMINAL, STOPPED, CANCELED) }

fun Stage.hasTasks(): Boolean =
  tasks.isNotEmpty()

fun Stage.hasAfterStages(): Boolean =
  firstAfterStages().isNotEmpty()
