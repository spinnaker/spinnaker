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
fun Execution<*>.initialStages() =
  getStages()
    .filter { it.isInitial() }

/**
 * @return the stage's first before stage or `null` if there are none.
 */
fun Stage<out Execution<*>>.firstBeforeStages() =
  getExecution()
    .getStages()
    .filter {
      it.getParentStageId() == getId() && it.getSyntheticStageOwner() == STAGE_BEFORE && it.getRequisiteStageRefIds().isEmpty()
    }

/**
 * @return the stage's first after stage or `null` if there are none.
 */
fun Stage<out Execution<*>>.firstAfterStages() =
  getExecution()
    .getStages()
    .filter {
      it.getParentStageId() == getId() && it.getSyntheticStageOwner() == STAGE_AFTER && it.getRequisiteStageRefIds().isEmpty()
    }

fun Stage<*>.isInitial() =
  getRequisiteStageRefIds() == null || getRequisiteStageRefIds().isEmpty()

/**
 * @return the stage's first task or `null` if there are none.
 */
fun Stage<out Execution<*>>.firstTask() = getTasks().firstOrNull()

/**
 * @return the stage's parent stage.
 * @throws IllegalStateException if the stage is not synthetic.
 */
fun Stage<out Execution<*>>.parent() =
  getExecution()
    .getStages()
    .find { it.getId() == getParentStageId() } ?: throw IllegalStateException("Not a synthetic stage")

/**
 * @return the task that follows [task] or `null` if [task] is the end of the
 * stage.
 */
fun Stage<out Execution<*>>.nextTask(task: Task) =
  if (task.isStageEnd) {
    null
  } else {
    val index = getTasks().indexOf(task)
    getTasks()[index + 1]
  }

/**
 * @return all upstream stages of this stage.
 */
fun Stage<*>.upstreamStages(): List<Stage<*>> =
  getExecution().getStages().filter { it.getRefId() in getRequisiteStageRefIds() }

/**
 * @return `true` if all upstream stages of this stage were run successfully.
 */
fun Stage<*>.allUpstreamStagesComplete(): Boolean =
  upstreamStages().all { it.getStatus() in listOf(SUCCEEDED, FAILED_CONTINUE, SKIPPED) }

fun Stage<*>.anyUpstreamStagesFailed(): Boolean =
  upstreamStages().any { it.getStatus() in listOf(TERMINAL, STOPPED, CANCELED) || it.getStatus() == NOT_STARTED && it.anyUpstreamStagesFailed() }

fun Stage<*>.beforeStages(): List<Stage<*>> =
  getExecution().getStages().filter { it.getParentStageId() == getId() && it.getSyntheticStageOwner() == STAGE_BEFORE }

fun Stage<*>.allBeforeStagesComplete(): Boolean =
  beforeStages().all { it.getStatus() in listOf(SUCCEEDED, FAILED_CONTINUE, SKIPPED) }

fun Stage<*>.anyBeforeStagesFailed(): Boolean =
  beforeStages().any { it.getStatus() in listOf(TERMINAL, STOPPED, CANCELED) }

fun Stage<*>.hasTasks(): Boolean =
  getTasks().isNotEmpty()

fun Stage<*>.hasAfterStages(): Boolean =
  firstAfterStages().isNotEmpty()
